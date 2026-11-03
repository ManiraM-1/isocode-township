package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.common.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BillStatusTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 10);

    @Test
    void unpaidBillIsDueUntilAndIncludingTheDueDate() {
        assertThat(BillStatus.of(unpaidBill(), DUE.minusDays(1))).isEqualTo(BillStatus.DUE);
        assertThat(BillStatus.of(unpaidBill(), DUE)).isEqualTo(BillStatus.DUE);
    }

    @Test
    void unpaidBillIsOverdueFromTheDayAfter() {
        assertThat(BillStatus.of(unpaidBill(), DUE.plusDays(1))).isEqualTo(BillStatus.OVERDUE);
    }

    @Test
    void fullyPaidBillIsPaidEvenAfterTheDueDate() {
        Bill bill = unpaidBill();
        ReflectionTestUtils.setField(bill, "amountPaid", Money.of("6370.00"));

        assertThat(BillStatus.of(bill, DUE.plusDays(30))).isEqualTo(BillStatus.PAID);
    }

    @Test
    void partlyPaidBillIsStillOverdue() {
        Bill bill = unpaidBill();
        ReflectionTestUtils.setField(bill, "amountPaid", Money.of("5000.00"));

        assertThat(BillStatus.of(bill, DUE.plusDays(1))).isEqualTo(BillStatus.OVERDUE);
    }

    private static Bill unpaidBill() {
        return new Bill(1L, YearMonth.of(2026, 11), 980, Money.of("6.50"), Money.of("6370.00"), DUE,
                Instant.parse("2026-11-01T00:00:00Z"));
    }
}
