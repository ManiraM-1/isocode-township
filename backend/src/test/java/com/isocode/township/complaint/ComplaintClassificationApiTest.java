package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class ComplaintClassificationApiTest {

    private static final String GAS = "There is a strong gas leak smell near the stairs on the third floor.";

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
    void resetTheScriptedAi() {
        ai.reset();
    }

    @Test
    void aSafetyWordEscalatesToTheDutyManagerEvenWhenTheAiSaysLow() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.OTHER, Urgency.LOW, new BigDecimal("0.95"), "Smell near stairs", "Minor"));

        JsonNode complaint = json(raise(GAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.aiUrgency").value("LOW"))
                .andExpect(jsonPath("$.safetyEscalated").value(true))
                .andExpect(jsonPath("$.safetyTerms", hasItem("gas leak")))
                .andExpect(jsonPath("$.escalation.dutyManager").value("Kiran Shinde"))
                .andExpect(jsonPath("$.escalation.acknowledgedAt").isEmpty())
                .andExpect(jsonPath("$.firstResponseDueAt").value(NOW.plus(Duration.ofMinutes(15)).toString()))
                .andExpect(jsonPath("$.timeline[*].type", hasItem("ESCALATED"))));

        Long kiran = userRepository.findByPhone("+919000000106").orElseThrow().getId();
        assertThat(outboxRepository.findByToUserIdOrderByIdDesc(kiran)).extracting(OutboxMessage::getBody)
                .anySatisfy(body -> assertThat(body).contains(complaint.get("refNo").asText(), "T12-0408", "gas leak"));
        mockMvc.perform(get("/api/desk/complaints").param("lane", "SAFETY").cookie(anjali))
                .andExpect(jsonPath("$[0].refNo").value(complaint.get("refNo").asText()))
                .andExpect(jsonPath("$[0].escalationOpen").value(true));
    }

    @Test
    void aHindiSafetyWordEscalatesToo() throws Exception {
        raise("रसोई में गैस की बदबू आ रही है, जल्दी आइए")
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.escalation.dutyManager").value("Kiran Shinde"));
    }

    @Test
    void anOrdinaryComplaintIsNotEscalated() throws Exception {
        raise("The corridor light outside my door is flickering all night.")
                .andExpect(jsonPath("$.safetyEscalated").value(false))
                .andExpect(jsonPath("$.escalation").isEmpty());
    }

    @Test
    void anAiCriticalRatingEscalatesToTheDutyManagerWithoutSafetyWords() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.CRITICAL, new BigDecimal("0.90"), "Corridor flooding",
                "Water is flooding the corridor from a burst pipe."));

        JsonNode complaint = json(raise("Water is pouring into the corridor from a burst pipe on our floor.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.safetyEscalated").value(false))
                .andExpect(jsonPath("$.safetyTerms").isEmpty())
                .andExpect(jsonPath("$.escalation.dutyManager").value("Kiran Shinde"))
                .andExpect(jsonPath("$.escalation.reason")
                        .value("AI rated critical: Water is flooding the corridor from a burst pipe."))
                .andExpect(jsonPath("$.timeline[*].type", hasItem("ESCALATED"))));

        Long kiran = userRepository.findByPhone("+919000000106").orElseThrow().getId();
        assertThat(outboxRepository.findByToUserIdOrderByIdDesc(kiran)).extracting(OutboxMessage::getBody)
                .anySatisfy(body -> assertThat(body).contains(complaint.get("refNo").asText(), "rated critical by the AI"));
        mockMvc.perform(get("/api/desk/complaints").param("lane", "SAFETY").cookie(anjali))
                .andExpect(jsonPath("$[0].refNo").value(complaint.get("refNo").asText()))
                .andExpect(jsonPath("$[0].escalationOpen").value(true));
    }

    @Test
    void safetyWordsAndAnAiCriticalRatingMakeOneEscalationForTheSafetyWords() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.OTHER, Urgency.CRITICAL, new BigDecimal("0.95"), "Gas leak", "Gas leak."));

        raise(GAS).andExpect(jsonPath("$.escalation.reason").value(startsWith("Safety words: gas")))
                .andExpect(jsonPath("$.timeline[?(@.type == 'ESCALATED')]", hasSize(1)));
    }

    @Test
    void anUnsureAiCriticalRatingStillEscalatesAndGoesToReview() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.ELECTRICAL, Urgency.CRITICAL, new BigDecimal("0.40"), "Meter room",
                "Possible electrical hazard."));

        raise("Something is wrong in the meter room, there is a buzzing noise.")
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.escalation.reason").value("AI rated critical: Possible electrical hazard."));
    }

    @Test
    void theKeywordFallbackNeverEscalatesWithoutSafetyWords() throws Exception {
        ai.willFail(Cause.ERROR);

        raise("Water is pouring into the corridor from a burst pipe on our floor.")
                .andExpect(jsonPath("$.classifiedBy").value("RULES_FALLBACK"))
                .andExpect(jsonPath("$.escalation").isEmpty());
    }

    @Test
    void lowAiConfidenceSendsItToTheReviewLane() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PARKING, Urgency.LOW, new BigDecimal("0.40"), "Parking issue", "Unclear"));

        String refNo = json(raise("Something is wrong near the parking, please send someone")
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.category").value("PARKING"))
                .andExpect(jsonPath("$.aiConfidence").value("0.40"))).get("refNo").asText();

        mockMvc.perform(get("/api/desk/complaints").param("lane", "NEEDS_REVIEW").cookie(anjali))
                .andExpect(jsonPath("$[*].refNo", hasItem(refNo)));
    }

    @Test
    void whenTheAiTimesOutKeywordRulesClassifyItAndItGoesToReview() throws Exception {
        ai.willFail(Cause.TIMEOUT);

        raise("Water is leaking from the bathroom ceiling.")
                .andExpect(jsonPath("$.classifiedBy").value("RULES_FALLBACK"))
                .andExpect(jsonPath("$.needsReview").value(true))
                .andExpect(jsonPath("$.category").value("PLUMBING"))
                .andExpect(jsonPath("$.assignee.name").value("Ravi Patil"))
                .andExpect(jsonPath("$.aiReason").value(startsWith(
                        "AI unavailable: the AI did not answer within the time limit.")));
    }

    @Test
    void theAiSeesOnlyTheComplaintTextNeverWhoOrWhere() throws Exception {
        String description = "The lift on our floor makes a grinding noise.";

        raise(description);

        assertThat(ai.seenDescriptions()).containsExactly(description);
        assertThat(ai.seenDescriptions().getFirst()).doesNotContain("T12", "0408", "Swati", "+91");
    }

    private ResultActions raise(String description) throws Exception {
        return mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat).param("description", description)
                .with(sameOriginCsrf()).cookie(swati));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
