package com.isocode.township.matching;

import static com.isocode.township.FixedClockConfiguration.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.payment.DemoHistorySeeder;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class SampleStatementTest {

    private static final Path SAMPLE = Path.of("..", "samples", "bank-statement-demo.csv");

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private DemoHistorySeeder historySeeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ParsedStatement statement;
    private Map<Integer, StatementRow> rows;

    @BeforeEach
    void setUp() throws IOException {
        coreSeeder.seedIfEmpty();
        historySeeder.seed();
        try (InputStream input = Files.newInputStream(SAMPLE)) {
            statement = BankStatementParser.parse(input);
        }
        rows = statement.rows().stream().collect(Collectors.toMap(StatementRow::rowNo, Function.identity()));
    }

    @Test
    void fileStartsWithAByteOrderMarkAndParsesWithOneBadRow() throws IOException {
        byte[] start = Files.readAllBytes(SAMPLE);

        assertThat(new byte[] {start[0], start[1], start[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(statement.totalRows()).isEqualTo(19);
        assertThat(statement.errors()).extracting(RowError::rowNo).containsExactly(20);
        assertThat(statement.rows()).filteredOn(row -> row.kind() == RowKind.NON_CREDIT)
                .extracting(StatementRow::rowNo).containsExactly(2, 5);
        assertThat(rows.get(2).debit()).isEqualTo(new BigDecimal("125640.00"));
    }

    @Test
    void everyCreditIsDatedOnOrBeforeTheFixedTestDate() {
        assertThat(statement.rows()).allSatisfy(row -> assertThat(row.txnDate()).isBeforeOrEqualTo(TODAY));
    }

    @Test
    void gatewaySettlementRowsUseSeededReferences() {
        assertThat(rows.get(14).gatewayRef()).contains("PGTEST-JD9EP9VAU8CP");
        assertThat(seededGatewayAmount("PGTEST-JD9EP9VAU8CP")).isEqualByComparingTo(rows.get(14).credit());

        assertThat(rows.get(15).gatewayRef()).contains("PGTEST-SLAMTBLQNZXA");
        assertThat(seededGatewayAmount("PGTEST-SLAMTBLQNZXA")).isNotEqualByComparingTo(rows.get(15).credit());
    }

    @Test
    void duplicateRowsAreAnAlreadyPostedUtrAndAnExactRepeat() {
        assertThat(rows.get(16).utr()).contains("699747290912");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payment WHERE utr = ?", Integer.class,
                "699747290912")).isEqualTo(1);
        assertThat(rows.get(19).rowHash()).isEqualTo(rows.get(18).rowHash());
    }

    @Test
    void amountSignalsMatchOneBillsRemainingPrincipalOnTheIntendedFlats() {
        assertThat(flatsWithBillRemaining(rows.get(3).credit())).containsExactly("T21-1601");
        assertThat(flatsWithBillRemaining(rows.get(4).credit())).containsExactly("T12-0408");
        assertThat(flatsWithBillRemaining(rows.get(6).credit())).containsExactly("T26-1203");
        assertThat(flatsWithBillRemaining(rows.get(9).credit())).containsExactly("T12-1108");
        assertThat(flatsWithBillRemaining(rows.get(10).credit())).containsExactly("T21-0104", "T21-1402");
    }

    @Test
    void rowsMeantToHaveNoAmountSignalMatchNoBillAndNoFlatTotal() {
        for (int rowNo : List.of(7, 8, 11, 12, 13, 17, 18)) {
            BigDecimal amount = rows.get(rowNo).credit();
            assertThat(flatsWithBillRemaining(amount)).as("row %d bill", rowNo).isEmpty();
            assertThat(flatsWithOutstanding(amount)).as("row %d outstanding", rowNo).isEmpty();
        }
    }

    @Test
    void upiIdsAreExtractedForTheRowsThatCarryThem() {
        assertThat(rows.get(3).upiId()).contains("aamir.s@okicici");
        assertThat(rows.get(8).upiId()).contains("dhole.home@okaxis");
        assertThat(rows.get(11).upiId()).contains("aamir.s@okicici");
        assertThat(rows.get(12).upiId()).contains("kale.family@okaxis");
        assertThat(rows.get(17).upiId()).contains("nene.home@okicici");
    }

    @Test
    void everyFlatCodeTheFileMentionsExistsInTheSeed() {
        List<String> mentioned = List.of("T21-1601", "T12-0408", "T26-1203", "T15-0702", "T07-1203", "T21-0303",
                "T07-1401", "T07-1407", "T15-1605", "T26-2007", "T26-0807");

        assertThat(jdbcTemplate.queryForList("SELECT code FROM flat", String.class)).containsAll(mentioned);
    }

    @Test
    void overpaymentRowIsMoreThanTheFlatOwes() {
        assertThat(outstandingOf("T15-1605")).isLessThan(rows.get(17).credit());
    }

    private BigDecimal seededGatewayAmount(String gatewayRef) {
        return jdbcTemplate.queryForObject("""
                SELECT p.amount FROM payment p JOIN payment_intent i ON i.gateway_ref = p.gateway_ref
                WHERE p.gateway_ref = ? AND i.status = 'SUCCEEDED'
                """, BigDecimal.class, gatewayRef);
    }

    private List<String> flatsWithBillRemaining(BigDecimal amount) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT f.code FROM bill b JOIN flat f ON f.id = b.flat_id
                WHERE b.amount_paid < b.amount AND b.amount - b.amount_paid = ? ORDER BY f.code
                """, String.class, amount);
    }

    private List<String> flatsWithOutstanding(BigDecimal amount) {
        return jdbcTemplate.queryForList("""
                SELECT f.code FROM flat f JOIN ledger_entry e ON e.flat_id = f.id
                GROUP BY f.code HAVING sum(e.debit - e.credit) = ?
                """, String.class, amount);
    }

    private BigDecimal outstandingOf(String flatCode) {
        return jdbcTemplate.queryForObject("""
                SELECT sum(e.debit - e.credit) FROM ledger_entry e JOIN flat f ON f.id = e.flat_id WHERE f.code = ?
                """, BigDecimal.class, flatCode);
    }
}
