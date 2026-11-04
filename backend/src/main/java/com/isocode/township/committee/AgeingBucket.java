package com.isocode.township.committee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

public enum AgeingBucket {
    NOT_YET_DUE("not-yet-due", "Not yet due"),
    DAYS_1_30("1-30", "1–30 days"),
    DAYS_31_60("31-60", "31–60 days"),
    DAYS_61_90("61-90", "61–90 days"),
    OVER_90("90-plus", "Over 90 days"),
    IN_CREDIT("in-credit", "In credit");

    private final String slug;
    private final String label;

    AgeingBucket(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public static AgeingBucket of(BigDecimal balance, LocalDate oldestUnpaidDue, LocalDate asOf) {
        if (balance.signum() < 0) {
            return IN_CREDIT;
        }
        long daysOverdue = daysOverdue(oldestUnpaidDue, asOf);
        if (daysOverdue == 0) {
            return NOT_YET_DUE;
        }
        if (daysOverdue <= 30) {
            return DAYS_1_30;
        }
        if (daysOverdue <= 60) {
            return DAYS_31_60;
        }
        if (daysOverdue <= 90) {
            return DAYS_61_90;
        }
        return OVER_90;
    }

    public static long daysOverdue(LocalDate oldestUnpaidDue, LocalDate asOf) {
        return oldestUnpaidDue == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(oldestUnpaidDue, asOf));
    }

    public static Optional<AgeingBucket> fromSlug(String slug) {
        return Arrays.stream(values()).filter(bucket -> bucket.slug.equals(slug)).findFirst();
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }
}
