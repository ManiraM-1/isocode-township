package com.isocode.township.billing;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    public static LocalDate interestStartsAfter(LocalDate dueDate, LocalDate lastChargedDayOrNull) {
        if (lastChargedDayOrNull == null || lastChargedDayOrNull.isBefore(dueDate)) {
            return dueDate;
        }
        return lastChargedDayOrNull;
    }
}
