package com.isocode.township.committee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ComplaintMetrics(
        int opened,
        int resolved,
        Long medianFirstResponseMinutes,
        Long medianResolutionMinutes,
        int firstResponseMeasured,
        int firstResponseBreached,
        BigDecimal firstResponseBreachShare,
        int reopened,
        BigDecimal reopenRate,
        int ratings,
        BigDecimal averageRating) {

    public static ComplaintMetrics compute(List<ComplaintFacts> complaints, Instant windowStart, Instant windowEnd,
                                           Instant now) {
        List<ComplaintFacts> opened = complaints.stream()
                .filter(complaint -> within(complaint.createdAt(), windowStart, windowEnd)).toList();
        List<ComplaintFacts> resolved = complaints.stream()
                .filter(complaint -> within(complaint.resolvedAt(), windowStart, windowEnd)).toList();
        List<Integer> ratings = complaints.stream()
                .filter(complaint -> within(complaint.closedAt(), windowStart, windowEnd) && complaint.rating() != null)
                .map(ComplaintFacts::rating)
                .toList();
        List<ComplaintFacts> measured = opened.stream()
                .filter(complaint -> complaint.firstResponseAt() != null || complaint.firstResponseDueAt().isBefore(now))
                .toList();
        int breached = (int) measured.stream().filter(ComplaintMetrics::missedFirstResponseTarget).count();
        int reopened = (int) opened.stream().filter(complaint -> complaint.reopenCount() > 0).count();

        return new ComplaintMetrics(opened.size(), resolved.size(),
                Medians.minutes(opened.stream()
                        .filter(complaint -> complaint.firstResponseAt() != null)
                        .map(complaint -> Duration.between(complaint.createdAt(), complaint.firstResponseAt()))
                        .toList()),
                Medians.minutes(resolved.stream()
                        .map(complaint -> Duration.between(complaint.createdAt(), complaint.resolvedAt()))
                        .toList()),
                measured.size(), breached, Percentages.share(breached, measured.size()),
                reopened, Percentages.share(reopened, opened.size()),
                ratings.size(), average(ratings));
    }

    private static boolean missedFirstResponseTarget(ComplaintFacts complaint) {
        return complaint.firstResponseAt() == null || complaint.firstResponseAt().isAfter(complaint.firstResponseDueAt());
    }

    private static boolean within(Instant moment, Instant start, Instant end) {
        return moment != null && !moment.isBefore(start) && moment.isBefore(end);
    }

    private static BigDecimal average(List<Integer> ratings) {
        if (ratings.isEmpty()) {
            return null;
        }
        int sum = ratings.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(ratings.size()), 1, RoundingMode.HALF_UP);
    }
}
