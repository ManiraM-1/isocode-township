package com.isocode.township.payment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentIntent i where i.id = :id")
    Optional<PaymentIntent> findLockedById(@Param("id") Long id);
}
