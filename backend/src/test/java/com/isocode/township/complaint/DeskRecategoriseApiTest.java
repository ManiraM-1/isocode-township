package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.ScriptedAiModel;
import com.isocode.township.TestSessions;
import com.isocode.township.ai.AiVerdict;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DeskRecategoriseApiTest {

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
    private TestSessions sessions;

    private Cookie anjali;
    private Cookie swati;
    private Long swatiFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        anjali = sessions.sessionFor(TestSessions.ANJALI);
        swati = sessions.sessionFor(TestSessions.SWATI);
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
    }

    @AfterEach
    void resetTheScriptedAi() {
        ai.reset();
    }

    @Test
    void recategoriseRecordsTheCorrectionAndHandsItToTheRightSpecialist() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.OTHER, Urgency.MEDIUM, new BigDecimal("0.40"), "Ceiling", "Unclear"));
        JsonNode raised = raise("Water is leaking from the bathroom ceiling.");
        long id = raised.get("id").asLong();

        recategorise(id, "{\"category\":\"PLUMBING\",\"urgency\":\"HIGH\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PLUMBING"))
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.aiCategory").value("OTHER"))
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.firstResponseDueAt").value(NOW.plus(Duration.ofHours(2)).toString()))
                .andExpect(jsonPath("$.resolveDueAt").value(NOW.plus(Duration.ofHours(24)).toString()))
                .andExpect(jsonPath("$.firstResponseAt").value(NOW.toString()))
                .andExpect(jsonPath("$.assignee.name").value("Ravi Patil"))
                .andExpect(jsonPath("$.timeline[2].type").value("CATEGORY_CHANGED"))
                .andExpect(jsonPath("$.timeline[2].from").value("OTHER/MEDIUM"))
                .andExpect(jsonPath("$.timeline[2].to").value("PLUMBING/HIGH"))
                .andExpect(jsonPath("$.timeline[2].actorName").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.timeline[3].type").value("AUTO_ASSIGNED"))
                .andExpect(jsonPath("$.timeline[3].from").value(raised.at("/assignee/name").asText()))
                .andExpect(jsonPath("$.timeline[3].to").value("Ravi Patil"));

        mockMvc.perform(get("/api/desk/complaints").param("lane", "NEEDS_REVIEW").cookie(anjali))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
    }

    @Test
    void anOwnerWithTheSkillKeepsTheComplaint() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PLUMBING, Urgency.MEDIUM, new BigDecimal("0.90"), "Seepage", "Water"));
        long id = raise("Damp patch spreading on the bedroom wall.").get("id").asLong();

        recategorise(id, "{\"category\":\"CIVIL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CIVIL"))
                .andExpect(jsonPath("$.urgency").value("MEDIUM"))
                .andExpect(jsonPath("$.assignee.name").value("Ravi Patil"))
                .andExpect(jsonPath("$.timeline[*].type", contains("CREATED", "AUTO_ASSIGNED", "CATEGORY_CHANGED")));
    }

    @Test
    void confirmingTheAiAnswerClearsReviewOnce() throws Exception {
        ai.willAnswer(new AiVerdict(ComplaintCategory.PARKING, Urgency.LOW, new BigDecimal("0.40"), "Parking", "Unclear"));
        long id = raise("Something is wrong near the parking, please send someone").get("id").asLong();

        recategorise(id, "{\"category\":\"PARKING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsReview").value(false))
                .andExpect(jsonPath("$.urgency").value("LOW"))
                .andExpect(jsonPath("$.timeline[-1:].from", contains("PARKING/LOW")))
                .andExpect(jsonPath("$.timeline[-1:].to", contains("PARKING/LOW")));
        recategorise(id, "{\"category\":\"PARKING\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The complaint already has this category and urgency."));
    }

    @Test
    void aSafetyComplaintCanBeRecategorisedButNeverLoweredBelowCritical() throws Exception {
        long id = raise(GAS).get("id").asLong();

        recategorise(id, "{\"category\":\"OTHER\",\"urgency\":\"LOW\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Safety words were found in this complaint, so it stays Critical."));
        recategorise(id, "{\"category\":\"CIVIL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CIVIL"))
                .andExpect(jsonPath("$.urgency").value("CRITICAL"));
    }

    @Test
    void onlyTheHelpDeskCanRecategoriseAndTheBodyIsChecked() throws Exception {
        long id = raise("The corridor light outside my door is flickering all night.").get("id").asLong();

        mockMvc.perform(patch("/api/desk/complaints/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"LIFT\"}").with(sameOriginCsrf()).cookie(swati))
                .andExpect(status().isForbidden());
        recategorise(id, "{}").andExpect(status().isBadRequest());
        recategorise(id, "{\"category\":\"FIRE_BRIGADE\"}").andExpect(status().isBadRequest());
        recategorise(999_999L, "{\"category\":\"LIFT\"}").andExpect(status().isNotFound());
    }

    @Test
    void acknowledgingAnEscalationIsRecordedOnceAndCountsAsTheFirstResponse() throws Exception {
        JsonNode raised = raise(GAS);
        long escalationId = raised.at("/escalation/id").asLong();

        acknowledge(escalationId, anjali)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalation.acknowledgedBy").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.escalation.acknowledgedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.firstResponseAt").value(NOW.toString()))
                .andExpect(jsonPath("$.actions.canAcknowledgeEscalation").value(false))
                .andExpect(jsonPath("$.timeline[-1:].type", contains("ESCALATION_ACKNOWLEDGED")))
                .andExpect(jsonPath("$.timeline[-1:].actorName", contains("Anjali Deshmukh")));

        mockMvc.perform(get("/api/desk/complaints").param("lane", "SAFETY").cookie(anjali))
                .andExpect(jsonPath("$[0].refNo").value(raised.get("refNo").asText()))
                .andExpect(jsonPath("$[0].escalationOpen").value(false));
        acknowledge(escalationId, anjali)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This escalation has already been acknowledged."));
    }

    @Test
    void theAcknowledgeActionIsOfferedOnlyWhileTheEscalationIsOpen() throws Exception {
        JsonNode raised = raise(GAS);

        mockMvc.perform(get("/api/desk/complaints/{id}", raised.get("id").asLong()).cookie(anjali))
                .andExpect(jsonPath("$.actions.canAcknowledgeEscalation").value(true))
                .andExpect(jsonPath("$.actions.canRecategorise").value(true));
        mockMvc.perform(get("/api/complaints/{id}", raised.get("id").asLong()).cookie(swati))
                .andExpect(jsonPath("$.actions.canAcknowledgeEscalation").value(false))
                .andExpect(jsonPath("$.actions.canRecategorise").value(false));
    }

    @Test
    void residentsCannotAcknowledgeAndUnknownEscalationsAreNotFound() throws Exception {
        long escalationId = raise(GAS).at("/escalation/id").asLong();

        acknowledge(escalationId, swati).andExpect(status().isForbidden());
        acknowledge(999_999L, anjali).andExpect(status().isNotFound());
    }

    private JsonNode raise(String description) throws Exception {
        String body = mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat)
                        .param("description", description).with(sameOriginCsrf()).cookie(swati))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions recategorise(long id, String json) throws Exception {
        return mockMvc.perform(patch("/api/desk/complaints/{id}/category", id).contentType(MediaType.APPLICATION_JSON)
                .content(json).with(sameOriginCsrf()).cookie(anjali));
    }

    private ResultActions acknowledge(long escalationId, Cookie who) throws Exception {
        return mockMvc.perform(post("/api/desk/escalations/{id}/acknowledge", escalationId)
                .with(sameOriginCsrf()).cookie(who));
    }
}
