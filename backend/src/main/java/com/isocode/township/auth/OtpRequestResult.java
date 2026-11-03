package com.isocode.township.auth;

import java.time.Duration;
import java.util.Optional;

public record OtpRequestResult(Duration expiresIn, Optional<String> demoOtp) {
}
