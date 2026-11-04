package com.isocode.township.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EscalationPolicyTest {

    @Test
    void safetyWordsEscalateWhateverTheAiSays() {
        assertThat(EscalationPolicy.reasonFor(List.of("gas", "gas leak"), ClassificationStatus.DONE, Urgency.LOW, "Minor"))
                .contains("Safety words: gas, gas leak");
        assertThat(EscalationPolicy.reasonFor(List.of("lift stuck"), ClassificationStatus.FAILED, Urgency.MEDIUM, null))
                .contains("Safety words: lift stuck");
    }

    @Test
    void anAiCriticalRatingEscalatesWithTheAiReason() {
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassificationStatus.DONE, Urgency.CRITICAL, "Water is flooding the corridor."))
                .contains("AI rated critical: Water is flooding the corridor.");
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassificationStatus.DONE, Urgency.CRITICAL, " "))
                .contains("AI rated critical: no reason given");
    }

    @Test
    void whenBothApplyTheSafetyRuleIsTheReason() {
        assertThat(EscalationPolicy.reasonFor(List.of("fire"), ClassificationStatus.DONE, Urgency.CRITICAL, "Fire in the meter room"))
                .contains("Safety words: fire");
    }

    @Test
    void lowerAiRatingsAndUnfinishedClassificationsDoNotEscalate() {
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassificationStatus.DONE, Urgency.HIGH, "Spreading leak")).isEmpty();
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassificationStatus.FAILED, Urgency.CRITICAL, "x")).isEmpty();
        assertThat(EscalationPolicy.reasonFor(List.of(), ClassificationStatus.PENDING, Urgency.CRITICAL, "x")).isEmpty();
    }

    @Test
    void aLongAiReasonIsCutToFitTheEscalation() {
        String reason = EscalationPolicy.reasonFor(List.of(), ClassificationStatus.DONE, Urgency.CRITICAL, "a".repeat(300)).orElseThrow();

        assertThat(reason).hasSize(EscalationPolicy.MAX_REASON_LENGTH).startsWith("AI rated critical: ").endsWith("…");
    }
}
