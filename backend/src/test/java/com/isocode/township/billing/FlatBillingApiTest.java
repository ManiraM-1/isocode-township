package com.isocode.township.billing;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class FlatBillingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private BillingDemoSeeder billingSeeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private TestSessions sessions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long swatiFlat;
    private Long neerajFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
        neerajFlat = flatRepository.findByCode("T07-1203").orElseThrow().getId();
    }

    @Test
    void summaryShowsOutstandingLiveLateFeeAndDaysOverdue() throws Exception {
        billingSeeder.seed();

        mockMvc.perform(get("/api/flats/{id}/summary", swatiFlat).cookie(sessions.sessionFor(TestSessions.SWATI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flatCode").value("T12-0408"))
                .andExpect(jsonPath("$.asOf").value("2026-11-03"))
                .andExpect(jsonPath("$.outstanding").value("19373.87"))
                .andExpect(jsonPath("$.advance").value("0.00"))
                .andExpect(jsonPath("$.accruedLateFee").value("21.98"))
                .andExpect(jsonPath("$.oldestUnpaidDueDate").value("2026-09-10"))
                .andExpect(jsonPath("$.daysOverdue").value(54));
    }

    @Test
    void billsAreListedNewestFirstWithStatus() throws Exception {
        billingSeeder.seed();

        mockMvc.perform(get("/api/flats/{id}/bills", swatiFlat).cookie(sessions.sessionFor(TestSessions.SWATI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].month", contains("2026-11", "2026-10", "2026-09")))
                .andExpect(jsonPath("$[*].status", contains("DUE", "OVERDUE", "OVERDUE")))
                .andExpect(jsonPath("$[*].amount", contains("6370.00", "6370.00", "6370.00")))
                .andExpect(jsonPath("$[0].dueDate").value("2026-11-10"))
                .andExpect(jsonPath("$[0].balance").value("6370.00"));
    }

    @Test
    void ledgerShowsEveryDebitInDateOrderWithARunningBalance() throws Exception {
        billingSeeder.seed();

        mockMvc.perform(get("/api/flats/{id}/ledger", swatiFlat).cookie(sessions.sessionFor(TestSessions.SWATI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].type", contains("BILL", "LATE_FEE", "BILL", "LATE_FEE", "LATE_FEE", "BILL")))
                .andExpect(jsonPath("$[*].debit", contains("6370.00", "73.30", "6370.00", "113.61", "76.96", "6370.00")))
                .andExpect(jsonPath("$[*].runningBalance",
                        contains("6370.00", "6443.30", "12813.30", "12926.91", "13003.87", "19373.87")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "bills", "ledger"})
    void residentGets403OnAnotherFlat(String endpoint) throws Exception {
        mockMvc.perform(get("/api/flats/{id}/" + endpoint, swatiFlat).cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have access to this resource."));
    }

    @Test
    void residentGets403OnAFlatThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/flats/{id}/summary", 999_999).cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isForbidden());
    }

    @Test
    void helpDeskCanReadAnyFlat() throws Exception {
        billingSeeder.seed();

        mockMvc.perform(get("/api/flats/{id}/summary", swatiFlat).cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding").value("19373.87"));
        mockMvc.perform(get("/api/flats/{id}/summary", 999_999).cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedOutRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/flats/{id}/summary", swatiFlat)).andExpect(status().isUnauthorized());
    }

    @Test
    void flatWithNoBillsOwesNothing() throws Exception {
        mockMvc.perform(get("/api/flats/{id}/summary", neerajFlat).cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding").value("0.00"))
                .andExpect(jsonPath("$.accruedLateFee").value("0.00"))
                .andExpect(jsonPath("$.oldestUnpaidDueDate").isEmpty())
                .andExpect(jsonPath("$.daysOverdue").value(0));
    }

    @Test
    void creditBeyondDebitsShowsAsAdvanceNotNegativeOutstanding() throws Exception {
        entityManager.flush();
        jdbcTemplate.update("""
                INSERT INTO ledger_entry (flat_id, entry_date, type, debit, credit, ref_type, ref_id, description, created_at)
                VALUES (?, DATE '2026-11-02', 'PAYMENT', 0, 500.00, 'TEST', 1, 'Advance', now())
                """, neerajFlat);

        mockMvc.perform(get("/api/flats/{id}/summary", neerajFlat).cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(jsonPath("$.outstanding").value("0.00"))
                .andExpect(jsonPath("$.advance").value("500.00"));
    }
}
