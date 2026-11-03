package com.isocode.township.complaint;

import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatMember;
import com.isocode.township.flat.FlatMemberRepository;
import com.isocode.township.flat.FlatRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComplaintDemoSeeder {

    static final String SWATI_FLAT = "T12-0408";
    static final String NEERAJ_FLAT = "T07-1203";
    static final String AAMIR_FLAT = "T21-1601";
    static final String LIFT_DUPLICATE_TOWER = "T07-";

    private static final DateTimeFormatter REF_MONTH = DateTimeFormatter.ofPattern("yyMM");
    private static final Set<String> BRIEF_FLATS = Set.of(SWATI_FLAT, NEERAJ_FLAT, AAMIR_FLAT);
    private static final Map<ComplaintCategory, String> SPECIALIST_PHONE = new EnumMap<>(Map.of(
            ComplaintCategory.PLUMBING, "+919000000102",
            ComplaintCategory.CIVIL, "+919000000102",
            ComplaintCategory.ELECTRICAL, "+919000000103",
            ComplaintCategory.LIFT, "+919000000104",
            ComplaintCategory.HOUSEKEEPING, "+919000000105",
            ComplaintCategory.PEST_CONTROL, "+919000000105",
            ComplaintCategory.SECURITY, "+919000000106"));
    private static final String GENERAL_DESK_PHONE = "+919000000101";

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final FlatRepository flatRepository;
    private final FlatMemberRepository memberRepository;
    private final AppUserRepository userRepository;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public ComplaintDemoSeeder(ComplaintRepository complaintRepository, ComplaintEventRepository eventRepository,
                               FlatRepository flatRepository, FlatMemberRepository memberRepository,
                               AppUserRepository userRepository, SocietySettingsRepository settingsRepository,
                               Clock clock) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.flatRepository = flatRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional
    public void seed() {
        List<Flat> pool = flatRepository.findAll(Sort.by("code")).stream()
                .filter(flat -> !BRIEF_FLATS.contains(flat.getCode()))
                .filter(flat -> signInResident(flat).isPresent())
                .toList();
        SlaTargets targets = SlaTargets.from(settingsRepository.current());
        for (Spec spec : specs()) {
            Flat flat = spec.flatCode() != null
                    ? flatRepository.findByCode(spec.flatCode()).orElseThrow()
                    : pooledFlat(pool, spec);
            write(spec, flat, targets);
        }
    }

    private static Flat pooledFlat(List<Flat> pool, Spec spec) {
        for (int step = 0; step < pool.size(); step++) {
            Flat candidate = pool.get((spec.poolIndex() + step) % pool.size());
            boolean liftInDuplicateTower = spec.category() == ComplaintCategory.LIFT
                    && candidate.getCode().startsWith(LIFT_DUPLICATE_TOWER);
            if (!liftInDuplicateTower) {
                return candidate;
            }
        }
        throw new IllegalStateException("No flat available for seeded complaint");
    }

    private void write(Spec spec, Flat flat, SlaTargets targets) {
        Instant now = clock.instant();
        Instant created = now.minus(spec.ago());
        AppUser resident = signInResident(flat).orElseThrow();
        Long staffId = staffFor(spec.category());

        Complaint complaint = complaintRepository.save(new Complaint(refNo(created), flat.getId(), resident.getId(),
                spec.description(), new Complaint.Classification(spec.category(), spec.urgency(), spec.category(),
                        spec.urgency(), spec.confidence(), spec.summary(), ClassifiedBy.AI, false),
                created, targets.firstResponseDue(created, spec.urgency()), targets.resolveDue(created, spec.urgency())));
        event(complaint, ComplaintEventType.CREATED, resident.getId(), null, "OPEN", null, created);
        complaint.assignTo(staffId);
        event(complaint, ComplaintEventType.AUTO_ASSIGNED, null, null, staffId.toString(), null, created);

        if (spec.outcome() == Outcome.ASSIGNED) {
            return;
        }
        Instant firstResponse = created.plus(spec.firstResponseAfter());
        complaint.moveTo(ComplaintStatus.IN_PROGRESS, Actor.STAFF, firstResponse);
        complaint.recordFirstResponse(firstResponse);
        event(complaint, ComplaintEventType.STATUS_CHANGED, staffId, "ASSIGNED", "IN_PROGRESS", spec.staffNote(),
                firstResponse);
        if (spec.outcome() == Outcome.IN_PROGRESS) {
            return;
        }

        Instant resolved = created.plus(spec.resolvedAfter());
        resolve(complaint, staffId, resolved);
        if (spec.outcome() == Outcome.RESOLVED) {
            return;
        }
        Instant residentReply = resolved.plus(Duration.ofHours(20));
        if (spec.outcome() == Outcome.REOPENED || spec.outcome() == Outcome.REOPENED_THEN_CLOSED) {
            complaint.reopen(targets.resolveDue(residentReply, spec.urgency()));
            event(complaint, ComplaintEventType.REOPENED, resident.getId(), "RESOLVED", "ASSIGNED",
                    spec.reopenReason(), residentReply);
            if (spec.outcome() == Outcome.REOPENED) {
                return;
            }
            Instant secondFix = residentReply.plus(Duration.ofHours(30));
            complaint.moveTo(ComplaintStatus.IN_PROGRESS, Actor.STAFF, secondFix.minus(Duration.ofHours(4)));
            event(complaint, ComplaintEventType.STATUS_CHANGED, staffId, "ASSIGNED", "IN_PROGRESS", null,
                    secondFix.minus(Duration.ofHours(4)));
            resolve(complaint, staffId, secondFix);
            residentReply = secondFix.plus(Duration.ofHours(20));
        }
        complaint.close(spec.rating(), spec.ratingComment(), residentReply);
        event(complaint, ComplaintEventType.CLOSED, resident.getId(), "RESOLVED", Integer.toString(spec.rating()),
                spec.ratingComment(), residentReply);
    }

    private void resolve(Complaint complaint, Long staffId, Instant at) {
        complaint.moveTo(ComplaintStatus.RESOLVED, Actor.STAFF, at);
        event(complaint, ComplaintEventType.STATUS_CHANGED, staffId, "IN_PROGRESS", "RESOLVED", null, at);
    }

    private void event(Complaint complaint, ComplaintEventType type, Long actor, String from, String to, String note,
                       Instant at) {
        eventRepository.save(new ComplaintEvent(complaint.getId(), type, actor, from, to, note, at));
    }

    private String refNo(Instant created) {
        return "CMP-%s-%04d".formatted(REF_MONTH.format(created.atZone(clock.getZone())),
                complaintRepository.nextRefSequence());
    }

    private Long staffFor(ComplaintCategory category) {
        String phone = SPECIALIST_PHONE.getOrDefault(category, GENERAL_DESK_PHONE);
        return userRepository.findByPhone(phone).orElseThrow().getId();
    }

    private Optional<AppUser> signInResident(Flat flat) {
        return memberRepository.findByFlatIdAndActiveTrue(flat.getId()).stream()
                .map(FlatMember::getUser)
                .filter(user -> user.getPhone().isPresent())
                .findFirst();
    }

    private static List<Spec> specs() {
        return List.of(
                brief(SWATI_FLAT, ComplaintCategory.PLUMBING, Urgency.HIGH,
                        "Water is leaking from the bathroom ceiling, it seems to come from the flat above. "
                                + "The wall near the switchboard is getting damp.",
                        "Bathroom ceiling leak from flat above", days(2), Outcome.IN_PROGRESS, minutes(100), null,
                        "Ravi will visit at 4 pm with the plumber", null, null, null),
                brief(NEERAJ_FLAT, ComplaintCategory.ELECTRICAL, Urgency.MEDIUM,
                        "The light in the stairwell on the 12th floor has not been working for three days.",
                        "Stairwell light out on 12th floor", days(40), Outcome.CLOSED, minutes(95), hours(26),
                        null, 5, "Fixed quickly, thank you", null),
                brief(AAMIR_FLAT, ComplaintCategory.PARKING, Urgency.LOW,
                        "A car without a society sticker has been parked in my slot B-112 since yesterday.",
                        "Unknown car parked in slot B-112", days(25), Outcome.CLOSED, hours(5), hours(30),
                        null, 4, null, null),

                pooled(0, ComplaintCategory.PLUMBING, Urgency.MEDIUM, "Kitchen sink drain is blocked and water is backing up.",
                        "Kitchen sink drain blocked", days(55), Outcome.CLOSED, hours(3), hours(20), 5, "Good job", null),
                pooled(1, ComplaintCategory.ELECTRICAL, Urgency.HIGH, "Sparks seen at the meter box earlier, now the power keeps tripping.",
                        "Power tripping at meter box", days(52), Outcome.CLOSED, minutes(45), hours(9), 4, null, null),
                pooled(2, ComplaintCategory.HOUSEKEEPING, Urgency.LOW, "Garbage on our floor was not collected for two days.",
                        "Floor garbage not collected", days(50), Outcome.CLOSED, hours(30), hours(40), 2,
                        "Took too long", null),
                pooled(3, ComplaintCategory.PEST_CONTROL, Urgency.MEDIUM, "Cockroaches in the kitchen even after cleaning, need pest control.",
                        "Cockroaches in kitchen", days(48), Outcome.CLOSED, hours(10), hours(70), 3, null, null),
                pooled(4, ComplaintCategory.CIVIL, Urgency.LOW, "Cracks have appeared in the balcony plaster after the rains.",
                        "Balcony plaster cracks", days(46), Outcome.CLOSED, hours(20), days(5), 4, null, null),
                pooled(5, ComplaintCategory.SECURITY, Urgency.HIGH, "The gate guard let a delivery person in without calling us.",
                        "Delivery let in without call", days(44), Outcome.CLOSED, minutes(50), hours(6), 5, null, null),
                pooled(6, ComplaintCategory.NOISE, Urgency.LOW, "Loud music from the flat below every night after midnight.",
                        "Late night loud music", days(41), Outcome.CLOSED, hours(14), days(3), 3,
                        "Better now but not fully", null),
                pooled(7, ComplaintCategory.PLUMBING, Urgency.HIGH, "No water supply in our flat since this morning.",
                        "No water supply since morning", days(38), Outcome.CLOSED, minutes(40), hours(5), 5, null, null),
                pooled(8, ComplaintCategory.ELECTRICAL, Urgency.MEDIUM, "Two corridor lights near the lift lobby are not working.",
                        "Lift lobby corridor lights out", days(35), Outcome.CLOSED, hours(6), hours(30), 4, null, null),
                pooled(9, ComplaintCategory.LIFT, Urgency.MEDIUM, "Lift door closes too fast and hit my mother's walker.",
                        "Lift door closes too fast", days(33), Outcome.CLOSED, hours(2), hours(28), 4, null, null),
                pooled(10, ComplaintCategory.HOUSEKEEPING, Urgency.MEDIUM, "The staircase has not been mopped this week and is slippery.",
                        "Staircase not mopped", days(30), Outcome.CLOSED, hours(9), hours(26), 4, null, null),
                pooled(11, ComplaintCategory.OTHER, Urgency.LOW, "Please share the date for the next society general meeting.",
                        "Asking for next meeting date", days(28), Outcome.CLOSED, hours(4), hours(8), 5, null, null),
                pooled(12, ComplaintCategory.PARKING, Urgency.MEDIUM, "Visitor cars are parking in the fire lane near the clubhouse.",
                        "Visitor cars in fire lane", days(22), Outcome.CLOSED, hours(3), hours(48), 3, null, null),
                pooled(13, ComplaintCategory.CIVIL, Urgency.MEDIUM, "Seepage on the bedroom wall shared with the next flat.",
                        "Bedroom wall seepage", days(20), Outcome.CLOSED, hours(11), days(4), 4, null, null),

                pooled(14, ComplaintCategory.PLUMBING, Urgency.MEDIUM, "The flush in the guest bathroom keeps running.",
                        "Guest toilet flush running", days(6), Outcome.RESOLVED, hours(2), hours(40), null, null, null),
                pooled(15, ComplaintCategory.ELECTRICAL, Urgency.LOW, "Doorbell has stopped working.",
                        "Doorbell not working", days(5), Outcome.RESOLVED, hours(8), days(3), null, null, null),
                pooled(16, ComplaintCategory.HOUSEKEEPING, Urgency.LOW, "Dustbin near the main gate is overflowing.",
                        "Main gate dustbin overflowing", days(3), Outcome.RESOLVED, hours(5), hours(30), null, null, null),
                pooled(17, ComplaintCategory.PEST_CONTROL, Urgency.LOW, "Mosquitoes breeding in the stagnant water near block parking.",
                        "Stagnant water, mosquitoes", days(4), Outcome.RESOLVED, hours(12), hours(60), null, null, null),

                pooled(18, ComplaintCategory.PLUMBING, Urgency.MEDIUM, "Bathroom tap leaking again after last week's repair.",
                        "Bathroom tap leaking", days(12), Outcome.REOPENED, hours(3), hours(30), null, null,
                        "Tap started dripping again the next day"),
                pooled(19, ComplaintCategory.CIVIL, Urgency.LOW, "Main door does not lock properly, the frame seems swollen.",
                        "Main door does not lock", days(18), Outcome.REOPENED_THEN_CLOSED, hours(9), days(2), 3,
                        "Second fix worked", "Door still sticks in the morning"),

                pooled(20, ComplaintCategory.ELECTRICAL, Urgency.HIGH, "Power socket in the kitchen is burning hot to touch.",
                        "Hot kitchen socket", days(1), Outcome.IN_PROGRESS, minutes(30), null, null, null, null),
                pooled(21, ComplaintCategory.SECURITY, Urgency.MEDIUM, "CCTV near tower parking seems to be off since two days.",
                        "Parking CCTV seems off", hours(30), Outcome.IN_PROGRESS, hours(4), null, null, null, null),
                pooled(22, ComplaintCategory.NOISE, Urgency.LOW, "Construction work in the next flat starts at 7 am.",
                        "Early morning construction noise", days(3), Outcome.IN_PROGRESS, hours(30), null, null, null, null),
                pooled(23, ComplaintCategory.LIFT, Urgency.HIGH, "Lift makes a loud grinding noise between floors 8 and 9.",
                        "Lift grinding noise", hours(20), Outcome.IN_PROGRESS, hours(1), null, null, null, null),

                pooled(24, ComplaintCategory.HOUSEKEEPING, Urgency.MEDIUM, "Water cooler in the clubhouse is dirty.",
                        "Dirty clubhouse water cooler", hours(5), Outcome.ASSIGNED, null, null, null, null, null),
                pooled(25, ComplaintCategory.PARKING, Urgency.LOW, "Two-wheeler parking lines need repainting.",
                        "Repaint two-wheeler lines", hours(10), Outcome.ASSIGNED, null, null, null, null, null),
                pooled(26, ComplaintCategory.PLUMBING, Urgency.MEDIUM, "Overhead tank overflow pipe is dripping onto the balcony below.",
                        "Tank overflow dripping", hours(12), Outcome.ASSIGNED, null, null, null, null, null));
    }

    private static Spec brief(String flatCode, ComplaintCategory category, Urgency urgency, String description,
                              String summary, Duration ago, Outcome outcome, Duration firstResponseAfter,
                              Duration resolvedAfter, String staffNote, Integer rating, String ratingComment,
                              String reopenReason) {
        return new Spec(flatCode, 0, category, urgency, description, summary, ago, outcome, firstResponseAfter,
                resolvedAfter, staffNote, rating, ratingComment, reopenReason);
    }

    private static Spec pooled(int poolIndex, ComplaintCategory category, Urgency urgency, String description,
                               String summary, Duration ago, Outcome outcome, Duration firstResponseAfter,
                               Duration resolvedAfter, Integer rating, String ratingComment, String reopenReason) {
        return new Spec(null, poolIndex * 3, category, urgency, description, summary, ago, outcome, firstResponseAfter,
                resolvedAfter, null, rating, ratingComment, reopenReason);
    }

    private static Duration minutes(long value) {
        return Duration.ofMinutes(value);
    }

    private static Duration hours(long value) {
        return Duration.ofHours(value);
    }

    private static Duration days(long value) {
        return Duration.ofDays(value);
    }

    enum Outcome {
        ASSIGNED,
        IN_PROGRESS,
        RESOLVED,
        CLOSED,
        REOPENED,
        REOPENED_THEN_CLOSED
    }

    record Spec(String flatCode, int poolIndex, ComplaintCategory category, Urgency urgency, String description,
                String summary, Duration ago, Outcome outcome, Duration firstResponseAfter, Duration resolvedAfter,
                String staffNote, Integer rating, String ratingComment, String reopenReason) {

        BigDecimal confidence() {
            return new BigDecimal("0.%02d".formatted(82 + Math.floorMod(description.length(), 14)));
        }
    }
}
