package com.isocode.township.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private final String secret = randomSecret();

    @Test
    void issuedTokenCarriesUserIdAndRole() {
        JwtService service = serviceAt(NOW, secret);

        String token = service.issue(staffWithId(11L));

        assertThat(service.parse(token)).contains(new SessionUser(11L, Role.HELPDESK));
    }

    @Test
    void tokenIsValidForTwelveHoursOnly() {
        String token = serviceAt(NOW, secret).issue(staffWithId(11L));

        assertThat(serviceAt(NOW.plus(Duration.ofHours(12)).minusSeconds(1), secret).parse(token)).isPresent();
        assertThat(serviceAt(NOW.plus(Duration.ofHours(12)).plusSeconds(1), secret).parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = serviceAt(NOW, randomSecret()).issue(staffWithId(11L));

        assertThat(serviceAt(NOW, secret).parse(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = serviceAt(NOW, secret).issue(staffWithId(11L));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(serviceAt(NOW, secret).parse(tampered)).isEmpty();
        assertThat(serviceAt(NOW, secret).parse("not-a-jwt")).isEmpty();
    }

    @Test
    void shortOrMissingSecretStopsStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(12)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too-short");
        assertThatThrownBy(() -> new JwtProperties("${JWT_SECRET}", Duration.ofHours(12)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtService serviceAt(Instant now, String secret) {
        return new JwtService(new JwtProperties(secret, Duration.ofHours(12)), Clock.fixed(now, KOLKATA));
    }

    private static AppUser staffWithId(Long id) {
        AppUser user = AppUser.staff("+919000000101", "Anjali Deshmukh", List.of(), false, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static String randomSecret() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }
}
