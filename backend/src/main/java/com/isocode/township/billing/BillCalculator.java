package com.isocode.township.billing;

import com.isocode.township.common.Money;
import java.math.BigDecimal;

public final class BillCalculator {

    private BillCalculator() {
    }

    public static BigDecimal amount(int areaSqft, BigDecimal ratePerSqft) {
        if (areaSqft <= 0) {
            throw new IllegalArgumentException("Area must be positive");
        }
        if (ratePerSqft == null || ratePerSqft.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        return Money.rounded(ratePerSqft.multiply(BigDecimal.valueOf(areaSqft)));
    }
}
