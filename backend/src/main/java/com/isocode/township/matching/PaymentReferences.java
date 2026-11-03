package com.isocode.township.matching;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentReferences {

    private static final Pattern TWELVE_DIGIT_UTR = Pattern.compile("(?<!\\d)\\d{12}(?!\\d)");
    private static final Pattern UPI_ID = Pattern.compile("[a-z0-9._-]{2,}@[a-z]{2,}");
    private static final Pattern GATEWAY_REF = Pattern.compile("PGTEST-[A-Z0-9]+");

    private PaymentReferences() {
    }

    public static Optional<String> utr(String description, String reference) {
        Optional<String> fromReference = firstMatch(TWELVE_DIGIT_UTR, reference);
        if (fromReference.isPresent()) {
            return fromReference;
        }
        Optional<String> fromDescription = firstMatch(TWELVE_DIGIT_UTR, description);
        if (fromDescription.isPresent()) {
            return fromDescription;
        }
        if (reference == null || reference.isBlank() || gatewayRef(null, reference).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(reference.strip().toUpperCase(Locale.ROOT));
    }

    public static Optional<String> upiId(String description, String reference) {
        return firstMatch(UPI_ID, join(description, reference).toLowerCase(Locale.ROOT));
    }

    public static Optional<String> gatewayRef(String description, String reference) {
        return firstMatch(GATEWAY_REF, join(description, reference).toUpperCase(Locale.ROOT));
    }

    private static Optional<String> firstMatch(Pattern pattern, String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private static String join(String description, String reference) {
        return (description == null ? "" : description) + " " + (reference == null ? "" : reference);
    }
}
