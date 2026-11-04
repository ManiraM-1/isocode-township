package com.isocode.township.ai;

import java.util.List;

public final class SafetyRule {

    private final TermMatcher matcher;

    public SafetyRule(List<String> terms) {
        this.matcher = TermMatcher.of(terms);
    }

    public List<String> check(String text) {
        return matcher.matchesIn(text);
    }
}
