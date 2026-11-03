package com.isocode.township.auth;

import com.isocode.township.common.AppProperties;
import com.isocode.township.common.MessageChannel;
import com.isocode.township.common.OutboxService;
import com.isocode.township.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private static final String SMS_TEMPLATE =
            "Your township app sign-in code is %s. It expires in %d minutes. Do not share it with anyone.";

    private final OtpChallengeRepository challengeRepository;
    private final AppUserRepository userRepository;
    private final OutboxService outboxService;
    private final OtpCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final OtpProperties otpProperties;
    private final AppProperties appProperties;
    private final Clock clock;
    private final String unknownPhoneHash;

    public OtpService(OtpChallengeRepository challengeRepository, AppUserRepository userRepository,
                      OutboxService outboxService, OtpCodeGenerator codeGenerator, PasswordEncoder passwordEncoder,
                      OtpProperties otpProperties, AppProperties appProperties, Clock clock) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.outboxService = outboxService;
        this.codeGenerator = codeGenerator;
        this.passwordEncoder = passwordEncoder;
        this.otpProperties = otpProperties;
        this.appProperties = appProperties;
        this.clock = clock;
        this.unknownPhoneHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public OtpRequestResult request(String phone) {
        Instant now = clock.instant();
        long recentRequests = challengeRepository.countByPhoneAndCreatedAtAfter(
                phone, now.minus(otpProperties.requestWindow()));
        if (recentRequests >= otpProperties.maxRequestsPerWindow()) {
            throw new TooManyRequestsException("Too many codes requested. Please wait a few minutes and try again.");
        }

        String code = codeGenerator.next();
        challengeRepository.save(new OtpChallenge(
                phone, passwordEncoder.encode(code), now, now.plus(otpProperties.ttl())));
        activeUser(phone).ifPresent(user -> outboxService.send(user.getId(), MessageChannel.SMS,
                SMS_TEMPLATE.formatted(code, otpProperties.ttl().toMinutes())));

        Optional<String> demoOtp = appProperties.demoMode() ? Optional.of(code) : Optional.empty();
        return new OtpRequestResult(otpProperties.ttl(), demoOtp);
    }

    @Transactional(noRollbackFor = OtpRejectedException.class)
    public AppUser verify(String phone, String code) {
        Instant now = clock.instant();
        Optional<OtpChallenge> latest = challengeRepository.findFirstByPhoneOrderByIdDesc(phone);
        Optional<AppUser> user = activeUser(phone);
        String expectedHash = user.isPresent() && latest.isPresent() ? latest.get().getCodeHash() : unknownPhoneHash;
        boolean codeMatches = passwordEncoder.matches(code, expectedHash);

        OtpChallenge challenge = latest.orElseThrow(() -> new OtpRejectedException(OtpRejection.EXPIRED, 0));
        if (challenge.isConsumed() || challenge.isExpiredAt(now)) {
            throw new OtpRejectedException(OtpRejection.EXPIRED, 0);
        }
        if (challenge.getAttempts() >= otpProperties.maxAttempts()) {
            throw new OtpRejectedException(OtpRejection.LOCKED, 0);
        }
        if (user.isEmpty() || !codeMatches) {
            challenge.recordFailedAttempt();
            int attemptsRemaining = otpProperties.maxAttempts() - challenge.getAttempts();
            throw attemptsRemaining > 0
                    ? new OtpRejectedException(OtpRejection.INVALID_CODE, attemptsRemaining)
                    : new OtpRejectedException(OtpRejection.LOCKED, 0);
        }

        challenge.consume(now);
        return user.get();
    }

    private Optional<AppUser> activeUser(String phone) {
        return userRepository.findByPhone(phone).filter(AppUser::isActive);
    }
}
