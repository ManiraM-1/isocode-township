package com.isocode.township.committee;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitteeArithmeticTest {

    @Test
    void reconciliationBalancesWhenEveryLedgerLineIsCounted() {
        LedgerTotals totals = new LedgerTotals(Money.of("10000.00"), Money.of("50000.00"), Money.of("120.40"),
                Money.of("20.40"), Money.of("45100.00"), Money.of("15000.00"));

        assertThat(totals.totalDue()).isEqualTo(Money.of("60100.00"));
        assertThat(totals.expectedClosing()).isEqualTo(Money.of("15000.00"));
        assertThat(totals.balanced()).isTrue();
        assertThat(totals.collectionRate()).isEqualTo(new BigDecimal("75.0"));
    }

    @Test
    void anUncountedLedgerLineShowsAsUnbalanced() {
        LedgerTotals totals = new LedgerTotals(Money.of("0.00"), Money.of("100.00"), Money.ZERO, Money.ZERO,
                Money.ZERO, Money.of("100.01"));

        assertThat(totals.balanced()).isFalse();
    }

    @Test
    void collectionRateIsEmptyWhenNothingWasDue() {
        LedgerTotals totals = new LedgerTotals(Money.of("-500.00"), Money.ZERO, Money.ZERO, Money.ZERO,
                Money.ZERO, Money.of("-500.00"));

        assertThat(totals.collectionRate()).isNull();
    }

    @Test
    void sharesAreRoundedHalfUpToOneDecimal() {
        assertThat(Percentages.share(2, 3)).isEqualTo(new BigDecimal("66.7"));
        assertThat(Percentages.share(1, 8)).isEqualTo(new BigDecimal("12.5"));
        assertThat(Percentages.share(1, 0)).isNull();
    }

    @Test
    void medianOfAnOddCountIsTheMiddleValue() {
        assertThat(Medians.minutes(List.of(Duration.ofMinutes(90), Duration.ofMinutes(10), Duration.ofMinutes(30))))
                .isEqualTo(30L);
    }

    @Test
    void medianOfAnEvenCountIsTheMeanOfTheMiddleTwoRoundedToTheMinute() {
        assertThat(Medians.minutes(List.of(Duration.ofMinutes(10), Duration.ofMinutes(15), Duration.ofSeconds(1),
                Duration.ofHours(5)))).isEqualTo(13L);
        assertThat(Medians.minutes(List.of())).isNull();
    }

    @Test
    void autoPostShareCountsOnlyRowsThatBroughtNewMoney() {
        ImportTotals totals = new ImportTotals(2, 6, 1, 3, 2, 1, 0);

        assertThat(totals.newPaymentRows()).isEqualTo(12);
        assertThat(totals.autoPostShare()).isEqualTo(new BigDecimal("50.0"));
        assertThat(new ImportTotals(0, 0, 0, 0, 0, 0, 0).autoPostShare()).isNull();
    }
}
