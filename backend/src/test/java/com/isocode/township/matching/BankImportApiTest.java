package com.isocode.township.matching;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.billing.FlatStatementService;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import com.isocode.township.payment.DemoHistorySeeder;
import com.isocode.township.payment.PaymentRepository;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class BankImportApiTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private FlatStatementService statementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestSessions sessions;

    private Cookie staff;
    private byte[] sample;

    @BeforeEach
    void setUp() throws IOException {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
        upiIdSeeder.seed();
        staff = sessions.sessionFor(TestSessions.ANJALI);
        sample = Files.readAllBytes(SampleStatementTest.SAMPLE);
    }

    @Test
    void importingTheSampleGivesTheDecisionsListedInTheReadme() throws Exception {
        JsonNode details = json(upload(staff, "statement.csv", sample).andExpect(status().isOk()));

        assertThat(decisionsByRow(details)).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                entry(2, "NON_CREDIT"), entry(3, "AUTO_POSTED"), entry(4, "AUTO_POSTED"), entry(5, "NON_CREDIT"),
                entry(6, "AUTO_POSTED"), entry(7, "SUGGESTED"), entry(8, "SUGGESTED"), entry(9, "SUGGESTED"),
                entry(10, "UNMATCHED"), entry(11, "SUGGESTED"), entry(12, "CONFLICT"), entry(13, "AUTO_POSTED"),
                entry(14, "CONFLICT"), entry(15, "RECONCILED_GATEWAY"), entry(16, "CONFLICT"), entry(17, "DUPLICATE"),
                entry(18, "AUTO_POSTED"), entry(19, "SUGGESTED"), entry(20, "POSSIBLE_DUPLICATE"),
                entry(21, "UNMATCHED"), entry(22, "DUPLICATE"), entry(23, "SUGGESTED"), entry(24, "PARSE_ERROR")));
        JsonNode summary = details.get("summary");
        assertThat(summary.get("totalRows").asInt()).isEqualTo(23);
        assertThat(summary.get("autoPosted").asInt()).isEqualTo(5);
        assertThat(summary.get("reconciled").asInt()).isEqualTo(1);
        assertThat(summary.get("suggested").asInt()).isEqualTo(6);
        assertThat(summary.get("conflicts").asInt()).isEqualTo(3);
        assertThat(summary.get("unmatched").asInt()).isEqualTo(2);
        assertThat(summary.get("duplicates").asInt()).isEqualTo(2);
        assertThat(summary.get("possibleDuplicates").asInt()).isEqualTo(1);
        assertThat(summary.get("nonCredit").asInt()).isEqualTo(2);
        assertThat(summary.get("errors").asInt()).isEqualTo(1);
        assertThat(summary.get("sameFileImportedBefore").isNull()).isTrue();
    }

    @Test
    void onlyAutoPostedRowsCreatePaymentsAndTheGatewayRowLinksTheExistingOne() throws Exception {
        long paymentsBefore = paymentRepository.count();

        JsonNode rows = json(upload(staff, "statement.csv", sample)).get("rows");

        assertThat(paymentRepository.count()).isEqualTo(paymentsBefore + 5);
        for (JsonNode row : rows) {
            String decision = row.get("decision").asText();
            boolean hasReceipt = !row.get("receiptNo").isNull();
            assertThat(hasReceipt).as("row %s %s", row.get("rowNo"), decision)
                    .isEqualTo(decision.equals("AUTO_POSTED") || decision.equals("RECONCILED_GATEWAY"));
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM payment p JOIN bank_row r ON r.payment_id = p.id
                WHERE r.decision = 'AUTO_POSTED' AND p.source = 'BANK_STATEMENT'
                """, Integer.class)).isEqualTo(5);
    }

    @Test
    void autoPostedPaymentsReduceTheFlatsDues() throws Exception {
        Long swati = flatRepository.findByCode("T12-0408").orElseThrow().getId();

        upload(staff, "statement.csv", sample);

        assertThat(statementService.summary(swati).outstanding()).isEqualTo(new BigDecimal("12978.21"));
    }

    @Test
    void importingTheSameStatementAgainPostsNothingAndReportsDuplicates() throws Exception {
        upload(staff, "statement.csv", sample);
        long paymentsAfterFirst = paymentRepository.count();

        JsonNode summary = json(upload(staff, "statement-again.csv", sample)).get("summary");

        assertThat(paymentRepository.count()).isEqualTo(paymentsAfterFirst);
        assertThat(summary.get("autoPosted").asInt()).isZero();
        assertThat(summary.get("reconciled").asInt()).isZero();
        assertThat(summary.get("duplicates").asInt()).isEqualTo(19);
        assertThat(summary.get("possibleDuplicates").asInt()).isEqualTo(2);
        assertThat(summary.get("nonCredit").asInt()).isEqualTo(1);
        assertThat(summary.get("errors").asInt()).isEqualTo(1);
        assertThat(summary.get("sameFileImportedBefore").isNull()).isFalse();
    }

    @Test
    void possibleDuplicateLinksTheRowItRepeats() throws Exception {
        JsonNode rows = json(upload(staff, "statement.csv", sample)).get("rows");
        JsonNode cash = rowNo(rows, 19);
        JsonNode repeat = rowNo(rows, 20);

        assertThat(repeat.get("duplicateOf").get("rowId").asLong()).isEqualTo(cash.get("id").asLong());
        assertThat(repeat.get("duplicateOf").get("rowNo").asInt()).isEqualTo(19);
        assertThat(repeat.get("openInQueue").asBoolean()).isTrue();
        assertThat(repeat.get("receiptNo").isNull()).isTrue();
    }

    @Test
    void accountsQueueListsEveryOpenRowWithReasonsAndCandidates() throws Exception {
        upload(staff, "statement.csv", sample);

        JsonNode queue = json(mockMvc.perform(get("/api/desk/accounts-queue").cookie(staff)).andExpect(status().isOk()));

        assertThat(queue).hasSize(12);
        assertThat(queue).allSatisfy(row -> assertThat(row.get("reasons")).isNotEmpty());
        JsonNode sharedAmount = rowNo(queue, 10);
        assertThat(sharedAmount.get("candidates")).extracting(candidate -> candidate.get("code").asText())
                .containsExactlyInAnyOrder("T21-0104", "T21-1402");
        assertThat(rowNo(queue, 23).get("reasons").toString()).contains("check before posting");
    }

    @Test
    void importIsAudited() throws Exception {
        long importId = json(upload(staff, "statement.csv", sample)).get("summary").get("id").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT details->>'autoPosted' FROM audit_log WHERE action = 'BANK_STATEMENT_IMPORTED' AND entity_id = ?",
                String.class, importId)).isEqualTo("5");
    }

    @Test
    void residentsCannotImportOrSeeImportsOrTheQueue() throws Exception {
        Cookie resident = sessions.sessionFor(TestSessions.AAMIR);

        upload(resident, "statement.csv", sample).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/desk/bank-imports").cookie(resident)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/desk/accounts-queue").cookie(resident)).andExpect(status().isForbidden());
    }

    @Test
    void filesThatAreNotUsableStatementsAreRejectedWithAReason() throws Exception {
        upload(staff, "statement.txt", sample)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Upload the bank statement as a .csv file."));
        upload(staff, "empty.csv", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty."));
        upload(staff, "no-credit.csv", "Txn Date,Description\n03/11/2026,x\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("The statement is missing a required column")));
        upload(staff, "huge.csv", new byte[(int) BankImportService.MAX_FILE_BYTES + 1])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is larger than 5 MB. Split the statement into smaller files."));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bank_import", Integer.class)).isZero();
    }

    private ResultActions upload(Cookie session, String fileName, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/desk/bank-imports")
                .file(new MockMultipartFile("file", fileName, "text/csv", content))
                .with(sameOriginCsrf())
                .cookie(session));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static Map<Integer, String> decisionsByRow(JsonNode details) {
        Map<Integer, String> decisions = new HashMap<>();
        details.get("rows").forEach(row -> decisions.put(row.get("rowNo").asInt(), row.get("decision").asText()));
        return decisions;
    }

    private static JsonNode rowNo(JsonNode rows, int rowNo) {
        for (JsonNode row : rows) {
            if (row.get("rowNo").asInt() == rowNo) {
                return row;
            }
        }
        throw new AssertionError("No row " + rowNo);
    }
}
