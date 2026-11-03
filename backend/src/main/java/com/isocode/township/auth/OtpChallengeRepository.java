package com.isocode.township.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    long countByPhoneAndCreatedAtAfter(String phone, Instant after);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByPhoneOrderByIdDesc(String phone);
}
