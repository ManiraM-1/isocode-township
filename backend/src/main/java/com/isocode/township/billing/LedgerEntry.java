package com.isocode.township.billing;

import com.isocode.township.common.Money;
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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entry")
public class LedgerEntry {

    public static final String REF_BILL = "BILL";
    public static final String REF_LATE_FEE_CHARGE = "LATE_FEE_CHARGE";
    public static final String REF_PAYMENT = "PAYMENT";
    public static final String REF_LATE_FEE_ADJUSTMENT = "LATE_FEE_ADJUSTMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LedgerEntryType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal credit;

    @Column(name = "ref_type", nullable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected LedgerEntry() {
    }

    private LedgerEntry(Long flatId, LocalDate entryDate, LedgerEntryType type, BigDecimal debit, BigDecimal credit,
                        String refType, Long refId, String description, Instant createdAt, Long createdBy) {
        this.flatId = flatId;
        this.entryDate = entryDate;
        this.type = type;
        this.debit = debit;
        this.credit = credit;
        this.refType = refType;
        this.refId = refId;
        this.description = description;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public static LedgerEntry debitForBill(Bill bill, String description, Instant createdAt, Long createdBy) {
        return new LedgerEntry(bill.getFlatId(), bill.getMonth().atDay(1), LedgerEntryType.BILL,
                bill.getAmount(), Money.ZERO, REF_BILL, bill.getId(), description, createdAt, createdBy);
    }

    public static LedgerEntry creditForPayment(Long flatId, Long paymentId, LocalDate paidOn, BigDecimal amount,
                                               String description, Instant createdAt, Long createdBy) {
        return new LedgerEntry(flatId, paidOn, LedgerEntryType.PAYMENT, Money.ZERO, amount,
                REF_PAYMENT, paymentId, description, createdAt, createdBy);
    }

    public static LedgerEntry creditForLateFeeAdjustment(Long flatId, Long adjustmentId, LocalDate recordedOn,
                                                         BigDecimal amount, String description, Instant createdAt,
                                                         Long createdBy) {
        return new LedgerEntry(flatId, recordedOn, LedgerEntryType.REVERSAL, Money.ZERO, amount,
                REF_LATE_FEE_ADJUSTMENT, adjustmentId, description, createdAt, createdBy);
    }

    public static LedgerEntry debitForLateFee(LateFeeCharge charge, String description, Instant createdAt,
                                              Long createdBy) {
        return new LedgerEntry(charge.getFlatId(), charge.getToDate(), LedgerEntryType.LATE_FEE,
                charge.getAmount(), Money.ZERO, REF_LATE_FEE_CHARGE, charge.getId(), description, createdAt, createdBy);
    }

    public BigDecimal signedAmount() {
        return debit.subtract(credit);
    }

    public Long getId() {
        return id;
    }

    public Long getFlatId() {
        return flatId;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public String getDescription() {
        return description;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
