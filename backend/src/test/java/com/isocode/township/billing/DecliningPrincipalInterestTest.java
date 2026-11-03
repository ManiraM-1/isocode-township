package com.isocode.township.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecliningPrincipalInterestTest {

    private static final BigDecimal TWENTY_ONE_PCT = new BigDecimal("21.00");
    private static final BigDecimal SWATI_BILL = Money.of("6370.00");
    private static final LocalDate SEP_10 = LocalDate.of(2026, 9, 10);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    @Test
    void withoutPaymentsItMatchesTheSimpleFormula() {
        LocalDate due = LocalDate.of(2026, 9, 17);

        assertThat(interest(SWATI_BILL, List.of(), due, due.plusDays(47))).isEqualTo(new BigDecimal("172.25"));
    }

    @Test
    void partPaymentMidPeriodLowersThePrincipalFromThatDayOn() {
        List<PrincipalReduction> paidOnSep20 = List.of(new PrincipalReduction(LocalDate.of(2026, 9, 20), Money.of("3000.00")));

        assertThat(interest(SWATI_BILL, paidOnSep20, SEP_10, SEP_30)).isEqualTo(new BigDecimal("54.31"));
    }

    @Test
    void fullPaymentStopsInterestOnThePaymentDay() {
        List<PrincipalReduction> paidInFullOnSep20 = List.of(new PrincipalReduction(LocalDate.of(2026, 9, 20), SWATI_BILL));

        assertThat(interest(SWATI_BILL, paidInFullOnSep20, SEP_10, SEP_30)).isEqualTo(new BigDecimal("32.98"));
    }

    @Test
    void paymentOnTheFirstLateDayCountsForTheWholePeriod() {
        List<PrincipalReduction> paidOnSep11 = List.of(new PrincipalReduction(LocalDate.of(2026, 9, 11), Money.of("3000.00")));

        assertThat(interest(SWATI_BILL, paidOnSep11, SEP_10, SEP_30)).isEqualTo(new BigDecimal("38.78"));
    }

    @Test
    void paymentBeforeTheDueDateLeavesInterestOnlyOnTheRemainder() {
        LocalDate due = LocalDate.of(2026, 10, 16);
        List<PrincipalReduction> aamirPaidEarly = List.of(new PrincipalReduction(LocalDate.of(2026, 10, 14), Money.of("5000.00")));

        assertThat(interest(Money.of("10660.00"), aamirPaidEarly, due, LocalDate.of(2026, 10, 31)))
                .isEqualTo(new BigDecimal("48.85"));
    }

    @Test
    void paymentsAfterThePeriodDoNotChangeIt() {
        List<PrincipalReduction> paidInOctober = List.of(new PrincipalReduction(LocalDate.of(2026, 10, 5), SWATI_BILL));

        assertThat(interest(SWATI_BILL, paidInOctober, SEP_10, SEP_30)).isEqualTo(new BigDecimal("73.30"));
    }

    @Test
    void severalPaymentsInAnyOrderAreAppliedByDate() {
        List<PrincipalReduction> outOfOrder = List.of(
                new PrincipalReduction(LocalDate.of(2026, 9, 25), Money.of("1000.00")),
                new PrincipalReduction(LocalDate.of(2026, 9, 20), Money.of("2000.00")),
                new PrincipalReduction(LocalDate.of(2026, 9, 20), Money.of("1000.00")));

        BigDecimal expected = Money.rounded(new BigDecimal(6370 * 9 + 3370 * 5 + 2370 * 6)
                .multiply(TWENTY_ONE_PCT)
                .divide(new BigDecimal("36500"), 2, Money.ROUNDING));
        assertThat(interest(SWATI_BILL, outOfOrder, SEP_10, SEP_30)).isEqualTo(expected);
    }

    @Test
    void nothingIsChargedWhenThePeriodIsEmpty() {
        assertThat(interest(SWATI_BILL, List.of(), SEP_30, SEP_30)).isEqualTo(Money.ZERO);
        assertThat(interest(SWATI_BILL, List.of(), SEP_30, SEP_10)).isEqualTo(Money.ZERO);
    }

    private static BigDecimal interest(BigDecimal bill, List<PrincipalReduction> reductions,
                                       LocalDate fromExclusive, LocalDate toInclusive) {
        return LateFeeCalculator.interestOnDecliningPrincipal(bill, reductions, TWENTY_ONE_PCT, fromExclusive, toInclusive);
    }
}
