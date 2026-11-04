package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class GeminiComplaintClassifierTest {

    private static final String KEY = "test-key-" + UUID.randomUUID();
    private static final String URL = "https://gemini.test/v1beta/models/gemini-3.5-flash-lite:generateContent";
    private static final String LEAK = "Water is leaking from the bathroom ceiling.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiComplaintClassifier classifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        classifier = new GeminiComplaintClassifier(builder.build(), properties(KEY, Duration.ofSeconds(6)), objectMapper);
    }

    @Test
    void sendsOnlyTheDescriptionAndAsksForJsonInTheAgreedShape() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(LEAK))
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.category.enum.length()").value(10))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required.length()").value(5))
                .andRespond(answer(Map.of("category", "PLUMBING", "urgency", "HIGH", "confidence", 0.93,
                        "summary", "Bathroom ceiling leak", "reason", "Water leak from above.")));

        AiVerdict verdict = classifier.classify(LEAK);

        assertThat(verdict).isEqualTo(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.HIGH, new BigDecimal("0.93"),
                "Bathroom ceiling leak", "Water leak from above."));
        server.verify();
    }

    @Test
    void theApiKeyNeverGoesInTheUrl() {
        server.expect(request -> assertThat(request.getURI().toString()).doesNotContain(KEY))
                .andRespond(answer(Map.of("category", "OTHER", "urgency", "LOW", "confidence", 0.7,
                        "summary", "x", "reason", "y")));

        classifier.classify(LEAK);
    }

    @Test
    void aCategoryOutsideTheListIsAnInvalidAnswer() {
        server.expect(requestTo(URL)).andRespond(answer(Map.of("category", "FIRE_BRIGADE", "urgency", "HIGH",
                "confidence", 0.9, "summary", "x", "reason", "y")));

        assertThatThrownBy(() -> classifier.classify(LEAK))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(Cause.INVALID_ANSWER));
    }

    @Test
    void anUrgencyOutsideTheListOrAConfidenceOutOfRangeIsAnInvalidAnswer() {
        server.expect(requestTo(URL)).andRespond(answer(Map.of("category", "LIFT", "urgency", "URGENT",
                "confidence", 0.9, "summary", "x", "reason", "y")));
        server.expect(requestTo(URL)).andRespond(answer(Map.of("category", "LIFT", "urgency", "HIGH",
                "confidence", 1.4, "summary", "x", "reason", "y")));

        assertThatThrownBy(() -> classifier.classify(LEAK)).isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> classifier.classify(LEAK)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void textThatIsNotJsonOrAMissingAnswerIsInvalid() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Sure! It is plumbing.\"}]}}]}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> classifier.classify(LEAK)).hasMessageStartingWith("INVALID_ANSWER");
        assertThatThrownBy(() -> classifier.classify(LEAK)).hasMessageStartingWith("INVALID_ANSWER");
    }

    @Test
    void serverErrorsAndRateLimitsAreErrors() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> classifier.classify(LEAK)).hasMessageStartingWith("ERROR");
        assertThatThrownBy(() -> classifier.classify(LEAK)).hasMessageStartingWith("ERROR");
    }

    @Test
    void aSocketTimeoutIsReportedAsATimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> classifier.classify(LEAK))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(Cause.TIMEOUT));
    }

    @Test
    void withoutAnApiKeyItDoesNotCallOutAtAll() {
        GeminiComplaintClassifier unconfigured = new GeminiComplaintClassifier(
                RestClient.builder().build(), properties("", Duration.ofSeconds(6)), objectMapper);

        assertThatThrownBy(() -> unconfigured.classify(LEAK))
                .isInstanceOfSatisfying(AiUnavailableException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(Cause.NOT_CONFIGURED));
    }

    @Test
    void longSummariesAreCutToTwelveWords() {
        assertThat(GeminiComplaintClassifier.shortSummary("one two three four five six seven eight nine ten eleven twelve thirteen"))
                .isEqualTo("one two three four five six seven eight nine ten eleven twelve");
    }

    @Test
    void aSlowServerIsCutOffAtTheConfiguredTimeout() throws IOException {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(3_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            GeminiProperties shortTimeout = new GeminiProperties(KEY, "gemini-3.5-flash-lite",
                    "http://127.0.0.1:" + slow.getAddress().getPort(), Duration.ofMillis(300));
            GeminiComplaintClassifier timed = new GeminiComplaintClassifier(RestClient.builder(), shortTimeout, objectMapper);
            long started = System.nanoTime();

            assertThatThrownBy(() -> timed.classify(LEAK))
                    .isInstanceOfSatisfying(AiUnavailableException.class,
                            failure -> assertThat(failure.reasonCode()).isEqualTo(Cause.TIMEOUT));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        } finally {
            slow.stop(0);
        }
    }

    private ResponseCreator answer(Map<String, Object> answer) {
        try {
            String inner = objectMapper.writeValueAsString(answer);
            String outer = objectMapper.writeValueAsString(Map.of("candidates",
                    List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", inner)))))));
            return withSuccess(outer, MediaType.APPLICATION_JSON);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static GeminiProperties properties(String key, Duration timeout) {
        return new GeminiProperties(key, "gemini-3.5-flash-lite", "https://gemini.test", timeout);
    }
}
