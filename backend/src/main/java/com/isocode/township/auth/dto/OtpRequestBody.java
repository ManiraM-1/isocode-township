package com.isocode.township.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequestBody(
        @NotBlank(message = "Enter your mobile number")
        @Pattern(regexp = PhoneFormat.INDIAN_MOBILE, message = PhoneFormat.MESSAGE)
        String phone) {
}
