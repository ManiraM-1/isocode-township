package com.isocode.township.committee;

import com.isocode.township.common.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DuesAgeing {

    private final LocalDate asOf;
    private final List<FlatBalance> flatsOwingOrInCredit;

    public DuesAgeing(LocalDate asOf, List<FlatBalance> balances) {
        this.asOf = asOf;
        this.flatsOwingOrInCredit = balances.stream().filter(flat -> flat.balance().signum() != 0).toList();
    }

    public LocalDate asOf() {
        return asOf;
    }

    public List<BucketTotal> buckets() {
        return Arrays.stream(AgeingBucket.values()).map(this::totalFor).toList();
    }

    public BigDecimal total() {
        return sum(flatsOwingOrInCredit);
    }

    public List<AgedFlat> flatsIn(AgeingBucket bucket) {
        return inBucket(bucket).stream()
                .map(this::aged)
                .sorted(Comparator.comparingLong(AgedFlat::daysOverdue).reversed()
                        .thenComparing(AgedFlat::amount, Comparator.reverseOrder())
                        .thenComparing(AgedFlat::flatCode))
                .toList();
    }

    private AgedFlat aged(FlatBalance flat) {
        boolean owes = flat.balance().signum() > 0;
        return new AgedFlat(flat.flatId(), flat.flatCode(), Money.rounded(flat.balance()),
                owes ? flat.oldestUnpaidDue() : null,
                owes ? AgeingBucket.daysOverdue(flat.oldestUnpaidDue(), asOf) : 0);
    }

    private BucketTotal totalFor(AgeingBucket bucket) {
        List<FlatBalance> flats = inBucket(bucket);
        return new BucketTotal(bucket, flats.size(), sum(flats));
    }

    private List<FlatBalance> inBucket(AgeingBucket bucket) {
        return flatsOwingOrInCredit.stream()
                .filter(flat -> AgeingBucket.of(flat.balance(), flat.oldestUnpaidDue(), asOf) == bucket)
                .toList();
    }

    private static BigDecimal sum(List<FlatBalance> flats) {
        return Money.rounded(flats.stream().map(FlatBalance::balance).reduce(Money.ZERO, BigDecimal::add));
    }

    public record BucketTotal(AgeingBucket bucket, int flats, BigDecimal amount) {
    }

    public record AgedFlat(Long flatId, String flatCode, BigDecimal amount, LocalDate oldestUnpaidDue,
                           long daysOverdue) {
    }
}
