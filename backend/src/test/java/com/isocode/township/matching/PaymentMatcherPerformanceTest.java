package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PaymentMatcherPerformanceTest {

    private static final int FLATS = 2_400;
    private static final int ROWS = 3_000;
    private static final int TOWERS = 28;
    private static final Duration BUDGET = Duration.ofSeconds(3);

    @Test
    void threeThousandRowsAgainstTwentyFourHundredFlatsMatchInUnderThreeSeconds() {
        Random random = new Random(7);
        List<String> codes = flatCodes();
        MatchContext.Builder builder = MatchContext.builder();
        for (int index = 0; index < FLATS; index++) {
            long flatId = index + 1L;
            builder.flat(flatId, codes.get(index));
            builder.billRemaining(flatId, YearMonth.of(2026, 11), billAmount(random));
            if (index % 3 == 0) {
                builder.knownUpiId("resident" + index + "@okaxis", flatId);
            }
        }
        MatchContext context = builder.build();
        List<StatementRow> rows = rows(random, codes);

        long started = System.nanoTime();
        List<MatchResult> results = PaymentMatcher.matchAll(rows, context);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(results).hasSize(ROWS);
        assertThat(results).extracting(MatchResult::decision)
                .contains(MatchDecision.AUTO_POSTED, MatchDecision.SUGGESTED, MatchDecision.UNMATCHED);
        assertThat(elapsed).isLessThan(BUDGET);
    }

    private static List<String> flatCodes() {
        List<String> codes = new ArrayList<>();
        for (int floor = 1; codes.size() < FLATS; floor++) {
            for (int tower = 1; tower <= TOWERS && codes.size() < FLATS; tower++) {
                for (int unit = 1; unit <= 4 && codes.size() < FLATS; unit++) {
                    codes.add("T%02d-%02d%02d".formatted(tower, floor, unit));
                }
            }
        }
        return codes;
    }

    private static List<StatementRow> rows(Random random, List<String> codes) {
        List<StatementRow> rows = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            int flat = random.nextInt(FLATS);
            String code = codes.get(flat);
            String utr = "6%011d".formatted(index);
            String description = switch (index % 5) {
                case 0 -> "UPI/CR/" + utr + "/RESIDENT/resident" + flat + "@okaxis/" + code + " NOV";
                case 1 -> "NEFT CR-HDFC0001234-" + code.replace("-", "/") + " MAINTENANCE";
                case 2 -> "IMPS/P2A/" + utr + "/TOWER " + Integer.parseInt(code.substring(1, 3)) + " FLAT "
                        + code.substring(4) + "/MAINT";
                case 3 -> "UPI/CR/" + utr + "/PAYER/payer" + index + "@ybl/MAINTENANCE";
                default -> "NEFT CR-SBIN0004321-VENDOR " + index;
            };
            rows.add(MatchFixtures.credit(index + 2, description, utr, billAmount(random).toPlainString()));
        }
        return rows;
    }

    private static BigDecimal billAmount(Random random) {
        return Money.rounded(new BigDecimal(650 + 5 * random.nextInt(231)).multiply(new BigDecimal("6.50")));
    }
}
