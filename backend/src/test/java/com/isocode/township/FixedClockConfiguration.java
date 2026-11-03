package com.isocode.township;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    public static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");
    public static final LocalDate TODAY = LocalDate.ofInstant(NOW, KOLKATA);

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, KOLKATA);
    }
}
