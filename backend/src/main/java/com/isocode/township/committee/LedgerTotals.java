package com.isocode.township.committee;

import com.isocode.township.common.Money;
import java.math.BigDecimal;

public record LedgerTotals(BigDecimal opening, BigDecimal billed, BigDecimal lateFeesCharged,
                           BigDecimal lateFeeReversals, BigDecimal collections, BigDecimal closing) {

    public BigDecimal totalDue() {
        return Money.rounded(opening.add(billed).add(lateFeesCharged).subtract(lateFeeReversals));
    }

    public BigDecimal expectedClosing() {
        return Money.rounded(totalDue().subtract(collections));
    }

    public boolean balanced() {
        return expectedClosing().compareTo(closing) == 0;
    }

    public BigDecimal collectionRate() {
        return Percentages.share(collections, totalDue());
    }
}
