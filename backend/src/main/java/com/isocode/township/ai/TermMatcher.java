package com.isocode.township.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TermMatcher {

    private static final String STEM_MARK = "*";
    private static final String NOT_A_WORD_CHARACTER_BEFORE = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String NOT_A_WORD_CHARACTER_AFTER = "(?![\\p{L}\\p{M}\\p{N}])";
    private static final String ANY_WORD_ENDING = "[\\p{L}\\p{M}]*";

    private final List<Term> terms;

    private TermMatcher(List<Term> terms) {
        this.terms = terms;
    }

    public static TermMatcher of(List<String> rawTerms) {
        List<Term> compiled = new ArrayList<>();
        for (String raw : rawTerms) {
            String term = normalise(raw.strip());
            if (!term.isEmpty()) {
                compiled.add(new Term(term.replace(STEM_MARK, ""), compile(term)));
            }
        }
        return new TermMatcher(List.copyOf(compiled));
    }

    public List<String> matchesIn(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalised = normalise(text);
        Set<String> found = new LinkedHashSet<>();
        for (Term term : terms) {
            if (term.pattern().matcher(normalised).find()) {
                found.add(term.label());
            }
        }
        return List.copyOf(found);
    }

    private static Pattern compile(String term) {
        String body = Arrays.stream(term.split("\\s+"))
                .map(word -> word.endsWith(STEM_MARK)
                        ? Pattern.quote(word.substring(0, word.length() - 1)) + ANY_WORD_ENDING
                        : Pattern.quote(word))
                .collect(Collectors.joining("\\s+"));
        return Pattern.compile(NOT_A_WORD_CHARACTER_BEFORE + body + NOT_A_WORD_CHARACTER_AFTER,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String normalise(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    private record Term(String label, Pattern pattern) {
    }
}
