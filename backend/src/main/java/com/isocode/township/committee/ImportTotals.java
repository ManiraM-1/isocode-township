package com.isocode.township.committee;

import java.math.BigDecimal;

public record ImportTotals(int imports, int autoPosted, int reconciled, int suggested, int conflicts, int unmatched,
                           int possibleDuplicates) {

    public int newPaymentRows() {
        return autoPosted + suggested + conflicts + unmatched + possibleDuplicates;
    }

    public BigDecimal autoPostShare() {
        return Percentages.share(autoPosted, newPaymentRows());
    }
}
