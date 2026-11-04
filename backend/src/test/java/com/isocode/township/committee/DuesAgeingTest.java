package com.isocode.township.committee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.committee.DuesAgeing.AgedFlat;
import com.isocode.township.committee.DuesAgeing.BucketTotal;
import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DuesAgeingTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 31);

    @ParameterizedTest
    @CsvSource({
            "2026-11-10, NOT_YET_DUE",
            "2026-10-31, NOT_YET_DUE",
            "2026-10-30, DAYS_1_30",
            "2026-10-01, DAYS_1_30",
            "2026-09-30, DAYS_31_60",
            "2026-09-01, DAYS_31_60",
            "2026-08-31, DAYS_61_90",
            "2026-08-02, DAYS_61_90",
            "2026-08-01, OVER_90"
    })
    void bucketFollowsTheOldestUnpaidDueDate(LocalDate oldestUnpaidDue, AgeingBucket expected) {
        assertThat(AgeingBucket.of(Money.of("100"), oldestUnpaidDue, AS_OF)).isEqualTo(expected);
    }

    @Test
    void aFlatInCreditIsItsOwnBucketWhateverItsDueDates() {
        assertThat(AgeingBucket.of(Money.of("-50"), LocalDate.of(2026, 1, 10), AS_OF)).isEqualTo(AgeingBucket.IN_CREDIT);
    }

    @Test
    void owingWithoutAnUnpaidItemIsNotYetDue() {
        assertThat(AgeingBucket.of(Money.of("10"), null, AS_OF)).isEqualTo(AgeingBucket.NOT_YET_DUE);
    }

    @Test
    void bucketsCountFlatsAndAddUpToTheTotalIncludingCredit() {
        DuesAgeing ageing = new DuesAgeing(AS_OF, List.of(
                flat(1, "T07-0101", "7475.00", "2026-11-10"),
                flat(2, "T07-0102", "6370.00", "2026-10-10"),
                flat(3, "T07-0103", "5000.00", "2026-10-20"),
                flat(4, "T07-0104", "12740.00", "2026-09-10"),
                flat(5, "T07-0105", "19000.00", "2026-07-10"),
                flat(6, "T07-0106", "-1200.50", null),
                flat(7, "T07-0107", "0.00", "2026-01-10")));

        assertThat(ageing.buckets()).extracting(BucketTotal::bucket, BucketTotal::flats, BucketTotal::amount)
                .containsExactly(
                        tuple(AgeingBucket.NOT_YET_DUE, 1, Money.of("7475.00")),
                        tuple(AgeingBucket.DAYS_1_30, 2, Money.of("11370.00")),
                        tuple(AgeingBucket.DAYS_31_60, 1, Money.of("12740.00")),
                        tuple(AgeingBucket.DAYS_61_90, 0, Money.of("0.00")),
                        tuple(AgeingBucket.OVER_90, 1, Money.of("19000.00")),
                        tuple(AgeingBucket.IN_CREDIT, 1, Money.of("-1200.50")));
        assertThat(ageing.total()).isEqualTo(Money.of("49384.50"));
        assertThat(ageing.buckets().stream().map(BucketTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(ageing.total());
    }

    @Test
    void flatsInABucketAreListedMostOverdueFirst() {
        DuesAgeing ageing = new DuesAgeing(AS_OF, List.of(
                flat(2, "T07-0102", "6370.00", "2026-10-10"),
                flat(3, "T07-0103", "5000.00", "2026-10-20"),
                flat(8, "T03-0101", "9000.00", "2026-10-10")));

        assertThat(ageing.flatsIn(AgeingBucket.DAYS_1_30))
                .extracting(AgedFlat::flatCode, AgedFlat::amount, AgedFlat::daysOverdue)
                .containsExactly(
                        tuple("T03-0101", Money.of("9000.00"), 21L),
                        tuple("T07-0102", Money.of("6370.00"), 21L),
                        tuple("T07-0103", Money.of("5000.00"), 11L));
    }

    @Test
    void aFlatInCreditShowsNoDueDate() {
        DuesAgeing ageing = new DuesAgeing(AS_OF, List.of(flat(6, "T07-0106", "-1200.50", "2026-10-10")));

        assertThat(ageing.flatsIn(AgeingBucket.IN_CREDIT))
                .containsExactly(new AgedFlat(6L, "T07-0106", Money.of("-1200.50"), null, 0));
    }

    private static FlatBalance flat(long id, String code, String balance, String oldestDue) {
        return new FlatBalance(id, code, Money.of(balance), oldestDue == null ? null : LocalDate.parse(oldestDue));
    }
}
