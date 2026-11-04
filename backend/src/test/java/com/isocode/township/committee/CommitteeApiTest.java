package com.isocode.township.committee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.auth.AppUserRepository;
import com.isocode.township.billing.BillingRunService;
import com.isocode.township.billing.LateFeePostingService;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.common.Money;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.PaymentMethod;
import com.isocode.township.payment.PaymentPostingService;
import com.isocode.township.payment.PaymentSource;
import com.isocode.township.payment.PostPaymentCommand;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CommitteeApiTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final Set<String> BRIEF_FLATS = Set.of("T07-1203", "T12-0408", "T21-1601");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private TestSessions sessions;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private BillingRunService billingRunService;

    @Autowired
    private LateFeePostingService lateFeePostingService;

    @Autowired
    private PaymentPostingService paymentPostingService;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie anjali;
    private Long staffId;
    private Long neeraj;
    private Long swati;
    private Long aamir;
    private Long advanceFlat;

    @BeforeEach
    void buildDataset() {
        seeder.seedIfEmpty();
        anjali = sessions.sessionFor(TestSessions.ANJALI);
        staffId = userRepository.findByPhone(TestSessions.ANJALI).orElseThrow().getId();
        neeraj = flatId("T07-1203");
        swati = flatId("T12-0408");
        aamir = flatId("T21-1601");
        advanceFlat = flatRepository.findAll().stream()
                .filter(flat -> !BRIEF_FLATS.contains(flat.getCode()))
                .map(flat -> flat.getId()).min(Long::compare).orElseThrow();

        issueBills(SEPTEMBER);
        pay(neeraj, "7475.00", "2026-09-08");
        pay(aamir, "10660.00", "2026-09-25");
        postInterestUpTo("2026-09-30");

        issueBills(OCTOBER);
        pay(neeraj, "7475.00", "2026-10-09");
        pay(advanceFlat, "2000.00", "2026-10-15");
        pay(aamir, "5000.00", "2026-10-20");
        postInterestUpTo("2026-10-31");

        complaintsAndImports();
    }

    @Test
    void octoberCollectionsAndReconciliationAreExact() throws Exception {
        overview("2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.asOf").value("2026-10-31"))
                .andExpect(jsonPath("$.collections.billed").value("24505.00"))
                .andExpect(jsonPath("$.collections.lateFeesCharged").value("284.85"))
                .andExpect(jsonPath("$.collections.lateFeeReversals").value("0.00"))
                .andExpect(jsonPath("$.collections.collected").value("14475.00"))
                .andExpect(jsonPath("$.collections.totalDue").value("31319.01"))
                .andExpect(jsonPath("$.collections.collectionRate").value("46.2"))
                .andExpect(jsonPath("$.collections.billsDue").value(3))
                .andExpect(jsonPath("$.collections.billsPaidOnTime").value(1))
                .andExpect(jsonPath("$.collections.onTimeRate").value("33.3"))
                .andExpect(jsonPath("$.collections.onTimeTarget").value("90"))
                .andExpect(jsonPath("$.reconciliation.openingOutstanding").value("6529.16"))
                .andExpect(jsonPath("$.reconciliation.billed").value("24505.00"))
                .andExpect(jsonPath("$.reconciliation.lateFeesCharged").value("284.85"))
                .andExpect(jsonPath("$.reconciliation.lateFeeReversals").value("0.00"))
                .andExpect(jsonPath("$.reconciliation.collections").value("14475.00"))
                .andExpect(jsonPath("$.reconciliation.expectedClosingOutstanding").value("16844.01"))
                .andExpect(jsonPath("$.reconciliation.closingOutstanding").value("16844.01"))
                .andExpect(jsonPath("$.reconciliation.balanced").value(true));
    }

    @Test
    void octoberAgeingAddsUpToClosingOutstandingWithAnInCreditLine() throws Exception {
        overview("2026-10")
                .andExpect(jsonPath("$.ageing.asOf").value("2026-10-31"))
                .andExpect(jsonPath("$.ageing.buckets[*].bucket",
                        contains("not-yet-due", "1-30", "31-60", "61-90", "90-plus", "in-credit")))
                .andExpect(jsonPath("$.ageing.buckets[*].flats", contains(0, 0, 2, 0, 0, 1)))
                .andExpect(jsonPath("$.ageing.buckets[*].amount",
                        contains("0.00", "0.00", "18844.01", "0.00", "0.00", "-2000.00")))
                .andExpect(jsonPath("$.ageing.buckets[5].label").value("In credit"))
                .andExpect(jsonPath("$.ageing.total").value("16844.01"))
                .andExpect(jsonPath("$.ageing.matchesClosingOutstanding").value(true));
    }

    @Test
    void ageingBucketListsItsFlatsMostOwedFirst() throws Exception {
        mockMvc.perform(get("/api/committee/ageing/31-60").param("month", "2026-10").cookie(anjali))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("31–60 days"))
                .andExpect(jsonPath("$.flats").value(2))
                .andExpect(jsonPath("$.amount").value("18844.01"))
                .andExpect(jsonPath("$.items[*].flatCode", contains("T12-0408", "T21-1601")))
                .andExpect(jsonPath("$.items[*].amount", contains("13003.87", "5840.14")))
                .andExpect(jsonPath("$.items[*].oldestUnpaidDue", contains("2026-09-10", "2026-09-10")))
                .andExpect(jsonPath("$.items[*].daysOverdue", contains(51, 51)));
        mockMvc.perform(get("/api/committee/ageing/in-credit").param("month", "2026-10").cookie(anjali))
                .andExpect(jsonPath("$.items[0].amount").value("-2000.00"))
                .andExpect(jsonPath("$.items[0].oldestUnpaidDue").isEmpty());
        mockMvc.perform(get("/api/committee/ageing/120-plus").cookie(anjali))
                .andExpect(status().isNotFound());
    }

    @Test
    void octoberMatchingFiguresComeFromThatMonthsImportsAndTheQueueNow() throws Exception {
        overview("2026-10")
                .andExpect(jsonPath("$.matching.lastImport.fileName").value("october.csv"))
                .andExpect(jsonPath("$.matching.lastImport.autoPosted").value(4))
                .andExpect(jsonPath("$.matching.importsThisMonth").value(1))
                .andExpect(jsonPath("$.matching.newPaymentRows").value(8))
                .andExpect(jsonPath("$.matching.autoPosted").value(4))
                .andExpect(jsonPath("$.matching.autoPostShare").value("50.0"))
                .andExpect(jsonPath("$.matching.queueSize").value(2))
                .andExpect(jsonPath("$.matching.oldestQueuedAt").value("2026-10-12T04:30:00Z"))
                .andExpect(jsonPath("$.matching.oldestQueuedDays").value(22));
        overview("2026-11")
                .andExpect(jsonPath("$.matching.lastImport.fileName").value("november.csv"))
                .andExpect(jsonPath("$.matching.importsThisMonth").value(1))
                .andExpect(jsonPath("$.matching.autoPostShare").value("50.0"));
    }

    @Test
    void octoberComplaintMetricsAreExact() throws Exception {
        overview("2026-10")
                .andExpect(jsonPath("$.complaints.opened").value(3))
                .andExpect(jsonPath("$.complaints.resolved").value(3))
                .andExpect(jsonPath("$.complaints.medianFirstResponseMinutes").value(95))
                .andExpect(jsonPath("$.complaints.medianResolutionMinutes").value(1440))
                .andExpect(jsonPath("$.complaints.firstResponseMeasured").value(3))
                .andExpect(jsonPath("$.complaints.firstResponseBreached").value(2))
                .andExpect(jsonPath("$.complaints.firstResponseBreachShare").value("66.7"))
                .andExpect(jsonPath("$.complaints.reopened").value(1))
                .andExpect(jsonPath("$.complaints.reopenRate").value("33.3"))
                .andExpect(jsonPath("$.complaints.reopenTarget").value("8"))
                .andExpect(jsonPath("$.complaints.ratings").value(2))
                .andExpect(jsonPath("$.complaints.averageRating").value("3.5"))
                .andExpect(jsonPath("$.complaints.openByCategory[*].category", contains("LIFT", "PLUMBING")))
                .andExpect(jsonPath("$.complaints.openByCategory[*].open", contains(1, 1)))
                .andExpect(jsonPath("$.complaints.activeSafetyEscalations").value(1))
                .andExpect(jsonPath("$.complaints.unacknowledgedEscalations").value(1));
    }

    @Test
    void aBackDatedPaymentShowsAsALateFeeReversalAndNovemberStillBalances() throws Exception {
        pay(swati, "3000.00", "2026-09-20");

        overview("2026-11")
                .andExpect(jsonPath("$.asOf").value("2026-11-03"))
                .andExpect(jsonPath("$.reconciliation.openingOutstanding").value("13844.01"))
                .andExpect(jsonPath("$.reconciliation.billed").value("0.00"))
                .andExpect(jsonPath("$.reconciliation.lateFeeReversals").value("72.49"))
                .andExpect(jsonPath("$.reconciliation.collections").value("0.00"))
                .andExpect(jsonPath("$.reconciliation.closingOutstanding").value("13771.52"))
                .andExpect(jsonPath("$.reconciliation.balanced").value(true))
                .andExpect(jsonPath("$.collections.billsDue").value(0))
                .andExpect(jsonPath("$.collections.onTimeRate").isEmpty())
                .andExpect(jsonPath("$.ageing.total").value("13771.52"))
                .andExpect(jsonPath("$.ageing.matchesClosingOutstanding").value(true));
        overview("2026-10")
                .andExpect(jsonPath("$.reconciliation.closingOutstanding").value("13844.01"))
                .andExpect(jsonPath("$.reconciliation.balanced").value(true));
    }

    @Test
    void theMonthlyReportDownloadsAsCsv() throws Exception {
        String csv = mockMvc.perform(get("/api/committee/report.csv").param("month", "2026-10").cookie(anjali))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"committee-report-2026-10.csv\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("Section,Metric,Value\r\nReport,Month,2026-10\r\n")
                .contains("Collections,Collection rate %,46.2\r\n")
                .contains("Reconciliation,Less late-fee reversals,0.00\r\n")
                .contains("Reconciliation,Closing outstanding,16844.01\r\n")
                .contains("Reconciliation,Balanced,Yes\r\n")
                .contains("Dues ageing,31–60 days amount,18844.01\r\n")
                .contains("Dues ageing,In credit amount,-2000.00\r\n")
                .contains("Payment matching,Auto-post share %,50.0\r\n")
                .contains("Complaints,Reopen rate %,33.3\r\n")
                .contains("Complaints,Open now: LIFT,1\r\n");
    }

    @Test
    void theMonthDefaultsToTheCurrentOneAndBadMonthsAreRefused() throws Exception {
        mockMvc.perform(get("/api/committee/overview").cookie(anjali))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-11"));
        overview("2026-12").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The committee report covers months up to the current one."));
        overview("October").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a month as YYYY-MM, for example 2026-10."));
    }

    @Test
    void residentsCannotOpenTheCommitteeView() throws Exception {
        Cookie resident = sessions.sessionFor(TestSessions.NEERAJ);
        mockMvc.perform(get("/api/committee/overview").cookie(resident)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/committee/ageing/31-60").cookie(resident)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/committee/report.csv").cookie(resident)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/committee/overview")).andExpect(status().isUnauthorized());
    }

    private ResultActions overview(String month) throws Exception {
        return mockMvc.perform(get("/api/committee/overview").param("month", month).cookie(anjali));
    }

    private void issueBills(YearMonth month) {
        for (Long flatId : new Long[] {neeraj, swati, aamir}) {
            billingRunService.issueBill(flatId, month, month.atDay(10), staffId);
        }
    }

    private void pay(Long flatId, String amount, String paidOn) {
        paymentPostingService.post(new PostPaymentCommand(flatId, Money.of(amount), PaymentMethod.CHEQUE,
                PaymentSource.MANUAL, null, null, LocalDate.parse(paidOn), staffId));
    }

    private void postInterestUpTo(String lastDay) {
        lateFeePostingService.postInterestUpTo(LocalDate.parse(lastDay), Money.of("21.00"), Instant.now(), staffId);
    }

    private void complaintsAndImports() {
        Long raisedBy = userRepository.findByPhone(TestSessions.SWATI).orElseThrow().getId();
        Long dutyManager = jdbc.queryForObject("SELECT id FROM app_user WHERE is_duty_manager LIMIT 1", Long.class);
        complaint("CMP-T-1", "PLUMBING", "CLOSED", "2026-10-02T04:30:00Z", "2026-10-02T04:40:00Z",
                "2026-10-02T04:45:00Z", "2026-10-02T08:30:00Z", "2026-10-03T08:30:00Z", 0, 5, raisedBy);
        complaint("CMP-T-2", "ELECTRICAL", "CLOSED", "2026-10-05T04:30:00Z", "2026-10-05T07:30:00Z",
                "2026-10-05T06:30:00Z", "2026-10-06T04:30:00Z", "2026-10-07T04:30:00Z", 1, 2, raisedBy);
        Long lift = complaint("CMP-T-3", "LIFT", "OPEN", "2026-10-30T04:30:00Z", null,
                "2026-10-31T04:30:00Z", null, null, 0, null, raisedBy);
        complaint("CMP-T-4", "CIVIL", "RESOLVED", "2026-09-20T04:30:00Z", "2026-09-20T05:30:00Z",
                "2026-09-20T12:30:00Z", "2026-10-04T04:30:00Z", null, 0, null, raisedBy);
        complaint("CMP-T-5", "PLUMBING", "ASSIGNED", "2026-11-02T04:30:00Z", null,
                "2026-11-03T12:30:00Z", null, null, 0, null, raisedBy);
        jdbc.update("INSERT INTO escalation (complaint_id, duty_manager_id, reason, created_at) VALUES (?, ?, ?, ?)",
                lift, dutyManager, "Safety words: lift stuck", timestamp("2026-10-30T04:30:00Z"));

        Long october = bankImport("october.csv", "2026-10-12T04:30:00Z", 14, 4, 1, 2, 1, 1, 0, 3, 2);
        bankImport("november.csv", "2026-11-02T04:30:00Z", 2, 1, 0, 1, 0, 0, 0, 0, 0);
        bankRow(october, 1, "SUGGESTED", false);
        bankRow(october, 2, "UNMATCHED", false);
        bankRow(october, 3, "CONFLICT", true);
        bankRow(october, 4, "AUTO_POSTED", false);
    }

    private Long complaint(String refNo, String category, String status, String created, String firstResponse,
                           String firstResponseDue, String resolved, String closed, int reopens, Integer rating,
                           Long raisedBy) {
        return jdbc.queryForObject("""
                INSERT INTO complaint (ref_no, flat_id, raised_by, description, category, urgency, classified_by,
                                       status, assignee_id, created_at, first_response_at, first_response_due_at,
                                       resolve_due_at, resolved_at, closed_at, reopen_count, rating)
                VALUES (?, ?, ?, 'Hand-built committee test complaint', ?, 'MEDIUM', 'AI', ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?)
                RETURNING id
                """, Long.class, refNo, swati, raisedBy, category, status, "OPEN".equals(status) ? null : staffId,
                timestamp(created), timestamp(firstResponse), timestamp(firstResponseDue),
                timestamp(firstResponseDue), timestamp(resolved), timestamp(closed), reopens, rating);
    }

    private Long bankImport(String fileName, String uploadedAt, int total, int autoPosted, int reconciled,
                            int suggested, int conflicts, int unmatched, int possibleDuplicates, int duplicates,
                            int nonCredit) {
        return jdbc.queryForObject("""
                INSERT INTO bank_import (file_name, file_sha256, uploaded_by, uploaded_at, total_rows, auto_posted,
                                         reconciled, suggested, conflicts, unmatched, duplicates,
                                         possible_duplicates, non_credit, errors)
                VALUES (?, md5(?) || md5(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                RETURNING id
                """, Long.class, fileName, fileName, uploadedAt, staffId, timestamp(uploadedAt), total, autoPosted,
                reconciled, suggested, conflicts, unmatched, duplicates, possibleDuplicates, nonCredit);
    }

    private void bankRow(Long importId, int rowNo, String decision, boolean resolved) {
        jdbc.update("""
                INSERT INTO bank_row (import_id, row_no, txn_date, description, amount, decision, resolution,
                                      resolved_by, resolved_at)
                VALUES (?, ?, DATE '2026-10-10', 'NEFT credit', 1000.00, ?, ?, ?, ?)
                """, importId, rowNo, decision, resolved ? "IGNORED" : null, resolved ? staffId : null,
                resolved ? timestamp("2026-10-13T04:30:00Z") : null);
    }

    private Long flatId(String code) {
        return flatRepository.findByCode(code).orElseThrow().getId();
    }

    private static Timestamp timestamp(String instant) {
        return instant == null ? null : Timestamp.from(Instant.parse(instant));
    }
}
