package com.isocode.township.billing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class LedgerDescriptions {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private LedgerDescriptions() {
    }

    static String bill(YearMonth month) {
        return "Maintenance for " + MONTH.format(month);
    }

    static String lateFee(YearMonth billMonth, LocalDate firstDay, LocalDate lastDay) {
        return "Late interest on " + MONTH.format(billMonth) + " bill, "
                + DAY_MONTH.format(firstDay) + " to " + DAY_MONTH_YEAR.format(lastDay);
    }
}
