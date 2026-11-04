package com.isocode.township.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeminiComplaintClassifier implements AiComplaintModel {

    static final int MAX_SUMMARY_WORDS = 12;
    private static final int MAX_SUMMARY_CHARS = 200;
    private static final int MAX_REASON_CHARS = 300;

    static final String INSTRUCTIONS = """
            You sort maintenance complaints written by residents of a gated township in Pune, India.
            Complaints may be in English, Hindi, Marathi or a mix written in Latin letters.
            Choose exactly one category:
            PLUMBING (water supply, leaks, taps, drains, toilets, tanks), ELECTRICAL (power, lights, sockets, wiring),
            LIFT (lifts and their doors), SECURITY (guards, gates, strangers, CCTV, theft),
            HOUSEKEEPING (garbage, cleaning of common areas), CIVIL (walls, cracks, seepage, doors, windows, tiles),
            PEST_CONTROL (cockroaches, rats, mosquitoes, termites), PARKING (parking slots, vehicles),
            NOISE (loud music, parties, construction noise), OTHER (anything else).
            Choose urgency: CRITICAL when there is danger to people or property right now (fire, gas, smoke, sparks,
            someone trapped, electric shock); HIGH when an essential service is out or damage is spreading;
            MEDIUM for ordinary faults; LOW for minor issues and requests.
            confidence is your probability from 0 to 1 that the category is right.
            summary is at most twelve words in English. reason is one short English sentence.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "category", Map.of("type", "STRING", "enum", names(ComplaintCategory.values())),
                    "urgency", Map.of("type", "STRING", "enum", names(Urgency.values())),
                    "confidence", Map.of("type", "NUMBER"),
                    "summary", Map.of("type", "STRING"),
                    "reason", Map.of("type", "STRING")),
            "required", List.of("category", "urgency", "confidence", "summary", "reason"));

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiComplaintClassifier(RestClient.Builder builder, GeminiProperties properties, ObjectMapper objectMapper) {
        this(builder.baseUrl(properties.baseUrl()).requestFactory(timedRequestFactory(properties)).build(),
                properties, objectMapper);
    }

    GeminiComplaintClassifier(RestClient restClient, GeminiProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiVerdict classify(String description) {
        if (!properties.isConfigured()) {
            throw new AiUnavailableException(Cause.NOT_CONFIGURED, "GEMINI_API_KEY is not set");
        }
        String response;
        try {
            response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestFor(description))
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException failure) {
            throw new AiUnavailableException(isTimeout(failure) ? Cause.TIMEOUT : Cause.ERROR, failure.getClass().getSimpleName());
        } catch (RestClientException failure) {
            throw new AiUnavailableException(Cause.ERROR, failure.getClass().getSimpleName());
        }
        return verdictFrom(response);
    }

    Map<String, Object> requestFor(String description) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", INSTRUCTIONS))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", description)))),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA));
    }

    private AiVerdict verdictFrom(String response) {
        try {
            JsonNode text = objectMapper.readTree(response == null ? "" : response)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual()) {
                throw invalid("no answer text");
            }
            JsonNode answer = objectMapper.readTree(text.asText());
            ComplaintCategory category = enumValue(ComplaintCategory.class, answer.path("category"));
            Urgency urgency = enumValue(Urgency.class, answer.path("urgency"));
            JsonNode confidenceNode = answer.path("confidence");
            if (!confidenceNode.isNumber()) {
                throw invalid("confidence is not a number");
            }
            BigDecimal confidence = confidenceNode.decimalValue();
            if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw invalid("confidence out of range");
            }
            return new AiVerdict(category, urgency, confidence.setScale(2, RoundingMode.HALF_UP),
                    shortSummary(answer.path("summary").asText("")),
                    truncate(answer.path("reason").asText("").strip(), MAX_REASON_CHARS));
        } catch (JsonProcessingException unreadable) {
            throw invalid("answer is not JSON");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
        if (!node.isTextual()) {
            throw invalid(type.getSimpleName() + " missing");
        }
        try {
            return Enum.valueOf(type, node.asText().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid(type.getSimpleName() + " not allowed");
        }
    }

    static String shortSummary(String summary) {
        String[] words = summary.strip().split("\\s+");
        String joined = String.join(" ", Arrays.copyOf(words, Math.min(words.length, MAX_SUMMARY_WORDS)));
        return truncate(joined, MAX_SUMMARY_CHARS);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static AiUnavailableException invalid(String detail) {
        return new AiUnavailableException(Cause.INVALID_ANSWER, detail);
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static JdkClientHttpRequestFactory timedRequestFactory(GeminiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
