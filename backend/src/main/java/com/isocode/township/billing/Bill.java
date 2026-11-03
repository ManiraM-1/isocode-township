package com.isocode.township.billing;

import com.isocode.township.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "bill")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flat_id", nullable = false)
    private Long flatId;

    @Column(nullable = false)
    private LocalDate period;

    @Column(name = "area_sqft", nullable = false)
    private int areaSqft;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal rate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Bill() {
    }

    public Bill(Long flatId, YearMonth month, int areaSqft, BigDecimal rate, BigDecimal amount,
                LocalDate dueDate, Instant createdAt) {
        this.flatId = flatId;
        this.period = month.atDay(1);
        this.areaSqft = areaSqft;
        this.rate = rate;
        this.amount = amount;
        this.dueDate = dueDate;
        this.amountPaid = Money.ZERO;
        this.createdAt = createdAt;
    }

    public BigDecimal principalOutstanding() {
        return amount.subtract(amountPaid);
    }

    public boolean isFullyPaid() {
        return principalOutstanding().signum() == 0;
    }

    public Long getId() {
        return id;
    }

    public Long getFlatId() {
        return flatId;
    }

    public YearMonth getMonth() {
        return YearMonth.from(period);
    }

    public int getAreaSqft() {
        return areaSqft;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }
}
