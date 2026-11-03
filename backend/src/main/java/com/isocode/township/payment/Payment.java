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
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentSource source;

    @Column(name = "gateway_ref", length = 40)
    private String gatewayRef;

    @Column(length = 40)
    private String utr;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 20)
    private String receiptNo;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Long flatId, BigDecimal amount, PaymentMethod method, PaymentSource source, String gatewayRef,
                   String utr, LocalDate paidOn, String receiptNo, Long postedBy, Instant createdAt) {
        this.flatId = flatId;
        this.amount = amount;
        this.method = method;
        this.source = source;
        this.gatewayRef = gatewayRef;
        this.utr = utr;
        this.paidOn = paidOn;
        this.receiptNo = receiptNo;
        this.postedBy = postedBy;
        this.createdAt = createdAt;
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

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentSource getSource() {
        return source;
    }

    public Optional<String> getGatewayRef() {
        return Optional.ofNullable(gatewayRef);
    }

    public Optional<String> getUtr() {
        return Optional.ofNullable(utr);
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public Long getPostedBy() {
        return postedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
