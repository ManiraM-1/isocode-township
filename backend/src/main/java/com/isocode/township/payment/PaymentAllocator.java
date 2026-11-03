package com.isocode.township.payment;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PaymentAllocator {

    private static final Comparator<OpenItem> OLDEST_BILL_PRINCIPAL_THEN_OLDEST_LATE_FEE =
            Comparator.comparing(OpenItem::type)
                    .thenComparing(OpenItem::oldestFirstBy)
                    .thenComparing(OpenItem::id);

    private PaymentAllocator() {
    }

    public static AllocationPlan allocate(BigDecimal amount, List<OpenItem> openItems) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        BigDecimal remaining = Money.rounded(amount);
        List<Allocation> allocations = new ArrayList<>();
        for (OpenItem item : openItems.stream().sorted(OLDEST_BILL_PRINCIPAL_THEN_OLDEST_LATE_FEE).toList()) {
            if (remaining.signum() == 0) {
                break;
            }
            if (item.outstanding().signum() <= 0) {
                continue;
            }
            BigDecimal applied = remaining.min(item.outstanding());
            allocations.add(new Allocation(item, applied));
            remaining = remaining.subtract(applied);
        }
        return new AllocationPlan(List.copyOf(allocations), remaining);
    }

    public enum ItemType {
        BILL,
        LATE_FEE
    }

    public record OpenItem(ItemType type, Long id, LocalDate oldestFirstBy, BigDecimal outstanding) {
    }

    public record Allocation(OpenItem item, BigDecimal amount) {
    }

    public record AllocationPlan(List<Allocation> allocations, BigDecimal advance) {
    }
}
