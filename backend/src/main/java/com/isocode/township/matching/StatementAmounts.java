package com.isocode.township.matching;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.util.regex.Pattern;

final class StatementAmounts {

    private static final Pattern CURRENCY_PREFIX = Pattern.compile("^(?i)(rs\\.?|inr)");
    private static final Pattern PLAIN_AMOUNT = Pattern.compile("\\d+(\\.\\d{1,2})?");

    private StatementAmounts() {
    }

    static BigDecimal parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = CURRENCY_PREFIX.matcher(raw.replace("₹", "").strip()).replaceFirst("")
                .replace(",", "")
                .replaceAll("\\s", "");
        if (!PLAIN_AMOUNT.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("'" + raw.strip() + "' is not an amount like 1,23,456.00");
        }
        return Money.rounded(new BigDecimal(cleaned));
    }
}
