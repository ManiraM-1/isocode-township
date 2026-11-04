package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.ConflictException;
import com.isocode.township.common.NotFoundException;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeskComplaintService {

    private static final Comparator<Complaint> BY_NEXT_DEADLINE =
            Comparator.comparing(DeskComplaintService::nextDeadline).thenComparing(Complaint::getId);
    private static final Comparator<Complaint> NEWEST_FIRST =
            Comparator.comparing(Complaint::getCreatedAt).reversed().thenComparing(Complaint::getId);

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final EscalationRepository escalationRepository;
    private final StaffLoads staffLoads;
    private final ComplaintHandover handover;
    private final DuplicateLinker duplicateLinker;
    private final SocietySettingsRepository settingsRepository;
    private final Clock clock;

    public DeskComplaintService(ComplaintRepository complaintRepository, ComplaintEventRepository eventRepository,
                                EscalationRepository escalationRepository, StaffLoads staffLoads,
                                ComplaintHandover handover, DuplicateLinker duplicateLinker,
                                SocietySettingsRepository settingsRepository, Clock clock) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.escalationRepository = escalationRepository;
        this.staffLoads = staffLoads;
        this.handover = handover;
        this.duplicateLinker = duplicateLinker;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Complaint> board(DeskLane lane, ComplaintStatus status, ComplaintCategory category, Long assigneeId,
                                 String towerCode) {
        Set<ComplaintStatus> statuses = lane == null ? EnumSet.allOf(ComplaintStatus.class) : lane.statuses();
        if (status != null) {
            statuses.retainAll(EnumSet.of(status));
        }
        if (statuses.isEmpty()) {
            return List.of();
        }
        String tower = towerCode == null || towerCode.isBlank() ? null : towerCode.strip().toUpperCase(Locale.ROOT);
        List<Complaint> found = complaintRepository.search(statuses, lane == DeskLane.SAFETY,
                lane == DeskLane.NEEDS_REVIEW, category, assigneeId, tower);
        boolean byDeadline = lane == DeskLane.OPEN || lane == DeskLane.SAFETY || lane == DeskLane.NEEDS_REVIEW;
        return found.stream().sorted(byDeadline ? BY_NEXT_DEADLINE : NEWEST_FIRST).toList();
    }

    @Transactional
    public Complaint changeStatus(SessionUser staff, Long complaintId, ComplaintStatus target, String note,
                                  boolean applyToLinked) {
        Complaint complaint = locked(complaintId);
        List<Complaint> linked = List.of();
        if (applyToLinked) {
            linked = complaintRepository.findByParentIdOrderByCreatedAtAscIdAsc(complaint.getId());
            if (linked.isEmpty()) {
                throw new ConflictException("This complaint has no linked complaints.");
            }
        }
        Instant now = clock.instant();
        moveAsStaff(complaint, staff, target, clean(note), now);
        String appliedNote = "Applied from " + complaint.getRefNo() + (clean(note) == null ? "" : ": " + clean(note));
        for (Complaint child : linked) {
            Complaint lockedChild = locked(child.getId());
            if (canApply(lockedChild, target)) {
                moveAsStaff(lockedChild, staff, target, appliedNote, now);
            }
        }
        return complaint;
    }

    @Transactional
    public Complaint unlink(SessionUser staff, Long complaintId) {
        Complaint complaint = locked(complaintId);
        if (complaint.getParentId() == null) {
            throw new ConflictException("This complaint is not linked to another complaint.");
        }
        Complaint root = locked(complaint.getParentId());
        duplicateLinker.unlink(complaint, root, staff.userId(), clock.instant());
        return complaint;
    }

    private void moveAsStaff(Complaint complaint, SessionUser staff, ComplaintStatus target, String note, Instant now) {
        ComplaintStatus from = complaint.getStatus();
        if (from == ComplaintStatus.OPEN && target.isActive()) {
            complaint.assignTo(staff.userId());
            record(complaint, ComplaintEventType.ASSIGNEE_CHANGED, staff.userId(), null, staff.userId().toString(),
                    null, now);
        }
        if (complaint.getStatus() != target) {
            complaint.moveTo(target, Actor.STAFF, now);
        }
        record(complaint, ComplaintEventType.STATUS_CHANGED, staff.userId(), from.name(), target.name(), note, now);
        complaint.recordFirstResponse(now);
    }

    private static boolean canApply(Complaint child, ComplaintStatus target) {
        if (child.getStatus() == target) {
            return false;
        }
        if (child.getStatus() == ComplaintStatus.OPEN && target.isActive()) {
            return true;
        }
        return ComplaintStateMachine.canMove(child.getStatus(), target, Actor.STAFF);
    }

    @Transactional
    public Complaint reassign(SessionUser staff, Long complaintId, Long assigneeId, String note) {
        Complaint complaint = locked(complaintId);
        if (!complaint.getStatus().isActive()) {
            throw new ConflictException("Only an open complaint can be reassigned.");
        }
        if (!staffLoads.isActiveStaff(assigneeId)) {
            throw new BadRequestException("Choose an active help-desk staff member.");
        }
        if (assigneeId.equals(complaint.getAssigneeId())) {
            throw new ConflictException("The complaint is already assigned to this person.");
        }
        Instant now = clock.instant();
        Long previous = complaint.getAssigneeId();
        complaint.assignTo(assigneeId);
        record(complaint, ComplaintEventType.ASSIGNEE_CHANGED, staff.userId(),
                previous == null ? null : previous.toString(), assigneeId.toString(), clean(note), now);
        complaint.recordFirstResponse(now);
        return complaint;
    }

    @Transactional
    public Complaint comment(SessionUser staff, Long complaintId, String text) {
        Complaint complaint = locked(complaintId);
        if (complaint.getStatus() == ComplaintStatus.CLOSED) {
            throw new ConflictException("A closed complaint cannot take new comments.");
        }
        Instant now = clock.instant();
        record(complaint, ComplaintEventType.COMMENTED, staff.userId(), null, null, text.strip(), now);
        complaint.recordFirstResponse(now);
        return complaint;
    }

    @Transactional
    public Complaint recategorise(SessionUser staff, Long complaintId, ComplaintCategory category, Urgency requestedUrgency) {
        Complaint complaint = locked(complaintId);
        Urgency previousUrgency = complaint.getUrgency();
        Urgency urgency = requestedUrgency == null ? previousUrgency : requestedUrgency;
        boolean categoryChanged = complaint.getCategory() != category;
        if (!categoryChanged && urgency == previousUrgency && !complaint.isNeedsReview()) {
            throw new ConflictException("The complaint already has this category and urgency.");
        }
        Instant now = clock.instant();
        String from = classificationLabel(complaint.getCategory(), previousUrgency);
        SlaTargets targets = SlaTargets.from(settingsRepository.current());
        complaint.recategorise(category, urgency, targets.firstResponseDue(complaint.getCreatedAt(), urgency),
                targets.rescheduleResolve(complaint.getResolveDueAt(), previousUrgency, urgency));
        record(complaint, ComplaintEventType.CATEGORY_CHANGED, staff.userId(), from,
                classificationLabel(category, urgency), null, now);
        if (categoryChanged) {
            handover.handOverIfOwnerLacksTheSkill(complaint, now);
            duplicateLinker.relinkAfterCategoryChange(complaint, now);
        }
        complaint.recordFirstResponse(now);
        return complaint;
    }

    @Transactional
    public Complaint acknowledgeEscalation(SessionUser staff, Long escalationId) {
        Escalation escalation = escalationRepository.findLockedById(escalationId)
                .orElseThrow(() -> new NotFoundException("Escalation not found"));
        if (escalation.isAcknowledged()) {
            throw new ConflictException("This escalation has already been acknowledged.");
        }
        Complaint complaint = locked(escalation.getComplaintId());
        Instant now = clock.instant();
        escalation.acknowledge(staff.userId(), now);
        record(complaint, ComplaintEventType.ESCALATION_ACKNOWLEDGED, staff.userId(), null, null, null, now);
        complaint.recordFirstResponse(now);
        return complaint;
    }

    private static String classificationLabel(ComplaintCategory category, Urgency urgency) {
        return category.name() + "/" + urgency.name();
    }

    private static Instant nextDeadline(Complaint complaint) {
        if (complaint.getStatus().isActive() && complaint.getFirstResponseAt() == null) {
            return complaint.getFirstResponseDueAt();
        }
        return complaint.getResolveDueAt();
    }

    private Complaint locked(Long complaintId) {
        return complaintRepository.findLockedById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found"));
    }

    private void record(Complaint complaint, ComplaintEventType type, Long actor, String from, String to, String note,
                        Instant at) {
        eventRepository.save(new ComplaintEvent(complaint.getId(), type, actor, from, to, note, at));
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }
}
