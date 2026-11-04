package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.ScriptedAiModel;
import com.isocode.township.TestSessions;
import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.ai.AiVerdict;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.OutboxMessage;
import com.isocode.township.common.OutboxMessageRepository;
import com.isocode.township.flat.FlatRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplaintClassificationApiTest {

    private static final String GAS = "There is a strong gas leak smell near the stairs on the third floor.";
    private static final Duration AI_WAIT = Duration.ofSeconds(15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private ScriptedAiModel ai;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestSessions sessions;

    private Cookie swati;
    private Cookie anjali;
    private Long swatiFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swati = sessions.sessionFor(TestSessions.SWATI);
        anjali = sessions.sessionFor(TestSessions.ANJALI);
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
    }

    @AfterEach
    void finishBackgroundWorkAndRemoveComplaints() {
        ai.releaseAnswers();
        await().atMost(AI_WAIT).until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM complaint WHERE classification_status = 'PENDING'", Integer.class) == 0);
        ai.reset();
        jdbcTemplate.update("DELETE FROM complaint_event");
        jdbcTemplate.update("DELETE FROM escalation");
        jdbcTemplate.update("DELETE FROM complaint");
        jdbcTemplate.update("DELETE FROM outbox_message");
    }

    @AfterAll
    void removeEverythingThisClassCommitted() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename NOT IN ('flyway_schema_history', 'society_settings')
                """, String.class);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET session_replication_role = replica");
                statement.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
                statement.execute("SET session_replication_role = origin");
            }
            return null;
        });
    }

    @Test
    void theSafetyEscalationHappensBeforeTheAiAnswers() throws Exception {
        ai.holdAnswers();

        JsonNode raised = json(raise(GAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.safetyEscalated").value(true))
                .andExpect(jsonPath("$.escalation.dutyManager").value("Kiran Shinde"))
                .andExpect(jsonPath("$.escalation.reason").value(startsWith("Safety words: gas")))
                .andExpect(jsonPath("$.firstResponseDueAt").value(NOW.plus(Duration.ofMinutes(15)).toString())));

        Long kiran = userRepository.findByPhone("+919000000106").orElseThrow().getId();
        assertThat(outboxRepository.findByToUserIdOrderByIdDesc(kiran)).extracting(OutboxMessage::getBody)
                .anySatisfy(body -> assertThat(body).contains(raised.get("refNo").asText(), "T12-0408", "gas leak"));
        assertThat(ai.seenDescriptions()).isEmpty();

        ai.releaseAnswers();
        JsonNode done = awaitClassified(raised);
        assertThat(done.at("/urgency").asText()).isEqualTo("CRITICAL");
        assertThat(done.at("/escalation/reason").asText()).startsWith("Safety words: gas");
    }

    @Test
    void theAiAnswerAppliesWhenNoStaffMemberHasActedYet() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.LIFT, Urgency.HIGH, new BigDecimal("0.92"), "Lift grinding noise",
                "Mechanical noise from the lift."));

        JsonNode raised = json(raise("Something makes a loud grinding noise near our floor all evening.")
                .andExpect(jsonPath("$.category").value("NOISE")));
        JsonNode done = awaitClassified(raised);

        assertThat(done.at("/classificationStatus").asText()).isEqualTo("DONE");
        assertThat(done.at("/classifiedBy").asText()).isEqualTo("AI");
        assertThat(done.at("/category").asText()).isEqualTo("LIFT");
        assertThat(done.at("/urgency").asText()).isEqualTo("HIGH");
        assertThat(done.at("/aiConfidence").asText()).isEqualTo("0.92");
        assertThat(done.at("/aiSummary").asText()).isEqualTo("Lift grinding noise");
        assertThat(done.at("/needsReview").asBoolean()).isFalse();
        assertThat(done.at("/firstResponseDueAt").asText()).isEqualTo(NOW.plus(Duration.ofHours(2)).toString());
        assertThat(done.at("/assignee/name").asText()).isEqualTo("Prakash Jadhav");
        detail(done).andExpect(jsonPath("$.timeline[*].type",
                        contains("CREATED", "AUTO_ASSIGNED", "CLASSIFIED", "AUTO_ASSIGNED")))
                .andExpect(jsonPath("$.timeline[2].from").value("NOISE/MEDIUM"))
                .andExpect(jsonPath("$.timeline[2].to").value("LIFT/HIGH"))
                .andExpect(jsonPath("$.timeline[2].actorName").value("System"));
    }

    @Test
    void onceStaffRecategoriseTheAiAnswerIsOnlyRecordedAsASuggestion() throws Exception {
        ai.holdAnswers();
        ai.willAnswer(new AiVerdict(ComplaintCategory.LIFT, Urgency.HIGH, new BigDecimal("0.92"), "Lift noise", "Lift."));
        JsonNode raised = json(raise("Something makes a loud grinding noise near our floor all evening."));

        mockMvc.perform(patch("/api/desk/complaints/{id}/category", raised.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"CIVIL\",\"urgency\":\"LOW\"}")
                        .with(sameOriginCsrf()).cookie(anjali))
                .andExpect(status().isOk());
        ai.releaseAnswers();
        JsonNode done = awaitClassified(raised);

        assertThat(done.at("/classificationStatus").asText()).isEqualTo("DONE");
        assertThat(done.at("/category").asText()).isEqualTo("CIVIL");
        assertThat(done.at("/urgency").asText()).isEqualTo("LOW");
        assertThat(done.at("/assignee/name").asText()).isEqualTo("Ravi Patil");
        assertThat(done.at("/aiCategory").asText()).isEqualTo("LIFT");
        detail(done).andExpect(jsonPath("$.timeline[-1:].type", contains("AI_SUGGESTED")))
                .andExpect(jsonPath("$.timeline[-1:].from", contains("CIVIL/LOW")))
                .andExpect(jsonPath("$.timeline[-1:].to", contains("LIFT/HIGH")));
    }

    @Test
    void whenTheAiFailsTwiceTheKeywordResultStaysAndGoesToReview() throws Exception {
        ai.willFail(Cause.TIMEOUT);

        JsonNode done = awaitClassified(json(raise("Water is leaking from the bathroom ceiling.")));

        assertThat(done.at("/classificationStatus").asText()).isEqualTo("FAILED");
        assertThat(done.at("/classifiedBy").asText()).isEqualTo("RULES_FALLBACK");
        assertThat(done.at("/needsReview").asBoolean()).isTrue();
        assertThat(done.at("/category").asText()).isEqualTo("PLUMBING");
        assertThat(done.at("/assignee/name").asText()).isEqualTo("Ravi Patil");
        assertThat(done.at("/aiReason").asText()).startsWith("AI unavailable: the AI did not answer within the time limit.");
        assertThat(ai.seenDescriptions()).hasSize(2);
        mockMvc.perform(get("/api/desk/complaints").param("lane", "NEEDS_REVIEW").cookie(anjali))
                .andExpect(jsonPath("$[*].id", hasItem(done.get("id").asInt())));
    }

    @Test
    void anAiCriticalRatingEscalatesToTheDutyManagerWithoutSafetyWords() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.CRITICAL, new BigDecimal("0.90"), "Corridor flooding",
                "Water is flooding the corridor from a burst pipe."));

        JsonNode raised = json(raise("Water is pouring into the corridor from a burst pipe on our floor.")
                .andExpect(jsonPath("$.escalation").isEmpty()));
        JsonNode done = awaitClassified(raised);

        assertThat(done.at("/urgency").asText()).isEqualTo("CRITICAL");
        assertThat(done.at("/safetyEscalated").asBoolean()).isFalse();
        assertThat(done.at("/escalation/dutyManager").asText()).isEqualTo("Kiran Shinde");
        assertThat(done.at("/escalation/reason").asText())
                .isEqualTo("AI rated critical: Water is flooding the corridor from a burst pipe.");
        Long kiran = userRepository.findByPhone("+919000000106").orElseThrow().getId();
        assertThat(outboxRepository.findByToUserIdOrderByIdDesc(kiran)).extracting(OutboxMessage::getBody)
                .anySatisfy(body -> assertThat(body).contains(raised.get("refNo").asText(), "rated critical by the AI"));
        mockMvc.perform(get("/api/desk/complaints").param("lane", "SAFETY").cookie(anjali))
                .andExpect(jsonPath("$[0].refNo").value(raised.get("refNo").asText()))
                .andExpect(jsonPath("$[0].escalationOpen").value(true));
    }

    @Test
    void anAiCriticalRatingStillEscalatesAfterStaffActedButChangesNothingElse() throws Exception {
        ai.holdAnswers();
        ai.willAnswer(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.CRITICAL, new BigDecimal("0.90"), "Flooding",
                "Water is flooding the corridor."));
        JsonNode raised = json(raise("Water is pouring into the corridor from a burst pipe on our floor."));

        mockMvc.perform(patch("/api/desk/complaints/{id}/category", raised.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"PLUMBING\",\"urgency\":\"HIGH\"}")
                        .with(sameOriginCsrf()).cookie(anjali))
                .andExpect(status().isOk());
        ai.releaseAnswers();
        JsonNode done = awaitClassified(raised);

        assertThat(done.at("/urgency").asText()).isEqualTo("HIGH");
        assertThat(done.at("/escalation/reason").asText()).isEqualTo("AI rated critical: Water is flooding the corridor.");
    }

    @Test
    void theHelpDeskCanLowerAnAiCriticalRatingButTheEscalationStays() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.CRITICAL, new BigDecimal("0.90"), "Leak",
                "Water is flooding the corridor."));
        JsonNode done = awaitClassified(json(raise("Water is dripping in the corridor from a pipe joint.")));

        mockMvc.perform(patch("/api/desk/complaints/{id}/category", done.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"PLUMBING\",\"urgency\":\"MEDIUM\"}")
                        .with(sameOriginCsrf()).cookie(anjali))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgency").value("MEDIUM"))
                .andExpect(jsonPath("$.aiUrgency").value("CRITICAL"))
                .andExpect(jsonPath("$.escalation.reason").value("AI rated critical: Water is flooding the corridor."))
                .andExpect(jsonPath("$.actions.canAcknowledgeEscalation").value(true));
    }

    @Test
    void safetyWordsAndAnAiCriticalRatingMakeOneEscalationForTheSafetyWords() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.OTHER, Urgency.CRITICAL, new BigDecimal("0.95"), "Gas leak", "Gas leak."));

        JsonNode done = awaitClassified(json(raise(GAS)));

        assertThat(done.at("/escalation/reason").asText()).startsWith("Safety words: gas");
        detail(done).andExpect(jsonPath("$.timeline[?(@.type == 'ESCALATED')]", hasSize(1)));
    }

    @Test
    void aSafetyWordKeepsItCriticalEvenWhenTheAiSaysLow() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.OTHER, Urgency.LOW, new BigDecimal("0.95"), "Smell near stairs", "Minor"));

        JsonNode done = awaitClassified(json(raise(GAS)));

        assertThat(done.at("/urgency").asText()).isEqualTo("CRITICAL");
        assertThat(done.at("/aiUrgency").asText()).isEqualTo("LOW");
        assertThat(done.at("/firstResponseDueAt").asText()).isEqualTo(NOW.plus(Duration.ofMinutes(15)).toString());
    }

    @Test
    void lowAiConfidenceSendsItToTheReviewLane() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PARKING, Urgency.LOW, new BigDecimal("0.40"), "Parking issue", "Unclear"));

        JsonNode done = awaitClassified(json(raise("Something is wrong near the parking, please send someone")));

        assertThat(done.at("/needsReview").asBoolean()).isTrue();
        assertThat(done.at("/aiConfidence").asText()).isEqualTo("0.40");
        mockMvc.perform(get("/api/desk/complaints").param("lane", "NEEDS_REVIEW").cookie(anjali))
                .andExpect(jsonPath("$[*].id", hasItem(done.get("id").asInt())));
    }

    @Test
    void whenTheAiChangesTheCategoryTheComplaintIsLinkedToTheMatchingGroup() throws Exception {
        String neighbourPhone = jdbcTemplate.queryForObject("""
                SELECT u.phone FROM flat f JOIN tower t ON t.id = f.tower_id
                JOIN flat_member m ON m.flat_id = f.id AND m.active
                JOIN app_user u ON u.id = m.user_id AND u.phone IS NOT NULL AND u.role = 'RESIDENT'
                WHERE t.code = 'T12' AND f.code <> 'T12-0408' ORDER BY f.id, u.id LIMIT 1
                """, String.class);
        Long neighbourFlat = jdbcTemplate.queryForObject("""
                SELECT m.flat_id FROM flat_member m JOIN app_user u ON u.id = m.user_id WHERE u.phone = ? AND m.active
                ORDER BY m.flat_id LIMIT 1
                """, Long.class, neighbourPhone);
        ai.willAnswer(new AiVerdict(ComplaintCategory.LIFT, Urgency.HIGH, new BigDecimal("0.92"), "Lift down", "Lift."));
        JsonNode liftGroup = awaitClassified(json(mockMvc.perform(multipart("/api/flats/{id}/complaints", neighbourFlat)
                .param("description", "The lift is not working since morning.").with(sameOriginCsrf())
                .cookie(sessions.sessionFor(neighbourPhone)))));

        JsonNode raised = json(raise("Something makes a loud grinding noise near our floor all evening.")
                .andExpect(jsonPath("$.link").isEmpty()));
        JsonNode done = awaitClassified(raised);

        assertThat(done.at("/category").asText()).isEqualTo("LIFT");
        assertThat(done.at("/link/rootRefNo").asText()).isEqualTo(liftGroup.get("refNo").asText());
    }

    @Test
    void aHindiSafetyWordEscalatesWhileTheResidentWaits() throws Exception {
        ai.holdAnswers();

        raise("रसोई में गैस की बदबू आ रही है, जल्दी आइए")
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.escalation.dutyManager").value("Kiran Shinde"));
    }

    @Test
    void theAiSeesOnlyTheComplaintTextNeverWhoOrWhere() throws Exception {
        String description = "The lift on our floor makes a grinding noise.";

        awaitClassified(json(raise(description)));

        assertThat(ai.seenDescriptions()).containsExactly(description);
        assertThat(ai.seenDescriptions().getFirst()).doesNotContain("T12", "0408", "Swati", "+91");
    }

    private JsonNode awaitClassified(JsonNode raised) {
        long id = raised.get("id").asLong();
        await().atMost(AI_WAIT).until(() -> !"PENDING".equals(jdbcTemplate.queryForObject(
                "SELECT classification_status FROM complaint WHERE id = ?", String.class, id)));
        try {
            return json(mockMvc.perform(get("/api/desk/complaints/{id}", id).cookie(anjali)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private ResultActions detail(JsonNode complaint) throws Exception {
        return mockMvc.perform(get("/api/desk/complaints/{id}", complaint.get("id").asLong()).cookie(anjali));
    }

    private ResultActions raise(String description) throws Exception {
        return mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat).param("description", description)
                .with(sameOriginCsrf()).cookie(swati));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
