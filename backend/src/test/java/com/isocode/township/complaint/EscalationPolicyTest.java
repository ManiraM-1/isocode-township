package com.isocode.township.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EscalationPolicyTest {

    @Test
    void safetyWordsEscalateWhateverTheAiSays() {
        assertThat(EscalationPolicy.reasonFor(List.of("gas", "gas leak"), ClassifiedBy.AI, Urgency.LOW, "Minor"))
                .contains("Safety words: gas, gas leak");
        assertThat(EscalationPolicy.reasonFor(List.of("lift stuck"), ClassifiedBy.RULES_FALLBACK, Urgency.MEDIUM, null))
                .contains("Safety words: lift stuck");
    }

    @Test
    void anAiCriticalRatingEscalatesWithTheAiReason() {
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassifiedBy.AI, Urgency.CRITICAL, "Water is flooding the corridor."))
                .contains("AI rated critical: Water is flooding the corridor.");
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassifiedBy.AI, Urgency.CRITICAL, " "))
                .contains("AI rated critical: no reason given");
    }

    @Test
    void whenBothApplyTheSafetyRuleIsTheReason() {
        assertThat(EscalationPolicy.reasonFor(List.of("fire"), ClassifiedBy.AI, Urgency.CRITICAL, "Fire in the meter room"))
                .contains("Safety words: fire");
    }

    @Test
    void lowerAiRatingsAndKeywordFallbackDoNotEscalate() {
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassifiedBy.AI, Urgency.HIGH, "Spreading leak")).isEmpty();
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassifiedBy.RULES_FALLBACK, Urgency.CRITICAL, "x")).isEmpty();
    }

    @Test
    void aLongAiReasonIsCutToFitTheEscalation() {
        String reason = EscalationPolicy.reasonFor(List.of(), ClassifiedBy.AI, Urgency.CRITICAL, "a".repeat(300)).orElseThrow();

        assertThat(reason).hasSize(EscalationPolicy.MAX_REASON_LENGTH).startsWith("AI rated critical: ").endsWith("…");
    }
}
