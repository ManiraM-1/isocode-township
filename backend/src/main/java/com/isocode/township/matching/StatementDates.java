package com.isocode.township.matching;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;

final class StatementDates {

    private static final List<DateTimeFormatter> ACCEPTED = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));

    private StatementDates() {
    }

    static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("the date is missing");
        }
        String text = raw.strip();
        return ACCEPTED.stream()
                .map(format -> parseWith(text, format))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + text + "' is not a date in dd/MM/yyyy, dd-MM-yyyy or yyyy-MM-dd"));
    }

    private static Optional<LocalDate> parseWith(String text, DateTimeFormatter format) {
        try {
            return Optional.of(LocalDate.parse(text, format));
        } catch (DateTimeParseException wrongFormat) {
            return Optional.empty();
        }
    }
}
