package com.isocode.township.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "late_fee_charge_id")
    private Long lateFeeChargeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_on", nullable = false)
    private LocalDate allocatedOn;

    protected PaymentAllocation() {
    }

    private PaymentAllocation(Long paymentId, Long billId, Long lateFeeChargeId, BigDecimal amount,
                              LocalDate allocatedOn) {
        this.paymentId = paymentId;
        this.billId = billId;
        this.lateFeeChargeId = lateFeeChargeId;
        this.amount = amount;
        this.allocatedOn = allocatedOn;
    }

    public static PaymentAllocation toBill(Long paymentId, Long billId, BigDecimal amount, LocalDate allocatedOn) {
        return new PaymentAllocation(paymentId, billId, null, amount, allocatedOn);
    }

    public static PaymentAllocation toLateFee(Long paymentId, Long chargeId, BigDecimal amount, LocalDate allocatedOn) {
        return new PaymentAllocation(paymentId, null, chargeId, amount, allocatedOn);
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getLateFeeChargeId() {
        return lateFeeChargeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getAllocatedOn() {
        return allocatedOn;
    }
}
