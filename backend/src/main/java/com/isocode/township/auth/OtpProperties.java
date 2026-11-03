package com.isocode.township.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(Duration ttl, int maxAttempts, int maxRequestsPerWindow, Duration requestWindow) {
}
