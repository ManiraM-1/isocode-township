package com.isocode.township.complaint.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssigneeChangeRequest(
        @NotNull(message = "Choose who should handle this") Long assigneeId,
        @Size(max = 500, message = "Keep the note under 500 characters") String note) {
}
