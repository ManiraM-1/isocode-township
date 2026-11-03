package com.isocode.township.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenRequest(
        @NotBlank(message = "Tell us what is still wrong")
        @Size(min = 5, max = 500, message = "The reason must be 5 to 500 characters")
        String reason) {
}
