package com.isocode.township.complaint;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DuplicateLinkingApiTest {

    private static final String LIFT_DOWN = "The lift is not working since morning, we are using the stairs.";
    private static final String LIFT_STOPPED = "Lift stopped at the ground floor and the doors will not open.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestSessions sessions;

    private Resident swati;
    private Resident neighbour;
    private Resident thirdNeighbour;
    private Resident neeraj;
    private Cookie anjali;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swati = resident(TestSessions.SWATI, "T12-0408");
        neeraj = resident(TestSessions.NEERAJ, "T07-1203");
        List<Map<String, Object>> others = jdbcTemplate.queryForList("""
                SELECT DISTINCT ON (f.id) f.code, u.phone FROM flat f
                JOIN tower t ON t.id = f.tower_id
                JOIN flat_member m ON m.flat_id = f.id AND m.active
                JOIN app_user u ON u.id = m.user_id AND u.phone IS NOT NULL AND u.role = 'RESIDENT'
                WHERE t.code = 'T12' AND f.code <> 'T12-0408'
                ORDER BY f.id, u.id
                LIMIT 2
                """);
        neighbour = resident((String) others.get(0).get("phone"), (String) others.get(0).get("code"));
        thirdNeighbour = resident((String) others.get(1).get("phone"), (String) others.get(1).get("code"));
        anjali = sessions.sessionFor(TestSessions.ANJALI);
    }

    @Test
    void liftComplaintsFromTwoFlatsInOneTowerAreLinkedAndEachKeepsItsOwnStatus() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        JsonNode second = raise(neighbour, LIFT_STOPPED);

        assertThat(second.at("/link/rootRefNo").asText()).isEqualTo(first.get("refNo").asText());
        assertThat(second.at("/link/flatsReporting").asInt()).isEqualTo(2);
        assertThat(second.at("/link/root").asBoolean()).isFalse();
        assertThat(second.at("/link/others").size()).isZero();
        assertThat(second.get("status").asText()).isEqualTo("ASSIGNED");

        mockMvc.perform(get("/api/complaints/{id}", first.get("id").asLong()).cookie(swati.session()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.link.root").value(true))
                .andExpect(jsonPath("$.link.flatsReporting").value(2))
                .andExpect(jsonPath("$.timeline[-1:].type", contains("LINKED")))
                .andExpect(jsonPath("$.timeline[-1:].to", contains(second.get("refNo").asText())));
        mockMvc.perform(get("/api/desk/complaints/{id}", first.get("id").asLong()).cookie(anjali))
                .andExpect(jsonPath("$.link.others[0].refNo").value(second.get("refNo").asText()))
                .andExpect(jsonPath("$.link.others[0].flatCode").value(neighbour.flatCode()))
                .andExpect(jsonPath("$.actions.canApplyToLinked").value(true))
                .andExpect(jsonPath("$.actions.canUnlink").value(false));
        mockMvc.perform(get("/api/desk/complaints").param("category", "LIFT").cookie(anjali))
                .andExpect(jsonPath("$[?(@.id == %d)].linkedCount".formatted(first.get("id").asLong()), contains(1)))
                .andExpect(jsonPath("$[?(@.id == %d)].linkedToRefNo".formatted(second.get("id").asLong()),
                        contains(first.get("refNo").asText())));
    }

    @Test
    void aThirdFlatLinksToTheFirstComplaintNotToTheSecond() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        raise(neighbour, LIFT_STOPPED);

        JsonNode third = raise(thirdNeighbour, "Lift is stuck on the 9th floor with its doors closed.");

        assertThat(third.at("/link/rootRefNo").asText()).isEqualTo(first.get("refNo").asText());
        assertThat(third.at("/link/flatsReporting").asInt()).isEqualTo(3);
    }

    @Test
    void aLiftComplaintInAnotherTowerIsNotLinked() throws Exception {
        raise(swati, LIFT_DOWN);

        assertThat(raise(neeraj, LIFT_STOPPED).get("link").isNull()).isTrue();
    }

    @Test
    void similarPlumbingTextIsLinkedButDissimilarPlumbingTextIsNot() throws Exception {
        JsonNode outage = raise(swati, "No water supply in our flat since morning.");

        assertThat(raise(thirdNeighbour, "Kitchen tap is dripping all night.").get("link").isNull()).isTrue();
        assertThat(raise(neighbour, "No water supply since morning in the flat.").at("/link/rootRefNo").asText())
                .isEqualTo(outage.get("refNo").asText());
    }

    @Test
    void aClosedComplaintIsNeverAParent() throws Exception {
        JsonNode closed = raise(swati, LIFT_DOWN);
        entityManager.flush();
        jdbcTemplate.update("""
                UPDATE complaint SET status = 'CLOSED', rating = 4, resolved_at = created_at, closed_at = created_at
                WHERE id = ?""", closed.get("id").asLong());
        entityManager.clear();

        assertThat(raise(neighbour, LIFT_STOPPED).get("link").isNull()).isTrue();
    }

    @Test
    void theHelpDeskCanUnlinkAndBothSidesRecordIt() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        JsonNode second = raise(neighbour, LIFT_STOPPED);

        mockMvc.perform(post("/api/desk/complaints/{id}/unlink", second.get("id").asLong())
                        .with(sameOriginCsrf()).cookie(swati.session()))
                .andExpect(status().isForbidden());
        unlink(second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").isEmpty())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.timeline[-1:].type", contains("UNLINKED")))
                .andExpect(jsonPath("$.timeline[-1:].actorName", contains("Anjali Deshmukh")));
        mockMvc.perform(get("/api/desk/complaints/{id}", first.get("id").asLong()).cookie(anjali))
                .andExpect(jsonPath("$.link").isEmpty())
                .andExpect(jsonPath("$.timeline[-1:].type", contains("UNLINKED")));
        unlink(second).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This complaint is not linked to another complaint."));
    }

    @Test
    void aStatusChangeReachesLinkedComplaintsOnlyWhenTheHelpDeskAsks() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        JsonNode second = raise(neighbour, LIFT_STOPPED);

        changeStatus(first, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        detail(second).andExpect(jsonPath("$.status").value("ASSIGNED"));

        changeStatus(first, "{\"status\":\"RESOLVED\",\"note\":\"Lift motor replaced\",\"applyToLinked\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.linkedUpdate.applied", contains(second.get("refNo").asText())))
                .andExpect(jsonPath("$.linkedUpdate.skipped").isEmpty());
        detail(second)
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty())
                .andExpect(jsonPath("$.timeline[*].note",
                        hasItem("Applied from " + first.get("refNo").asText() + ": Lift motor replaced")));
        changeStatus(second, "{\"status\":\"RESOLVED\",\"applyToLinked\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This complaint has no linked complaints."));
    }

    @Test
    void theHelpDeskIsToldWhichLinkedComplaintsCouldNotMakeTheMove() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        JsonNode second = raise(neighbour, LIFT_STOPPED);
        changeStatus(second, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());

        changeStatus(first, "{\"status\":\"IN_PROGRESS\"}")
                .andExpect(jsonPath("$.linkedUpdate").isEmpty());
        changeStatus(first, "{\"status\":\"RESOLVED\",\"applyToLinked\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedUpdate.applied").isEmpty())
                .andExpect(jsonPath("$.linkedUpdate.skipped[0].refNo").value(second.get("refNo").asText()))
                .andExpect(jsonPath("$.linkedUpdate.skipped[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.linkedUpdate.skipped[0].reason").value("Already resolved."));
    }

    @Test
    void aLinkedComplaintThatCannotGoBackIsSkippedWithTheReason() throws Exception {
        JsonNode first = raise(swati, LIFT_DOWN);
        JsonNode second = raise(neighbour, LIFT_STOPPED);
        changeStatus(second, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());

        changeStatus(first, "{\"status\":\"IN_PROGRESS\",\"applyToLinked\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedUpdate.skipped[0].reason")
                        .value("Cannot move from resolved to in progress."));
        detail(second).andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    private JsonNode raise(Resident resident, String description) throws Exception {
        String body = mockMvc.perform(multipart("/api/flats/{id}/complaints", resident.flatId())
                        .param("description", description).with(sameOriginCsrf()).cookie(resident.session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions unlink(JsonNode complaint) throws Exception {
        return mockMvc.perform(post("/api/desk/complaints/{id}/unlink", complaint.get("id").asLong())
                .with(sameOriginCsrf()).cookie(anjali));
    }

    private ResultActions changeStatus(JsonNode complaint, String json) throws Exception {
        return mockMvc.perform(patch("/api/desk/complaints/{id}/status", complaint.get("id").asLong())
                .contentType(MediaType.APPLICATION_JSON).content(json).with(sameOriginCsrf()).cookie(anjali));
    }

    private ResultActions detail(JsonNode complaint) throws Exception {
        return mockMvc.perform(get("/api/desk/complaints/{id}", complaint.get("id").asLong()).cookie(anjali));
    }

    private Resident resident(String phone, String flatCode) {
        return new Resident(sessions.sessionFor(phone), flatRepository.findByCode(flatCode).orElseThrow().getId(),
                flatCode);
    }

    private record Resident(Cookie session, Long flatId, String flatCode) {
    }
}
