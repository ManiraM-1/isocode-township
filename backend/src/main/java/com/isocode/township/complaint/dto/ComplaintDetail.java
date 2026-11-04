package com.isocode.township.complaint.dto;

import com.isocode.township.complaint.ClassifiedBy;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.ComplaintEventType;
import com.isocode.township.complaint.ComplaintStatus;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ComplaintDetail(
        Long id,
        String refNo,
        Long flatId,
        String flatCode,
        String raisedBy,
        String description,
        boolean hasPhoto,
        ComplaintCategory category,
        Urgency urgency,
        ComplaintStatus status,
        Person assignee,
        Instant createdAt,
        Instant firstResponseDueAt,
        Instant firstResponseAt,
        Instant resolveDueAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant nextDueAt,
        boolean overdue,
        int reopenCount,
        Integer rating,
        String ratingComment,
        ClassifiedBy classifiedBy,
        ComplaintCategory aiCategory,
        Urgency aiUrgency,
        BigDecimal aiConfidence,
        String aiSummary,
        String aiReason,
        boolean needsReview,
        boolean safetyEscalated,
        List<String> safetyTerms,
        EscalationView escalation,
        List<TimelineEntry> timeline,
        Actions actions) {

    public record EscalationView(Long id, String dutyManager, String reason, Instant raisedAt, String acknowledgedBy,
                                 Instant acknowledgedAt) {
    }

    public record Person(Long id, String name) {
    }

    public record TimelineEntry(ComplaintEventType type, String actorName, String actorRole, String from, String to,
                                String note, Instant at) {
    }

    public record Actions(boolean canClose, boolean canReopen, boolean canComment, boolean canReassign,
                          boolean canRecategorise, boolean canAcknowledgeEscalation,
                          Set<ComplaintStatus> statusChanges) {
    }
}
