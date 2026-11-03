package com.isocode.township.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "late_fee_adjustment")
public class LateFeeAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "late_fee_charge_id", nullable = false)
    private Long lateFeeChargeId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LateFeeAdjustment() {
    }

    public LateFeeAdjustment(Long lateFeeChargeId, Long paymentId, BigDecimal amount, Instant createdAt) {
        this.lateFeeChargeId = lateFeeChargeId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getLateFeeChargeId() {
        return lateFeeChargeId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
