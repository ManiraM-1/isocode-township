package com.isocode.township.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ClassifiedBy;
import com.isocode.township.complaint.Complaint.Classification;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.yaml.snakeyaml.Yaml;

final class ComplaintEvalSet {

    static final Path FILE = Path.of("..", "samples", "complaints-eval.csv");

    private ComplaintEvalSet() {
    }

    record Labelled(String language, ComplaintCategory category, Urgency urgency, String text) {
    }

    record Outcome(Labelled expected, Classification actual) {

        boolean categoryRight() {
            return actual.category() == expected.category();
        }

        boolean urgencyRight() {
            return actual.urgency() == expected.urgency();
        }

        boolean missedCritical() {
            return expected.urgency() == Urgency.CRITICAL && actual.urgency() != Urgency.CRITICAL;
        }

        boolean falseAlarm() {
            return expected.urgency() != Urgency.CRITICAL && !actual.safetyTerms().isEmpty();
        }
    }

    record Report(String title, List<Outcome> outcomes) {

        double categoryAccuracy() {
            return share(outcomes.stream().filter(Outcome::categoryRight).count());
        }

        double urgencyAccuracy() {
            return share(outcomes.stream().filter(Outcome::urgencyRight).count());
        }

        long missedCriticals() {
            return outcomes.stream().filter(Outcome::missedCritical).count();
        }

        long falseAlarms() {
            return outcomes.stream().filter(Outcome::falseAlarm).count();
        }

        long fallbacks() {
            return outcomes.stream().filter(outcome -> outcome.actual().classifiedBy() == ClassifiedBy.RULES_FALLBACK).count();
        }

        String render() {
            StringBuilder text = new StringBuilder("%n== %s ==%n".formatted(title));
            text.append("complaints %d, category %.0f%%, urgency %.0f%%, missed critical %d, false alarms %d, fallbacks %d%n"
                    .formatted(outcomes.size(), categoryAccuracy() * 100, urgencyAccuracy() * 100, missedCriticals(),
                            falseAlarms(), fallbacks()));
            Map<String, int[]> byLanguage = new TreeMap<>();
            outcomes.forEach(outcome -> {
                int[] counts = byLanguage.computeIfAbsent(outcome.expected().language(), language -> new int[2]);
                counts[0]++;
                counts[1] += outcome.categoryRight() ? 1 : 0;
            });
            byLanguage.forEach((language, counts) ->
                    text.append("  %-9s category %d/%d%n".formatted(language, counts[1], counts[0])));
            outcomes.stream().filter(outcome -> !outcome.categoryRight() || !outcome.urgencyRight())
                    .forEach(outcome -> text.append("  miss: expected %s/%s, got %s/%s: %s%n".formatted(
                            outcome.expected().category(), outcome.expected().urgency(), outcome.actual().category(),
                            outcome.actual().urgency(), outcome.expected().text())));
            return text.toString();
        }

        private double share(long count) {
            return outcomes.isEmpty() ? 0 : (double) count / outcomes.size();
        }
    }

    static List<Labelled> load() throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            List<Labelled> rows = new ArrayList<>();
            for (CSVRecord row : format.parse(reader)) {
                rows.add(new Labelled(row.get("language"), ComplaintCategory.valueOf(row.get("category")),
                        Urgency.valueOf(row.get("urgency")), row.get("text")));
            }
            return rows;
        }
    }

    static ClassificationService serviceWith(AiComplaintModel model, Duration retryDelay) throws IOException {
        SocietySettings settings = mock(SocietySettings.class);
        when(settings.getAiConfidenceThreshold()).thenReturn(new BigDecimal("0.60"));
        SocietySettingsRepository settingsRepository = mock(SocietySettingsRepository.class);
        when(settingsRepository.current()).thenReturn(settings);
        return new ClassificationService(model, new SafetyProperties(configuredSafetyTerms()), settingsRepository,
                new GeminiProperties(null, null, null, null, retryDelay));
    }

    static Report evaluate(String title, ClassificationService service, Duration pauseBetweenCalls)
            throws IOException, InterruptedException {
        List<Outcome> outcomes = new ArrayList<>();
        for (Labelled row : load()) {
            outcomes.add(new Outcome(row, service.classify(row.text())));
            Thread.sleep(pauseBetweenCalls.toMillis());
        }
        return new Report(title, outcomes);
    }

    @SuppressWarnings("unchecked")
    private static List<String> configuredSafetyTerms() throws IOException {
        try (InputStream config = ComplaintEvalSet.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> yaml = new Yaml().load(config);
            Map<String, Object> app = (Map<String, Object>) yaml.get("app");
            Map<String, Object> safety = (Map<String, Object>) app.get("safety");
            return (List<String>) safety.get("terms");
        }
    }
}
