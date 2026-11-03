package com.isocode.township.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Write a comment")
        @Size(max = 1000, message = "Keep the comment under 1000 characters")
        String text) {
}
