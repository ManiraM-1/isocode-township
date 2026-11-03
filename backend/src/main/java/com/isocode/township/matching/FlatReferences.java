package com.isocode.township.matching;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FlatReferences {

    private static final List<Pattern> FLAT_CODE_FORMS = List.of(
            Pattern.compile("(?<![A-Z0-9])T\\s?(\\d{1,2})\\s*[-/ ]\\s*(\\d{4})(?!\\d)"),
            Pattern.compile("(?<![A-Z0-9])T(\\d{2})(\\d{4})(?!\\d)"),
            Pattern.compile("(?<![A-Z0-9])TOWER\\s*(\\d{1,2})\\s*[,/-]?\\s*FLAT\\s*(?:NO\\.?\\s*)?(\\d{4})(?!\\d)"));

    private FlatReferences() {
    }

    public static Set<String> flatCodesIn(String description, String reference) {
        String text = ((description == null ? "" : description) + " " + (reference == null ? "" : reference))
                .toUpperCase(Locale.ROOT);
        Set<String> codes = new LinkedHashSet<>();
        for (Pattern form : FLAT_CODE_FORMS) {
            Matcher matcher = form.matcher(text);
            while (matcher.find()) {
                codes.add(canonical(matcher.group(1), matcher.group(2)));
            }
        }
        return codes;
    }

    public static ReferenceSignal resolve(String description, String reference, Set<String> knownFlatCodes) {
        List<String> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String code : flatCodesIn(description, reference)) {
            (knownFlatCodes.contains(code) ? known : unknown).add(code);
        }
        return new ReferenceSignal(List.copyOf(known), List.copyOf(unknown));
    }

    private static String canonical(String tower, String flat) {
        return "T%02d-%s".formatted(Integer.parseInt(tower), flat);
    }

    public record ReferenceSignal(List<String> knownCodes, List<String> unknownCodes) {

        public boolean isAmbiguous() {
            return knownCodes.size() > 1;
        }

        public boolean namesOneFlat() {
            return knownCodes.size() == 1;
        }
    }
}
