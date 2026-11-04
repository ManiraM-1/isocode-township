package com.isocode.township.complaint.dto;

import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.ComplaintStatus;
import com.isocode.township.complaint.Urgency;
import java.time.Instant;

public record ComplaintSummary(
        Long id,
        String refNo,
        Long flatId,
        String flatCode,
        String summary,
        ComplaintCategory category,
        Urgency urgency,
        ComplaintStatus status,
        String assigneeName,
        Instant createdAt,
        Instant nextDueAt,
        boolean overdue,
        int reopenCount,
        boolean needsReview,
        boolean safetyEscalated,
        boolean escalationOpen,
        Long openEscalationId,
        String linkedToRefNo,
        int linkedCount,
        boolean hasPhoto) {
}
