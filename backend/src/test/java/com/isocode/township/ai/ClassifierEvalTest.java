package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.ai.ComplaintEvalSet.Report;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@Tag("ai-eval")
class ClassifierEvalTest {

    private static final Duration PAUSE_FOR_FREE_TIER_RATE_LIMIT = Duration.ofSeconds(5);
    private static final List<Path> DOT_ENV_LOCATIONS = List.of(Path.of("..", ".env"), Path.of(".env"));

    @Test
    void geminiClassifiesTheLabelledComplaints() throws Exception {
        Map<String, String> dotEnv = readDotEnv();
        String key = setting("GEMINI_API_KEY", dotEnv).orElse("");
        assumeTrue(!key.isBlank() && !key.startsWith("your-"),
                "GEMINI_API_KEY is not set in the environment or in .env");
        String model = setting("GEMINI_MODEL", dotEnv).orElse("gemini-3.5-flash-lite");
        GeminiProperties properties = new GeminiProperties(key, model, "https://generativelanguage.googleapis.com",
                Duration.ofSeconds(6));
        ClassificationService service = ComplaintEvalSet.serviceWith(
                new GeminiComplaintClassifier(RestClient.builder(), properties, new ObjectMapper()));

        Report report = ComplaintEvalSet.evaluate("Gemini " + model, service, PAUSE_FOR_FREE_TIER_RATE_LIMIT);
        System.out.println(report.render());

        assertThat(report.fallbacks()).as("complaints Gemini did not answer").isLessThan(report.outcomes().size());
        assertThat(report.missedCriticals()).isZero();
        assertThat(report.falseAlarms()).isZero();
    }

    private static Optional<String> setting(String name, Map<String, String> dotEnv) {
        return Optional.ofNullable(System.getenv(name)).filter(value -> !value.isBlank())
                .or(() -> Optional.ofNullable(dotEnv.get(name)).filter(value -> !value.isBlank()));
    }

    private static Map<String, String> readDotEnv() throws IOException {
        Map<String, String> values = new HashMap<>();
        for (Path location : DOT_ENV_LOCATIONS) {
            if (Files.isRegularFile(location)) {
                for (String line : Files.readAllLines(location, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    int equals = trimmed.indexOf('=');
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || equals < 1) {
                        continue;
                    }
                    values.putIfAbsent(trimmed.substring(0, equals).strip(), unquoted(trimmed.substring(equals + 1).strip()));
                }
                return values;
            }
        }
        return values;
    }

    private static String unquoted(String value) {
        boolean quoted = value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}
