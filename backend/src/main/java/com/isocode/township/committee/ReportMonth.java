package com.isocode.township.committee;

import com.isocode.township.common.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public record ReportMonth(YearMonth month, LocalDate firstDay, LocalDate asOf, LocalDate lastDueDateCounted,
                         Instant windowStart, Instant windowEnd) {

    public static ReportMonth parse(String text, Clock clock) {
        YearMonth current = YearMonth.now(clock);
        if (text == null || text.isBlank()) {
            return of(current, clock);
        }
        YearMonth month;
        try {
            month = YearMonth.parse(text.strip());
        } catch (DateTimeParseException invalid) {
            throw new BadRequestException("Choose a month as YYYY-MM, for example 2026-10.");
        }
        if (month.isAfter(current)) {
            throw new BadRequestException("The committee report covers months up to the current one.");
        }
        return of(month, clock);
    }

    public static ReportMonth of(YearMonth month, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate lastDay = month.atEndOfMonth();
        LocalDate asOf = lastDay.isBefore(today) ? lastDay : today;
        LocalDate yesterday = today.minusDays(1);
        LocalDate lastDueDateCounted = lastDay.isBefore(yesterday) ? lastDay : yesterday;
        return new ReportMonth(month, month.atDay(1), asOf, lastDueDateCounted,
                month.atDay(1).atStartOfDay(clock.getZone()).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant());
    }
}
