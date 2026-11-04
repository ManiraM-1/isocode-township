package com.isocode.township.complaint.dto;

import com.isocode.township.complaint.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotNull(message = "Choose the new status") ComplaintStatus status,
        @Size(max = 500, message = "Keep the note under 500 characters") String note,
        boolean applyToLinked) {
}
