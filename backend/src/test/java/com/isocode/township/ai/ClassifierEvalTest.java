package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.ai.ComplaintEvalSet.Report;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

@Tag("ai-eval")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class ClassifierEvalTest {

    private static final Duration PAUSE_FOR_FREE_TIER_RATE_LIMIT = Duration.ofSeconds(5);

    @Test
    void geminiClassifiesTheLabelledComplaints() throws Exception {
        String model = Optional.ofNullable(System.getenv("GEMINI_MODEL")).filter(name -> !name.isBlank())
                .orElse("gemini-3.5-flash-lite");
        GeminiProperties properties = new GeminiProperties(System.getenv("GEMINI_API_KEY"), model,
                "https://generativelanguage.googleapis.com", Duration.ofSeconds(6));
        ClassificationService service = ComplaintEvalSet.serviceWith(
                new GeminiComplaintClassifier(RestClient.builder(), properties, new ObjectMapper()));

        Report report = ComplaintEvalSet.evaluate("Gemini " + model, service, PAUSE_FOR_FREE_TIER_RATE_LIMIT);
        System.out.println(report.render());

        assertThat(report.missedCriticals()).isZero();
    }
}
