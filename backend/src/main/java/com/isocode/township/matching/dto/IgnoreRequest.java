package com.isocode.township.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IgnoreRequest(
        @NotBlank(message = "Say why this is not a maintenance payment")
        @Size(min = 3, max = 500, message = "The reason must be 3 to 500 characters")
        String reason) {
}
