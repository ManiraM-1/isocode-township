package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.isocode.township.matching.MatchResult.DuplicateOf;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SampleStatementMatchingTest {

    private Map<Integer, MatchResult> results;

    @BeforeEach
    void matchTheSample() throws IOException {
        ParsedStatement statement;
        try (InputStream input = Files.newInputStream(SampleStatementTest.SAMPLE)) {
            statement = BankStatementParser.parse(input);
        }
        List<MatchResult> matched = PaymentMatcher.matchAll(statement.rows(), MatchFixtures.seedLikeContext().build());
        results = matched.stream().collect(Collectors.toMap(MatchResult::rowNo, Function.identity()));
    }

    @Test
    void everyRowGetsTheDecisionListedInTheSamplesReadme() {
        assertThat(results).extractingFromEntries(entry -> Map.entry(entry.getKey(), entry.getValue().decision()))
                .containsExactlyInAnyOrder(
                        Map.entry(2, MatchDecision.NON_CREDIT),
                        Map.entry(3, MatchDecision.AUTO_POSTED),
                        Map.entry(4, MatchDecision.AUTO_POSTED),
                        Map.entry(5, MatchDecision.NON_CREDIT),
                        Map.entry(6, MatchDecision.AUTO_POSTED),
                        Map.entry(7, MatchDecision.SUGGESTED),
                        Map.entry(8, MatchDecision.SUGGESTED),
                        Map.entry(9, MatchDecision.SUGGESTED),
                        Map.entry(10, MatchDecision.UNMATCHED),
                        Map.entry(11, MatchDecision.SUGGESTED),
                        Map.entry(12, MatchDecision.CONFLICT),
                        Map.entry(13, MatchDecision.AUTO_POSTED),
                        Map.entry(14, MatchDecision.CONFLICT),
                        Map.entry(15, MatchDecision.RECONCILED_GATEWAY),
                        Map.entry(16, MatchDecision.CONFLICT),
                        Map.entry(17, MatchDecision.DUPLICATE),
                        Map.entry(18, MatchDecision.AUTO_POSTED),
                        Map.entry(19, MatchDecision.SUGGESTED),
                        Map.entry(20, MatchDecision.POSSIBLE_DUPLICATE),
                        Map.entry(21, MatchDecision.UNMATCHED),
                        Map.entry(22, MatchDecision.DUPLICATE));
    }

    @Test
    void countsPerDecisionMatchTheReadmeTotals() {
        Map<MatchDecision, Long> counts = results.values().stream()
                .collect(Collectors.groupingBy(MatchResult::decision, Collectors.counting()));

        assertThat(counts).containsOnly(
                entry(MatchDecision.AUTO_POSTED, 5L),
                entry(MatchDecision.RECONCILED_GATEWAY, 1L),
                entry(MatchDecision.SUGGESTED, 5L),
                entry(MatchDecision.CONFLICT, 3L),
                entry(MatchDecision.UNMATCHED, 2L),
                entry(MatchDecision.DUPLICATE, 2L),
                entry(MatchDecision.POSSIBLE_DUPLICATE, 1L),
                entry(MatchDecision.NON_CREDIT, 2L));
    }

    @Test
    void repeatsPointAtTheRowTheyRepeat() {
        assertThat(results.get(20).duplicateOf()).contains(DuplicateOf.thisImport(19));
        assertThat(results.get(22).duplicateOf()).contains(DuplicateOf.thisImport(21));
    }

    @Test
    void theIgnoredAmountIsExplainedOnTheAutoPostedRow() {
        assertThat(results.get(13).reasons()).contains("Amount ₹4,070.00 also equals the remaining amount of "
                + "T12-1108's October 2026 bill; ignored because reference and UPI ID agree");
    }

    @Test
    void onlyAutoPostedRowsWouldCreateAPayment() {
        assertThat(results.values()).filteredOn(result -> result.decision().isPostedAutomatically())
                .extracting(MatchResult::rowNo).containsExactlyInAnyOrder(3, 4, 6, 13, 18);
    }
}
