package com.isocode.township.committee.dto;

import com.isocode.township.committee.CommitteeFactsQuery.CategoryCount;
import com.isocode.township.committee.CommitteeFactsQuery.LastImport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record CommitteeOverview(
        YearMonth month,
        LocalDate asOf,
        Instant generatedAt,
        Collections collections,
        Reconciliation reconciliation,
        Ageing ageing,
        Matching matching,
        Complaints complaints) {

    public record Collections(BigDecimal billed, BigDecimal lateFeesCharged, BigDecimal lateFeeReversals,
                              BigDecimal collected, BigDecimal totalDue, BigDecimal collectionRate, int billsDue,
                              int billsPaidOnTime, BigDecimal onTimeRate, BigDecimal onTimeTarget) {
    }

    public record Reconciliation(BigDecimal openingOutstanding, BigDecimal billed, BigDecimal lateFeesCharged,
                                 BigDecimal lateFeeReversals, BigDecimal collections,
                                 BigDecimal expectedClosingOutstanding, BigDecimal closingOutstanding,
                                 boolean balanced) {
    }

    public record Ageing(LocalDate asOf, List<AgeingLine> buckets, BigDecimal total,
                         boolean matchesClosingOutstanding) {
    }

    public record AgeingLine(String bucket, String label, int flats, BigDecimal amount) {
    }

    public record Matching(LastImport lastImport, int importsThisMonth, int newPaymentRows, int autoPosted,
                           BigDecimal autoPostShare, int queueSize, Instant oldestQueuedAt,
                           Long oldestQueuedDays) {
    }

    public record Complaints(int opened, int resolved, Long medianFirstResponseMinutes,
                             Long medianResolutionMinutes, int firstResponseMeasured, int firstResponseBreached,
                             BigDecimal firstResponseBreachShare, int reopened, BigDecimal reopenRate,
                             BigDecimal reopenTarget, int ratings, BigDecimal averageRating,
                             List<CategoryCount> openByCategory, int activeSafetyEscalations,
                             int unacknowledgedEscalations) {
    }
}
