package com.isocode.township.complaint;

import com.isocode.township.auth.AppUser;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.auth.Role;
import com.isocode.township.auth.SessionUser;
import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import com.isocode.township.complaint.dto.ComplaintDetail;
import com.isocode.township.complaint.dto.ComplaintDetail.Actions;
import com.isocode.township.complaint.dto.ComplaintDetail.EscalationView;
import com.isocode.township.complaint.dto.ComplaintDetail.Person;
import com.isocode.township.complaint.dto.ComplaintDetail.TimelineEntry;
import com.isocode.township.complaint.dto.ComplaintSummary;
import com.isocode.township.flat.Flat;
import com.isocode.township.flat.FlatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ComplaintViews {

    private static final int SUMMARY_LENGTH = 80;
    private static final Set<ComplaintEventType> STAFF_ID_EVENTS =
            EnumSet.of(ComplaintEventType.AUTO_ASSIGNED, ComplaintEventType.ASSIGNEE_CHANGED,
                    ComplaintEventType.ESCALATED);

    private final ComplaintEventRepository eventRepository;
    private final EscalationRepository escalationRepository;
    private final AppUserRepository userRepository;
    private final FlatRepository flatRepository;
    private final Clock clock;

    public ComplaintViews(ComplaintEventRepository eventRepository, EscalationRepository escalationRepository,
                          AppUserRepository userRepository,
                          FlatRepository flatRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.escalationRepository = escalationRepository;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
        this.clock = clock;
    }

    public ComplaintSummary summary(Complaint complaint) {
        Instant nextDue = nextDue(complaint);
        return new ComplaintSummary(complaint.getId(), complaint.getRefNo(), complaint.getFlatId(),
                flatCode(complaint.getFlatId()), shortSummary(complaint), complaint.getCategory(), complaint.getUrgency(),
                complaint.getStatus(), name(complaint.getAssigneeId()), complaint.getCreatedAt(), nextDue,
                isOverdue(nextDue), complaint.getReopenCount(), complaint.isNeedsReview(),
                complaint.isSafetyEscalated(), escalationOpen(complaint), complaint.getPhotoContentType().isPresent());
    }

    public ComplaintDetail detail(Complaint complaint, SessionUser viewer) {
        Instant nextDue = nextDue(complaint);
        return new ComplaintDetail(complaint.getId(), complaint.getRefNo(), complaint.getFlatId(),
                flatCode(complaint.getFlatId()), name(complaint.getRaisedBy()), complaint.getDescription(),
                complaint.getPhotoContentType().isPresent(), complaint.getCategory(), complaint.getUrgency(),
                complaint.getStatus(), person(complaint.getAssigneeId()), complaint.getCreatedAt(),
                complaint.getFirstResponseDueAt(), complaint.getFirstResponseAt(), complaint.getResolveDueAt(),
                complaint.getResolvedAt(), complaint.getClosedAt(), nextDue, isOverdue(nextDue),
                complaint.getReopenCount(), complaint.getRating().orElse(null), complaint.getRatingComment(),
                complaint.getClassifiedBy(), complaint.getAiCategory(), complaint.getAiUrgency(),
                complaint.getAiConfidence(), complaint.getAiSummary(), complaint.getAiReason(), complaint.isNeedsReview(),
                complaint.isSafetyEscalated(), complaint.getSafetyTerms(), escalation(complaint), timeline(complaint),
                actions(complaint, viewer, escalationOpen(complaint)));
    }

    private EscalationView escalation(Complaint complaint) {
        return escalationRepository.findByComplaintId(complaint.getId())
                .map(escalation -> new EscalationView(escalation.getId(), name(escalation.getDutyManagerId()),
                        escalation.getReason(), escalation.getCreatedAt(), name(escalation.getAcknowledgedBy()),
                        escalation.getAcknowledgedAt()))
                .orElse(null);
    }

    private boolean escalationOpen(Complaint complaint) {
        return escalationRepository.findByComplaintId(complaint.getId())
                .map(escalation -> !escalation.isAcknowledged())
                .orElse(false);
    }

    private List<TimelineEntry> timeline(Complaint complaint) {
        return eventRepository.findByComplaintIdOrderByCreatedAtAscIdAsc(complaint.getId()).stream()
                .map(event -> {
                    Optional<AppUser> actor = Optional.ofNullable(event.getActorUserId()).flatMap(userRepository::findById);
                    boolean staffIds = STAFF_ID_EVENTS.contains(event.getType());
                    return new TimelineEntry(event.getType(),
                            actor.map(AppUser::getFullName).orElse("System"),
                            actor.map(user -> user.getRole().name()).orElse("SYSTEM"),
                            staffIds ? nameOf(event.getFromValue()) : event.getFromValue(),
                            staffIds ? nameOf(event.getToValue()) : event.getToValue(),
                            event.getNote(), event.getCreatedAt());
                })
                .toList();
    }

    private static Actions actions(Complaint complaint, SessionUser viewer, boolean escalationOpen) {
        ComplaintStatus status = complaint.getStatus();
        if (viewer.role() == Role.HELPDESK) {
            return new Actions(false, false, status != ComplaintStatus.CLOSED, status.isActive(),
                    status != ComplaintStatus.CLOSED, escalationOpen,
                    ComplaintStateMachine.nextFor(status, Actor.STAFF));
        }
        boolean resolved = status == ComplaintStatus.RESOLVED;
        return new Actions(resolved, resolved, false, false, false, false, EnumSet.noneOf(ComplaintStatus.class));
    }

    private Instant nextDue(Complaint complaint) {
        if (!complaint.getStatus().isActive()) {
            return null;
        }
        return complaint.getFirstResponseAt() == null ? complaint.getFirstResponseDueAt() : complaint.getResolveDueAt();
    }

    private boolean isOverdue(Instant nextDue) {
        return nextDue != null && clock.instant().isAfter(nextDue);
    }

    private static String shortSummary(Complaint complaint) {
        if (complaint.getAiSummary() != null) {
            return complaint.getAiSummary();
        }
        String text = complaint.getDescription();
        return text.length() <= SUMMARY_LENGTH ? text : text.substring(0, SUMMARY_LENGTH - 1).strip() + "…";
    }

    private String flatCode(Long flatId) {
        return flatRepository.findById(flatId).map(Flat::getCode).orElse(null);
    }

    private Person person(Long userId) {
        return userId == null ? null : new Person(userId, name(userId));
    }

    private String name(Long userId) {
        return userId == null ? null : userRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    private String nameOf(String userIdText) {
        if (userIdText == null) {
            return null;
        }
        return name(Long.valueOf(userIdText));
    }
}
