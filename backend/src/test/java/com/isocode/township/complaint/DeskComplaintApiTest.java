package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
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
class DeskComplaintApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void staffWorkThroughTheStatusFlowAndTheFirstActionIsTheFirstResponse() throws Exception {
        long id = raise("Water is leaking from the bathroom ceiling.");

        changeStatus(id, "IN_PROGRESS", "Plumber on the way")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.firstResponseAt").value(NOW.toString()))
                .andExpect(jsonPath("$.actions.statusChanges", contains("RESOLVED")));
        changeStatus(id, "RESOLVED", "Replaced the pipe joint")
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.timeline[*].type",
                        contains("CREATED", "AUTO_ASSIGNED", "STATUS_CHANGED", "STATUS_CHANGED")))
                .andExpect(jsonPath("$.timeline[2].note").value("Plumber on the way"))
                .andExpect(jsonPath("$.timeline[2].actorName").value("Anjali Deshmukh"));
    }

    @Test
    void illegalMovesAreConflictsWithAPlainMessage() throws Exception {
        long id = raise("Water is leaking from the bathroom ceiling.");
        changeStatus(id, "RESOLVED", null);

        changeStatus(id, "IN_PROGRESS", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The help desk cannot move a complaint from resolved to in progress."));
        changeStatus(id, "CLOSED", null).andExpect(status().isConflict());
        changeStatus(id, "BOGUS", null).andExpect(status().isBadRequest());
    }

    @Test
    void aCommentAlsoCountsAsTheFirstResponse() throws Exception {
        long id = raise("The corridor light outside my door is flickering all night.");

        comment(id, "We will check it this evening")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.firstResponseAt").value(NOW.toString()))
                .andExpect(jsonPath("$.timeline[-1:].note", contains("We will check it this evening")));
        comment(id, "").andExpect(status().isBadRequest());
    }

    @Test
    void reassigningRecordsWhoFromAndTo() throws Exception {
        long id = raise("The notice board near the clubhouse needs updating.");
        Long ravi = userRepository.findByPhone("+919000000102").orElseThrow().getId();

        reassign(id, ravi, "Plumbing job")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.name").value("Ravi Patil"))
                .andExpect(jsonPath("$.timeline[-1:].type", contains("ASSIGNEE_CHANGED")))
                .andExpect(jsonPath("$.timeline[-1:].from", contains("Anjali Deshmukh")))
                .andExpect(jsonPath("$.timeline[-1:].to", contains("Ravi Patil")));
        reassign(id, ravi, null).andExpect(status().isConflict());
        Long resident = userRepository.findByPhone(TestSessions.SWATI).orElseThrow().getId();
        reassign(id, resident, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose an active help-desk staff member."));
    }

    @Test
    void movingAnUnassignedComplaintAssignsItToTheStaffMemberWhoActs() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET active = FALSE WHERE role = 'HELPDESK'");
        long id = raise("Stray dogs near the gate at night are scaring children.");
        jdbcTemplate.update("UPDATE app_user SET active = TRUE WHERE role = 'HELPDESK'");

        mockMvc.perform(get("/api/desk/complaints/{id}", id).cookie(anjali))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee").isEmpty());
        changeStatus(id, "IN_PROGRESS", null)
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignee.name").value("Anjali Deshmukh"));
    }

    @Test
    void theOpenLaneShowsOverdueComplaintsFirst() throws Exception {
        long onTime = raise("The corridor light outside my door is flickering all night.");
        long overdue = raise("Water is leaking from the bathroom ceiling.");
        entityManager.flush();
        jdbcTemplate.update("UPDATE complaint SET first_response_due_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(3600)), overdue);
        entityManager.clear();

        JsonNode lane = json(mockMvc.perform(get("/api/desk/complaints").param("lane", "OPEN").cookie(anjali)));

        assertThat(ids(lane)).containsExactly(overdue, onTime);
        assertThat(lane.get(0).get("overdue").asBoolean()).isTrue();
        assertThat(lane.get(1).get("overdue").asBoolean()).isFalse();
    }

    @Test
    void lanesAndFiltersNarrowTheBoard() throws Exception {
        long resolved = raise("Water is leaking from the bathroom ceiling.");
        changeStatus(resolved, "RESOLVED", null);
        long open = raise("The corridor light outside my door is flickering all night.");

        assertThat(ids(board(Map.of("lane", "AWAITING_RESIDENT")))).containsExactly(resolved);
        assertThat(ids(board(Map.of("lane", "OPEN")))).containsExactly(open);
        assertThat(ids(board(Map.of("tower", "t12")))).containsExactlyInAnyOrder(resolved, open);
        assertThat(ids(board(Map.of("tower", "T07")))).isEmpty();
        assertThat(ids(board(Map.of("category", "PLUMBING", "status", "RESOLVED")))).containsExactly(resolved);
        assertThat(ids(board(Map.of("lane", "SAFETY")))).isEmpty();
        assertThat(ids(board(Map.of("lane", "OPEN", "status", "RESOLVED")))).isEmpty();
    }

    @Test
    void residentsCannotUseAnyDeskComplaintEndpoint() throws Exception {
        long id = raise("Water is leaking from the bathroom ceiling.");

        mockMvc.perform(get("/api/desk/complaints").cookie(swati)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/desk/complaints/{id}", id).cookie(swati)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/desk/complaints/{id}/status", id).with(sameOriginCsrf()).cookie(swati)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/desk/complaints/{id}/assignee", id).with(sameOriginCsrf()).cookie(swati)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/desk/complaints/{id}/comments", id).with(sameOriginCsrf()).cookie(swati)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }

    private long raise(String description) throws Exception {
        return json(mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat).param("description", description)
                .with(sameOriginCsrf()).cookie(swati))).get("id").asLong();
    }

    private ResultActions changeStatus(long id, String status, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("note", note);
        return mockMvc.perform(patch("/api/desk/complaints/{id}/status", id).with(sameOriginCsrf()).cookie(anjali)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions reassign(long id, Long assigneeId, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("assigneeId", assigneeId);
        body.put("note", note);
        return mockMvc.perform(patch("/api/desk/complaints/{id}/assignee", id).with(sameOriginCsrf()).cookie(anjali)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions comment(long id, String text) throws Exception {
        return mockMvc.perform(post("/api/desk/complaints/{id}/comments", id).with(sameOriginCsrf()).cookie(anjali)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("text", text))));
    }

    private JsonNode board(Map<String, String> params) throws Exception {
        var request = get("/api/desk/complaints").cookie(anjali);
        params.forEach(request::param);
        return json(mockMvc.perform(request).andExpect(status().isOk()));
    }

    private static List<Long> ids(JsonNode rows) {
        List<Long> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
