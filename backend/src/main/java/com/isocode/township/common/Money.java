package com.isocode.township.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal of(String amount) {
        return new BigDecimal(amount).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal rounded(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }
}
