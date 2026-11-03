package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LateFeeCalculatorTest {

    private static final BigDecimal TWENTY_ONE_PCT = new BigDecimal("21.00");
    private static final LocalDate DUE = LocalDate.of(2026, 9, 17);

    @Test
    void swatiFortySevenDaysOverdueOwesExactly172Rupees25Paise() {
        BigDecimal interest = LateFeeCalculator.interest(
                Money.of("6370.00"), TWENTY_ONE_PCT, DUE, DUE.plusDays(47));

        assertThat(interest).isEqualTo(new BigDecimal("172.25"));
    }

    @Test
    void noInterestOnOrBeforeTheDueDate() {
        assertThat(LateFeeCalculator.interest(Money.of("6370.00"), TWENTY_ONE_PCT, DUE, DUE)).isEqualTo(Money.ZERO);
        assertThat(LateFeeCalculator.interest(Money.of("6370.00"), TWENTY_ONE_PCT, DUE, DUE.minusDays(5)))
                .isEqualTo(Money.ZERO);
    }

    @Test
    void oneDayLateChargesOneDayOfInterest() {
        assertThat(LateFeeCalculator.interest(Money.of("6370.00"), TWENTY_ONE_PCT, DUE, DUE.plusDays(1)))
                .isEqualTo(new BigDecimal("3.66"));
    }

    @Test
    void noInterestWhenNothingIsOutstandingOrTheRateIsZero() {
        assertThat(LateFeeCalculator.interest(Money.ZERO, TWENTY_ONE_PCT, DUE, DUE.plusDays(30))).isEqualTo(Money.ZERO);
        assertThat(LateFeeCalculator.interest(Money.of("6370.00"), BigDecimal.ZERO, DUE, DUE.plusDays(30)))
                .isEqualTo(Money.ZERO);
    }

    @Test
    void partialPrincipalChargesOnlyOnWhatIsUnpaid() {
        assertThat(LateFeeCalculator.interest(Money.of("5660.00"), TWENTY_ONE_PCT, DUE, DUE.plusDays(18)))
                .isEqualTo(new BigDecimal("58.62"));
    }

    @Test
    void interestStartsAfterTheDueDateOrAfterTheLastChargedDay() {
        assertThat(LateFeeCalculator.interestStartsAfter(DUE, null)).isEqualTo(DUE);
        assertThat(LateFeeCalculator.interestStartsAfter(DUE, LocalDate.of(2026, 9, 30))).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(LateFeeCalculator.interestStartsAfter(DUE, LocalDate.of(2026, 9, 1))).isEqualTo(DUE);
    }

    @Test
    void consecutiveChargesAddUpToOneChargeOverTheWholePeriodWithinAPaisaPerCharge() {
        BigDecimal principal = Money.of("6370.00");
        LocalDate monthEnd = LocalDate.of(2026, 9, 30);
        LocalDate nextMonthEnd = LocalDate.of(2026, 10, 31);

        BigDecimal split = LateFeeCalculator.interest(principal, TWENTY_ONE_PCT, DUE, monthEnd)
                .add(LateFeeCalculator.interest(principal, TWENTY_ONE_PCT, monthEnd, nextMonthEnd));
        BigDecimal whole = LateFeeCalculator.interest(principal, TWENTY_ONE_PCT, DUE, nextMonthEnd);

        assertThat(split.subtract(whole).abs()).isLessThanOrEqualTo(new BigDecimal("0.01"));
    }
}
