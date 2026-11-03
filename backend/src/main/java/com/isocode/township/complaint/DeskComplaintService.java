package com.isocode.township.complaint;

import com.isocode.township.auth.SessionUser;
import com.isocode.township.common.BadRequestException;
import com.isocode.township.common.ConflictException;
import com.isocode.township.common.NotFoundException;
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
    private final StaffLoads staffLoads;
    private final Clock clock;

    public DeskComplaintService(ComplaintRepository complaintRepository, ComplaintEventRepository eventRepository,
                                StaffLoads staffLoads, Clock clock) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.staffLoads = staffLoads;
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
    public Complaint changeStatus(SessionUser staff, Long complaintId, ComplaintStatus target, String note) {
        Complaint complaint = locked(complaintId);
        Instant now = clock.instant();
        ComplaintStatus from = complaint.getStatus();
        if (from == ComplaintStatus.OPEN && target.isActive()) {
            complaint.assignTo(staff.userId());
            record(complaint, ComplaintEventType.ASSIGNEE_CHANGED, staff.userId(), null, staff.userId().toString(),
                    null, now);
        }
        if (complaint.getStatus() != target) {
            complaint.moveTo(target, Actor.STAFF, now);
        }
        record(complaint, ComplaintEventType.STATUS_CHANGED, staff.userId(), from.name(), target.name(), clean(note), now);
        complaint.recordFirstResponse(now);
        return complaint;
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
