package com.isocode.township.committee;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Percentages {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percentages() {
    }

    public static BigDecimal share(long part, long whole) {
        return share(BigDecimal.valueOf(part), BigDecimal.valueOf(whole));
    }

    public static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }
}
