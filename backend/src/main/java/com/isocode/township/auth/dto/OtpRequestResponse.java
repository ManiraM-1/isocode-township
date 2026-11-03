package com.isocode.township.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpRequestResponse(long expiresInSeconds, String demoOtp) {
}
