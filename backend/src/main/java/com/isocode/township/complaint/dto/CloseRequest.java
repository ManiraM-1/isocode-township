package com.isocode.township.complaint.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloseRequest(
        @NotNull(message = "Choose a rating from 1 to 5")
        @Min(value = 1, message = "Choose a rating from 1 to 5")
        @Max(value = 5, message = "Choose a rating from 1 to 5")
        Integer rating,

        @Size(max = 500, message = "Keep the comment under 500 characters")
        String comment) {
}
