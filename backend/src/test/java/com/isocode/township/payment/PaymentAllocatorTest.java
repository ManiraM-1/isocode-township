package com.isocode.township.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.common.Money;
import com.isocode.township.payment.PaymentAllocator.Allocation;
import com.isocode.township.payment.PaymentAllocator.AllocationPlan;
import com.isocode.township.payment.PaymentAllocator.ItemType;
import com.isocode.township.payment.PaymentAllocator.OpenItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentAllocatorTest {

    private static final OpenItem SEP_BILL = bill(1L, "2026-09-01", "6370.00");
    private static final OpenItem OCT_BILL = bill(2L, "2026-10-01", "6370.00");
    private static final OpenItem NOV_BILL = bill(3L, "2026-11-01", "6370.00");
    private static final OpenItem SEP_LATE_FEE = lateFee(10L, "2026-09-30", "73.30");

    @Test
    void partPaymentGoesToTheOnlyBill() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("5000.00"), List.of(bill(9L, "2026-10-01", "10660.00")));

        assertThat(plan.allocations()).extracting(a -> a.item().id(), Allocation::amount)
                .containsExactly(tuple(9L, new BigDecimal("5000.00")));
        assertThat(plan.advance()).isEqualTo(Money.ZERO);
    }

    @Test
    void oldestBillPrincipalFirstThenLateFeesThenAdvance() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("13000.00"), List.of(SEP_LATE_FEE, OCT_BILL, SEP_BILL));

        assertThat(plan.allocations()).extracting(a -> a.item().id(), Allocation::amount).containsExactly(
                tuple(1L, new BigDecimal("6370.00")),
                tuple(2L, new BigDecimal("6370.00")),
                tuple(10L, new BigDecimal("73.30")));
        assertThat(plan.advance()).isEqualTo(new BigDecimal("186.70"));
    }

    @Test
    void anyBillPrincipalComesBeforeAnOlderLateFee() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("6370.00"), List.of(SEP_LATE_FEE, NOV_BILL));

        assertThat(plan.allocations()).extracting(a -> a.item().type()).containsExactly(ItemType.BILL);
    }

    @Test
    void paymentSmallerThanTheOldestBillLeavesNewerBillsUntouched() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("1000.00"), List.of(NOV_BILL, SEP_BILL, OCT_BILL));

        assertThat(plan.allocations()).extracting(a -> a.item().id(), Allocation::amount)
                .containsExactly(tuple(1L, new BigDecimal("1000.00")));
    }

    @Test
    void nothingOpenMeansTheWholePaymentIsAdvance() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("500.00"), List.of());

        assertThat(plan.allocations()).isEmpty();
        assertThat(plan.advance()).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    void exactPaymentLeavesNoAdvanceAndSkipsSettledItems() {
        AllocationPlan plan = PaymentAllocator.allocate(Money.of("6370.00"),
                List.of(bill(5L, "2026-08-01", "0.00"), SEP_BILL));

        assertThat(plan.allocations()).extracting(a -> a.item().id()).containsExactly(1L);
        assertThat(plan.advance()).isEqualTo(Money.ZERO);
    }

    @Test
    void zeroOrNegativePaymentsAreRejected() {
        assertThatThrownBy(() -> PaymentAllocator.allocate(BigDecimal.ZERO, List.of(SEP_BILL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAllocator.allocate(new BigDecimal("-1"), List.of(SEP_BILL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OpenItem bill(Long id, String period, String outstanding) {
        return new OpenItem(ItemType.BILL, id, LocalDate.parse(period), Money.of(outstanding));
    }

    private static OpenItem lateFee(Long id, String toDate, String outstanding) {
        return new OpenItem(ItemType.LATE_FEE, id, LocalDate.parse(toDate), Money.of(outstanding));
    }
}
