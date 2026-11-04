package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.ai.ComplaintEvalSet.Report;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KeywordBaselineEvalTest {

    @Test
    void withTheAiDownKeywordRulesAndTheSafetyRuleStillCatchEveryEmergency() throws Exception {
        ClassificationService rulesOnly = ComplaintEvalSet.serviceWith(description -> {
            throw new AiUnavailableException(Cause.NOT_CONFIGURED, "offline baseline");
        }, Duration.ZERO);

        Report report = ComplaintEvalSet.evaluate("Keyword fallback baseline (no AI)", rulesOnly, Duration.ZERO);
        System.out.println(report.render());

        assertThat(report.outcomes()).hasSize(35);
        assertThat(report.missedCriticals()).isZero();
        assertThat(report.falseAlarms()).isZero();
        assertThat(report.fallbacks()).isEqualTo(35);
        assertThat(report.categoryAccuracy()).isGreaterThanOrEqualTo(0.85);
    }
}
