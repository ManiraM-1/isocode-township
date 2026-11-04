package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.FieldValidationException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.AutoAssigner.StaffLoad;
import com.isocode.township.flat.FlatAccessGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

    public static final int MIN_DESCRIPTION = 10;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;
    private static final DateTimeFormatter REF_MONTH = DateTimeFormatter.ofPattern("yyMM");

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final ComplaintPhotoStore photoStore;
    private final ComplaintClassifier classifier;
    private final StaffLoads staffLoads;
    private final ComplaintEscalator escalator;
    private final SocietySettingsRepository settingsRepository;
    private final FlatAccessGuard accessGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ComplaintService(ComplaintRepository complaintRepository, ComplaintEventRepository eventRepository,
                            ComplaintPhotoStore photoStore, ComplaintClassifier classifier, StaffLoads staffLoads,
                            ComplaintEscalator escalator,
                            SocietySettingsRepository settingsRepository, FlatAccessGuard accessGuard,
                            ApplicationEventPublisher events, Clock clock) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.photoStore = photoStore;
        this.classifier = classifier;
        this.staffLoads = staffLoads;
        this.escalator = escalator;
        this.settingsRepository = settingsRepository;
        this.accessGuard = accessGuard;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Complaint raise(SessionUser user, Long flatId, String rawDescription, byte[] photo) {
        accessGuard.requireMember(user, flatId);
        String description = validDescription(rawDescription);
        Optional<ImageType> photoType = validPhoto(photo);

        Instant now = clock.instant();
        Complaint.Classification classification = classifier.provisional(description);
        SlaTargets targets = SlaTargets.from(settingsRepository.current());
        Complaint complaint = complaintRepository.save(new Complaint(nextRefNo(now), flatId, user.userId(),
                description, classification, now,
                targets.firstResponseDue(now, classification.urgency()),
                targets.resolveDue(now, classification.urgency())));
        photoType.ifPresent(type -> {
            photoStore.save(complaint.getId(), photo, type);
            complaint.attachPhotoType(type.contentType());
        });
        record(complaint, ComplaintEventType.CREATED, user.userId(), null, ComplaintStatus.OPEN.name(), null, now);
        escalator.escalate(complaint, now);

        AutoAssigner.pick(complaint.getCategory(), staffLoads.current()).map(StaffLoad::staffId).ifPresent(staffId -> {
            complaint.assignTo(staffId);
            record(complaint, ComplaintEventType.AUTO_ASSIGNED, null, null, staffId.toString(), null, now);
        });
        events.publishEvent(new ComplaintRaised(complaint.getId(), description));
        return complaint;
    }

    @Transactional
    public Complaint close(SessionUser user, Long complaintId, int rating, String comment) {
        Complaint complaint = lockedForResident(user, complaintId);
        Instant now = clock.instant();
        String note = comment == null || comment.isBlank() ? null : comment.strip();
        complaint.close(rating, note, now);
        record(complaint, ComplaintEventType.CLOSED, user.userId(), ComplaintStatus.RESOLVED.name(),
                Integer.toString(rating), note, now);
        return complaint;
    }

    @Transactional
    public Complaint reopen(SessionUser user, Long complaintId, String reason) {
        Complaint complaint = lockedForResident(user, complaintId);
        Instant now = clock.instant();
        SlaTargets targets = SlaTargets.from(settingsRepository.current());
        complaint.reopen(targets.resolveDue(now, complaint.getUrgency()));
        record(complaint, ComplaintEventType.REOPENED, user.userId(), ComplaintStatus.RESOLVED.name(),
                ComplaintStatus.ASSIGNED.name(), reason.strip(), now);
        return complaint;
    }

    @Transactional(readOnly = true)
    public Complaint viewable(SessionUser user, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found"));
        accessGuard.requireMemberOrStaff(user, complaint.getFlatId());
        return complaint;
    }

    private Complaint lockedForResident(SessionUser user, Long complaintId) {
        Complaint complaint = complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found"));
        accessGuard.requireMember(user, complaint.getFlatId());
        return complaint;
    }

    private void record(Complaint complaint, ComplaintEventType type, Long actor, String from, String to, String note,
                        Instant at) {
        eventRepository.save(new ComplaintEvent(complaint.getId(), type, actor, from, to, note, at));
    }

    private String nextRefNo(Instant now) {
        return "CMP-%s-%04d".formatted(REF_MONTH.format(now.atZone(clock.getZone())),
                complaintRepository.nextRefSequence());
    }

    private static String validDescription(String raw) {
        String description = raw == null ? "" : raw.strip();
        if (description.length() < MIN_DESCRIPTION) {
            throw new FieldValidationException("description",
                    "Describe the problem in at least " + MIN_DESCRIPTION + " characters");
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw new FieldValidationException("description",
                    "Keep the description under " + MAX_DESCRIPTION + " characters");
        }
        return description;
    }

    private static Optional<ImageType> validPhoto(byte[] photo) {
        if (photo == null || photo.length == 0) {
            return Optional.empty();
        }
        if (photo.length > MAX_PHOTO_BYTES) {
            throw new FieldValidationException("photo", "The photo is larger than 5 MB");
        }
        return Optional.of(ImageType.detect(photo)
                .orElseThrow(() -> new FieldValidationException("photo", "Attach a JPEG, PNG or WebP photo")));
    }
}
