package com.isocode.township.matching.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssignRequest(
        @NotNull(message = "Choose the flat this payment belongs to") Long flatId,
        @Size(max = 500, message = "Keep the note under 500 characters") String note) {
}
