package com.isocode.township.billing;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public final class LateFeeCalculator {

    private static final BigDecimal PERCENT_DAYS_PER_YEAR = BigDecimal.valueOf(100L * 365L);

    private LateFeeCalculator() {
    }

    public static BigDecimal interest(BigDecimal principalOutstanding, BigDecimal annualPct,
                                      LocalDate fromExclusive, LocalDate toInclusive) {
        long days = ChronoUnit.DAYS.between(fromExclusive, toInclusive);
        if (days <= 0 || principalOutstanding.signum() <= 0 || annualPct.signum() <= 0) {
            return Money.ZERO;
        }
        return principalOutstanding
                .multiply(annualPct)
                .multiply(BigDecimal.valueOf(days))
                .divide(PERCENT_DAYS_PER_YEAR, Money.SCALE, Money.ROUNDING);
    }

    public static BigDecimal interestOnDecliningPrincipal(BigDecimal billAmount, List<PrincipalReduction> reductions,
                                                          BigDecimal annualPct, LocalDate fromExclusive,
                                                          LocalDate toInclusive) {
        if (!toInclusive.isAfter(fromExclusive) || annualPct.signum() <= 0) {
            return Money.ZERO;
        }
        List<PrincipalReduction> inDateOrder = reductions.stream()
                .sorted(Comparator.comparing(PrincipalReduction::on))
                .toList();

        BigDecimal principal = billAmount;
        for (PrincipalReduction reduction : inDateOrder) {
            if (!reduction.on().isAfter(fromExclusive)) {
                principal = principal.subtract(reduction.amount());
            }
        }

        BigDecimal principalDays = BigDecimal.ZERO;
        LocalDate lastDayCounted = fromExclusive;
        for (PrincipalReduction reduction : inDateOrder) {
            if (!reduction.on().isAfter(fromExclusive) || reduction.on().isAfter(toInclusive)) {
                continue;
            }
            LocalDate dayBeforeReduction = reduction.on().minusDays(1);
            principalDays = principalDays.add(principalOverDays(principal, lastDayCounted, dayBeforeReduction));
            principal = principal.subtract(reduction.amount());
            lastDayCounted = dayBeforeReduction;
        }
        principalDays = principalDays.add(principalOverDays(principal, lastDayCounted, toInclusive));

        return principalDays
                .multiply(annualPct)
                .divide(PERCENT_DAYS_PER_YEAR, Money.SCALE, Money.ROUNDING);
    }

    private static BigDecimal principalOverDays(BigDecimal principal, LocalDate fromExclusive, LocalDate toInclusive) {
        long days = ChronoUnit.DAYS.between(fromExclusive, toInclusive);
        if (days <= 0 || principal.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return principal.multiply(BigDecimal.valueOf(days));
    }

    public static LocalDate interestStartsAfter(LocalDate dueDate, LocalDate lastChargedDayOrNull) {
        if (lastChargedDayOrNull == null || lastChargedDayOrNull.isBefore(dueDate)) {
            return dueDate;
        }
        return lastChargedDayOrNull;
    }
}
