package com.isocode.township.matching;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.payment.DemoHistorySeeder;
import com.isocode.township.payment.PaymentRepository;
import java.io.ByteArrayInputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DemoStatementTest {

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private DemoHistorySeeder historySeeder;

    @Autowired
    private KnownUpiIdDemoSeeder upiIdSeeder;

    @Autowired
    private DemoStatementGenerator generator;

    @Autowired
    private MatchContextFactory contextFactory;

    @Autowired
    private BankImportService importService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestSessions sessions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long staffId;

    @BeforeEach
    void seed() {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
        upiIdSeeder.seed();
        staffId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, TestSessions.ANJALI);
    }

    @Test
    void generatedStatementProducesEveryDecisionType() {
        byte[] statement = generator.generate();

        assertThat(decisions(statement)).containsAll(EnumSet.allOf(MatchDecision.class));
        assertThat(BankStatementParser.parse(new ByteArrayInputStream(statement)).errors()).hasSize(1);
    }

    @Test
    void theLargeAmountRowIsHeldWithItsReason() {
        List<MatchResult> results = match(generator.generate());

        assertThat(results).filteredOn(result -> result.decision() == MatchDecision.SUGGESTED)
                .flatExtracting(MatchResult::reasons)
                .anySatisfy(reason -> assertThat(reason).contains("check before posting"));
    }

    @Test
    void afterImportingItAFreshStatementStillCoversEveryDecisionType() {
        importService.importFile("demo.csv", generator.generate(), staffId);

        assertThat(decisions(generator.generate())).containsAll(EnumSet.allOf(MatchDecision.class));
    }

    @Test
    void uploadingTheSameGeneratedFileTwicePostsNothingTheSecondTime() {
        byte[] statement = generator.generate();
        importService.importFile("demo.csv", statement, staffId);
        long paymentsAfterFirst = paymentRepository.count();

        BankImport second = importService.importFile("demo-again.csv", statement, staffId);

        assertThat(paymentRepository.count()).isEqualTo(paymentsAfterFirst);
        assertThat(second.getAutoPosted()).isZero();
    }

    @Test
    void endpointDoesNotExistWhenDemoModeIsOff() throws Exception {
        mockMvc.perform(get("/api/desk/demo/bank-statement.csv").with(sameOriginCsrf())
                        .cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isNotFound());
    }

    private Set<MatchDecision> decisions(byte[] statement) {
        return match(statement).stream().map(MatchResult::decision).collect(Collectors.toSet());
    }

    private List<MatchResult> match(byte[] statement) {
        ParsedStatement parsed = BankStatementParser.parse(new ByteArrayInputStream(statement));
        return PaymentMatcher.matchAll(parsed.rows(), contextFactory.build());
    }
}
