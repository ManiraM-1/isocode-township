package com.isocode.township.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_intent")
public class PaymentIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentIntentStatus status;

    @Column(name = "gateway_ref", nullable = false, unique = true, length = 40)
    private String gatewayRef;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentIntent() {
    }

    public PaymentIntent(Long flatId, BigDecimal amount, String idempotencyKey, String gatewayRef, Long createdBy,
                         Instant createdAt) {
        this.flatId = flatId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.gatewayRef = gatewayRef;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.status = PaymentIntentStatus.CREATED;
    }

    public void markSucceeded(Instant now) {
        status = PaymentIntentStatus.SUCCEEDED;
        completedAt = now;
    }

    public void markFailed(Instant now) {
        status = PaymentIntentStatus.FAILED;
        completedAt = now;
    }

    public boolean isOpen() {
        return status == PaymentIntentStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public Long getFlatId() {
        return flatId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentIntentStatus getStatus() {
        return status;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
