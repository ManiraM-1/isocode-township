package com.isocode.township.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.auth.dto.OtpRequestResponse;
import com.isocode.township.common.AppProperties;
import com.isocode.township.common.MessageChannel;
import com.isocode.township.common.OutboxService;
import com.isocode.township.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");
    private static final String PHONE = "+919000000001";
    private static final String CODE = "482913";
    private static final OtpProperties OTP_PROPERTIES =
            new OtpProperties(Duration.ofMinutes(5), 5, 3, Duration.ofMinutes(10));

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));

    private OtpChallengeRepository challengeRepository;
    private AppUserRepository userRepository;
    private OutboxService outboxService;
    private OtpCodeGenerator codeGenerator;
    private AppUser neeraj;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(OtpChallengeRepository.class);
        userRepository = mock(AppUserRepository.class);
        outboxService = mock(OutboxService.class);
        codeGenerator = mock(OtpCodeGenerator.class);
        when(codeGenerator.next()).thenReturn(CODE);
        when(challengeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        neeraj = AppUser.resident(PHONE, "Neeraj Kulkarni", NOW);
        ReflectionTestUtils.setField(neeraj, "id", 7L);
    }

    @Test
    void demoModeReturnsTheCodeInTheResponse() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));

        OtpRequestResult result = service(true).request(PHONE);

        assertThat(result.demoOtp()).contains(CODE);
        assertThat(result.expiresIn()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void outsideDemoModeTheCodeIsNeverReturned() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));

        OtpRequestResult result = service(false).request(PHONE);

        assertThat(result.demoOtp()).isEmpty();
    }

    @Test
    void knownUserGetsTheCodeBySms() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));

        service(false).request(PHONE);

        verify(outboxService).send(eq(7L), eq(MessageChannel.SMS), contains(CODE));
    }

    @Test
    void unknownPhoneGetsTheSameResultButNoSms() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        OtpRequestResult result = service(false).request(PHONE);

        assertThat(result).isEqualTo(new OtpRequestResult(Duration.ofMinutes(5), Optional.empty()));
        verify(challengeRepository).save(any());
        verify(outboxService, never()).send(any(), any(), anyString());
    }

    @Test
    void challengeStoresOnlyAHashOfTheCodeAndExpiresInFiveMinutes() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));
        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);

        service(false).request(PHONE);

        verify(challengeRepository).save(saved.capture());
        assertThat(saved.getValue().getCodeHash()).isNotEqualTo(CODE);
        assertThat(passwordEncoder.matches(CODE, saved.getValue().getCodeHash())).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void fourthRequestWithinTenMinutesIsRateLimited() {
        when(challengeRepository.countByPhoneAndCreatedAtAfter(PHONE, NOW.minus(Duration.ofMinutes(10))))
                .thenReturn(3L);

        assertThatThrownBy(() -> service(false).request(PHONE)).isInstanceOf(TooManyRequestsException.class);
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void wrongCodeCountsAnAttemptAndReportsWhatIsLeft() {
        OtpChallenge challenge = openChallenge();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));

        assertThatThrownBy(() -> service(false).verify(PHONE, "000000"))
                .isInstanceOfSatisfying(OtpRejectedException.class, rejected -> {
                    assertThat(rejected.reason()).isEqualTo(OtpRejection.INVALID_CODE);
                    assertThat(rejected.attemptsRemaining()).isEqualTo(4);
                });
        assertThat(challenge.getAttempts()).isEqualTo(1);
    }

    @Test
    void fifthWrongAttemptLocksAndSixthIsRejectedEvenWithTheRightCode() {
        openChallenge();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));
        OtpService service = service(false);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> service.verify(PHONE, "000000")).isInstanceOf(OtpRejectedException.class);
        }

        assertThatThrownBy(() -> service.verify(PHONE, "000000"))
                .isInstanceOfSatisfying(OtpRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(OtpRejection.LOCKED));
        assertThatThrownBy(() -> service.verify(PHONE, CODE))
                .isInstanceOfSatisfying(OtpRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(OtpRejection.LOCKED));
    }

    @Test
    void correctCodeForUnknownPhoneIsTreatedAsWrong() {
        OtpChallenge challenge = openChallenge();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(false).verify(PHONE, CODE))
                .isInstanceOfSatisfying(OtpRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(OtpRejection.INVALID_CODE));
        assertThat(challenge.getAttempts()).isEqualTo(1);
    }

    @Test
    void codeIsRejectedAtTheExactExpiryInstant() {
        when(challengeRepository.findFirstByPhoneOrderByIdDesc(PHONE)).thenReturn(Optional.of(
                new OtpChallenge(PHONE, passwordEncoder.encode(CODE), NOW.minus(Duration.ofMinutes(5)), NOW)));
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));

        assertThatThrownBy(() -> service(false).verify(PHONE, CODE))
                .isInstanceOfSatisfying(OtpRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(OtpRejection.EXPIRED));
    }

    @Test
    void correctCodeSignsInOnceAndThenCannotBeReused() {
        OtpChallenge challenge = openChallenge();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));
        OtpService service = service(false);

        assertThat(service.verify(PHONE, CODE)).isSameAs(neeraj);
        assertThat(challenge.isConsumed()).isTrue();

        assertThatThrownBy(() -> service.verify(PHONE, CODE))
                .isInstanceOfSatisfying(OtpRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(OtpRejection.EXPIRED));
    }

    @Test
    void inDemoModeKnownAndUnknownPhonesGetTheSameResponseShape() throws Exception {
        String unknownPhone = "+919999999999";
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(neeraj));
        when(userRepository.findByPhone(unknownPhone)).thenReturn(Optional.empty());
        OtpService service = service(true);

        OtpRequestResult known = service.request(PHONE);
        OtpRequestResult unknown = service.request(unknownPhone);

        assertThat(unknown).isEqualTo(known);
        ObjectMapper json = new ObjectMapper();
        JsonNode knownJson = json.valueToTree(toResponse(known));
        JsonNode unknownJson = json.valueToTree(toResponse(unknown));
        assertThat(fieldNames(unknownJson)).containsExactly("expiresInSeconds", "demoOtp");
        assertThat(fieldNames(unknownJson)).isEqualTo(fieldNames(knownJson));
        assertThat(unknownJson.get("demoOtp").asText()).matches("\\d{6}");
    }

    @Test
    void everyVerifyRunsExactlyOneHashCheckWhetherOrNotThePhoneIsKnown() {
        PasswordEncoder countingEncoder = spy(new BCryptPasswordEncoder(4));
        OtpChallenge knownChallenge = new OtpChallenge(PHONE, countingEncoder.encode(CODE), NOW, NOW.plusSeconds(300));
        OtpChallenge unknownChallenge = new OtpChallenge("+919999999999", countingEncoder.encode(CODE), NOW, NOW.plusSeconds(300));
        when(challengeRepository.findFirstByPhoneOrderByIdDesc(PHONE)).thenReturn(Optional.of(knownChallenge));
        when(challengeRepository.findFirstByPhoneOrderByIdDesc("+919999999999")).thenReturn(Optional.of(unknownChallenge));
        when(challengeRepository.findFirstByPhoneOrderByIdDesc("+919888888888")).thenReturn(Optional.empty());
        when(userRepository.findByPhone(anyString())).thenAnswer(invocation ->
                PHONE.equals(invocation.getArgument(0)) ? Optional.of(neeraj) : Optional.empty());
        OtpService service = new OtpService(challengeRepository, userRepository, outboxService, codeGenerator,
                countingEncoder, OTP_PROPERTIES, new AppProperties(ZoneId.of("Asia/Kolkata"), false), clock);

        for (String phone : new String[] {PHONE, "+919999999999", "+919888888888"}) {
            clearInvocations(countingEncoder);
            assertThatThrownBy(() -> service.verify(phone, "000000")).isInstanceOf(OtpRejectedException.class);
            verify(countingEncoder, times(1)).matches(eq("000000"), anyString());
        }
    }

    private static OtpRequestResponse toResponse(OtpRequestResult result) {
        return new OtpRequestResponse(result.expiresIn().toSeconds(), result.demoOtp().orElse(null));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private OtpChallenge openChallenge() {
        OtpChallenge challenge = new OtpChallenge(PHONE, passwordEncoder.encode(CODE), NOW, NOW.plus(Duration.ofMinutes(5)));
        when(challengeRepository.findFirstByPhoneOrderByIdDesc(PHONE)).thenReturn(Optional.of(challenge));
        return challenge;
    }

    private OtpService service(boolean demoMode) {
        return new OtpService(challengeRepository, userRepository, outboxService, codeGenerator, passwordEncoder,
                OTP_PROPERTIES, new AppProperties(ZoneId.of("Asia/Kolkata"), demoMode), clock);
    }
}
