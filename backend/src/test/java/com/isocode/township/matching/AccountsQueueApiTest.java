package com.isocode.township.matching;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.DemoHistorySeeder;
import com.isocode.township.payment.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class AccountsQueueApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private DemoHistorySeeder historySeeder;

    @Autowired
    private KnownUpiIdDemoSeeder upiIdSeeder;

    @Autowired
    private KnownUpiIdRepository knownUpiIdRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestSessions sessions;

    @Autowired
    private EntityManager entityManager;

    private Cookie staff;
    private Map<Integer, Long> rowIds;

    @BeforeEach
    void importTheSample() throws Exception {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
        upiIdSeeder.seed();
        staff = sessions.sessionFor(TestSessions.ANJALI);
        JsonNode details = json(mockMvc.perform(multipart("/api/desk/bank-imports")
                .file(new MockMultipartFile("file", "statement.csv", "text/csv",
                        Files.readAllBytes(SampleStatementTest.SAMPLE)))
                .with(sameOriginCsrf()).cookie(staff)));
        rowIds = new HashMap<>();
        details.get("rows").forEach(row -> rowIds.put(row.get("rowNo").asInt(), row.get("id").asLong()));
    }

    @Test
    void confirmingASuggestionPostsExactlyOnceAndLeavesTheQueue() throws Exception {
        long paymentsBefore = paymentRepository.count();

        confirm(7).andExpect(status().isOk())
                .andExpect(jsonPath("$.row.resolution").value("CONFIRMED"))
                .andExpect(jsonPath("$.row.resolvedBy").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.row.openInQueue").value(false))
                .andExpect(jsonPath("$.row.receiptNo").isNotEmpty());
        confirm(7).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This row was already resolved."));

        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore + 1);
        assertThat(paymentFlatCode(7)).isEqualTo("T15-0702");
        assertThat(queueRowNos()).doesNotContainKey(7);
    }

    @Test
    void confirmingLearnsTheUpiIdForTheFlat() throws Exception {
        confirm(9).andExpect(status().isOk()).andExpect(jsonPath("$.warnings").isEmpty());

        KnownUpiId learned = knownUpiIdRepository.findByUpiId("shree.ent@ybl").orElseThrow();
        assertThat(learned.getFlatId()).isEqualTo(flatRepository.findByCode("T12-1108").orElseThrow().getId());
        assertThat(learned.getLearnedFromRowId()).isEqualTo(rowIds.get(9));
    }

    @Test
    void assigningARowWhoseUpiIdBelongsToAnotherFlatWarnsAndKeepsTheOldMapping() throws Exception {
        Long neerajFlat = flatRepository.findByCode("T07-1203").orElseThrow().getId();
        Long aamirFlat = flatRepository.findByCode("T21-1601").orElseThrow().getId();

        assign(12, neerajFlat, "Aamir paid on Neeraj's behalf, confirmed by phone")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("UPI aamir.s@okicici is already known for T21-1601; not changed"));

        assertThat(knownUpiIdRepository.findByUpiId("aamir.s@okicici").orElseThrow().getFlatId()).isEqualTo(aamirFlat);
        assertThat(paymentFlatCode(12)).isEqualTo("T07-1203");
    }

    @Test
    void assigningToTheChosenFlatWorksEvenAgainstTheSuggestionAndIsAudited() throws Exception {
        Long chosenFlat = flatRepository.findByCode("T26-0807").orElseThrow().getId();

        assign(21, chosenFlat, "Resident called; caterer refund was routed to their maintenance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.resolution").value("ASSIGNED"))
                .andExpect(jsonPath("$.row.resolutionNote").value("Resident called; caterer refund was routed to their maintenance"));

        assertThat(paymentFlatCode(21)).isEqualTo("T26-0807");
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT actor_user_id, details->>'flatId' AS flat_id, details->>'note' AS note
                FROM audit_log WHERE action = 'BANK_ROW_ASSIGNED' AND entity_id = ?
                """, rowIds.get(21));
        assertThat(audit.get("flat_id")).isEqualTo(chosenFlat.toString());
        assertThat(audit.get("actor_user_id")).isNotNull();
    }

    @Test
    void confirmingARowWithoutASuggestionAsksForAnAssignment() throws Exception {
        confirm(10).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This row has no suggested flat. Assign it to a flat instead."));
    }

    @Test
    void ignoringAPossibleDuplicateClosesItWithoutPosting() throws Exception {
        long paymentsBefore = paymentRepository.count();

        ignore(20, "Duplicate of row 19, bank listed the deposit twice")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.resolution").value("IGNORED"))
                .andExpect(jsonPath("$.row.receiptNo").isEmpty());

        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore);
        assertThat(queueRowNos()).doesNotContainKey(20).containsKey(19);
    }

    @Test
    void ignoringNeedsAReason() throws Exception {
        ignore(21, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void rowsOutsideTheQueueCannotBeActedOn() throws Exception {
        confirm(3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This row is not in the accounts queue."));
        ignore(17, "Already posted").andExpect(status().isConflict());
    }

    @Test
    void flatSearchFindsFlatsByCodeOrResidentName() throws Exception {
        mockMvc.perform(get("/api/desk/flats/search").param("q", "t21 16").cookie(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("T21-1601"))
                .andExpect(jsonPath("$[0].residents[0]").value("Aamir Shaikh"));
        mockMvc.perform(get("/api/desk/flats/search").param("q", "swati").cookie(staff))
                .andExpect(jsonPath("$[0].code").value("T12-0408"));
        mockMvc.perform(get("/api/desk/flats/search").param("q", "x").cookie(staff))
                .andExpect(status().isBadRequest());
    }

    @Test
    void residentsCannotActOnTheQueueOrSearchFlats() throws Exception {
        Cookie resident = sessions.sessionFor(TestSessions.SWATI);

        mockMvc.perform(post("/api/desk/bank-rows/{id}/confirm", rowIds.get(7)).with(sameOriginCsrf()).cookie(resident))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/desk/bank-rows/{id}/ignore", rowIds.get(7)).with(sameOriginCsrf()).cookie(resident)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"not mine\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/desk/flats/search").param("q", "T12").cookie(resident))
                .andExpect(status().isForbidden());
    }

    private ResultActions confirm(int rowNo) throws Exception {
        return mockMvc.perform(post("/api/desk/bank-rows/{id}/confirm", rowIds.get(rowNo))
                .with(sameOriginCsrf()).cookie(staff));
    }

    private ResultActions assign(int rowNo, Long flatId, String note) throws Exception {
        return mockMvc.perform(post("/api/desk/bank-rows/{id}/assign", rowIds.get(rowNo))
                .with(sameOriginCsrf()).cookie(staff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("flatId", flatId, "note", note))));
    }

    private ResultActions ignore(int rowNo, String reason) throws Exception {
        return mockMvc.perform(post("/api/desk/bank-rows/{id}/ignore", rowIds.get(rowNo))
                .with(sameOriginCsrf()).cookie(staff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private String paymentFlatCode(int rowNo) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
                SELECT f.code FROM bank_row r JOIN payment p ON p.id = r.payment_id JOIN flat f ON f.id = p.flat_id
                WHERE r.id = ?
                """, String.class, rowIds.get(rowNo));
    }

    private Map<Integer, Boolean> queueRowNos() throws Exception {
        Map<Integer, Boolean> open = new HashMap<>();
        json(mockMvc.perform(get("/api/desk/accounts-queue").cookie(staff)))
                .forEach(row -> open.put(row.get("rowNo").asInt(), true));
        return open;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
