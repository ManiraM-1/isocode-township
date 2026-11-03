package com.isocode.township.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record BillingRunResult(
        YearMonth month,
        int billsCreated,
        LocalDate lateInterestPostedUpTo,
        int lateFeeChargesPosted,
        BigDecimal lateFeeTotal) {
}
