package com.isocode.township.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyBody(
        @NotBlank(message = "Enter your mobile number")
        @Pattern(regexp = PhoneFormat.INDIAN_MOBILE, message = PhoneFormat.MESSAGE)
        String phone,

        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "^[0-9]{6}$", message = "The code has 6 digits")
        String code) {
}
