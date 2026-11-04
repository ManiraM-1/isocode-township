package com.isocode.township.complaint.dto;

import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import jakarta.validation.constraints.NotNull;

public record CategoryChangeRequest(
        @NotNull(message = "Choose a category") ComplaintCategory category,
        Urgency urgency) {
}
