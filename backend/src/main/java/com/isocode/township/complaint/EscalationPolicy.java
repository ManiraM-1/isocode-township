package com.isocode.township.complaint;

import java.util.List;
import java.util.Optional;

public final class EscalationPolicy {

    static final int MAX_REASON_LENGTH = 300;

    private EscalationPolicy() {
    }

    public static Optional<String> reasonFor(List<String> safetyTerms, ClassificationStatus status, Urgency aiUrgency,
                                             String aiReason) {
        if (!safetyTerms.isEmpty()) {
            return Optional.of(fit("Safety words: " + String.join(", ", safetyTerms)));
        }
        if (status == ClassificationStatus.DONE && aiUrgency == Urgency.CRITICAL) {
            String why = aiReason == null || aiReason.isBlank() ? "no reason given" : aiReason.strip();
            return Optional.of(fit("AI rated critical: " + why));
        }
        return Optional.empty();
    }

    private static String fit(String reason) {
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }
}
