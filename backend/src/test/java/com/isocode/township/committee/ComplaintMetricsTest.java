package com.isocode.township.committee;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplaintMetricsTest {

    private static final Instant OCT_START = Instant.parse("2026-09-30T18:30:00Z");
    private static final Instant NOV_START = Instant.parse("2026-10-31T18:30:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");

    @Test
    void monthFiguresComeFromTheComplaintsOpenedResolvedAndClosedInIt() {
        List<ComplaintFacts> complaints = List.of(
                complaint("2026-10-02T04:30:00Z", "2026-10-02T04:40:00Z", "2026-10-02T04:45:00Z",
                        "2026-10-02T08:30:00Z", "2026-10-03T08:30:00Z", 0, 5),
                complaint("2026-10-05T04:30:00Z", "2026-10-05T07:30:00Z", "2026-10-05T06:30:00Z",
                        "2026-10-06T04:30:00Z", "2026-10-07T04:30:00Z", 1, 2),
                complaint("2026-10-10T04:30:00Z", "2026-10-10T05:00:00Z", "2026-10-10T12:30:00Z",
                        "2026-11-01T04:30:00Z", null, 0, null),
                complaint("2026-10-30T04:30:00Z", null, "2026-10-31T04:30:00Z", null, null, 0, null),
                complaint("2026-11-03T04:00:00Z", null, "2026-11-03T12:00:00Z", null, null, 0, null),
                complaint("2026-09-20T04:30:00Z", "2026-09-20T05:30:00Z", "2026-09-20T12:30:00Z",
                        "2026-10-04T04:30:00Z", "2026-10-20T04:30:00Z", 0, 4),
                complaint("2026-10-31T18:00:00Z", null, "2026-11-04T04:30:00Z", null, null, 0, null));

        ComplaintMetrics metrics = ComplaintMetrics.compute(complaints, OCT_START, NOV_START, NOW);

        assertThat(metrics.opened()).isEqualTo(5);
        assertThat(metrics.resolved()).isEqualTo(3);
        assertThat(metrics.medianFirstResponseMinutes()).isEqualTo(30L);
        assertThat(metrics.medianResolutionMinutes()).isEqualTo(1440L);
        assertThat(metrics.firstResponseMeasured()).isEqualTo(4);
        assertThat(metrics.firstResponseBreached()).isEqualTo(2);
        assertThat(metrics.firstResponseBreachShare()).isEqualTo(new BigDecimal("50.0"));
        assertThat(metrics.reopened()).isEqualTo(1);
        assertThat(metrics.reopenRate()).isEqualTo(new BigDecimal("20.0"));
        assertThat(metrics.ratings()).isEqualTo(3);
        assertThat(metrics.averageRating()).isEqualTo(new BigDecimal("3.7"));
    }

    @Test
    void aMonthWithoutComplaintsHasNoRates() {
        ComplaintMetrics metrics = ComplaintMetrics.compute(List.of(), OCT_START, NOV_START, NOW);

        assertThat(metrics.opened()).isZero();
        assertThat(metrics.medianFirstResponseMinutes()).isNull();
        assertThat(metrics.medianResolutionMinutes()).isNull();
        assertThat(metrics.firstResponseBreachShare()).isNull();
        assertThat(metrics.reopenRate()).isNull();
        assertThat(metrics.averageRating()).isNull();
    }

    private static ComplaintFacts complaint(String created, String firstResponse, String firstResponseDue,
                                            String resolved, String closed, int reopens, Integer rating) {
        return new ComplaintFacts(Instant.parse(created), instant(firstResponse), Instant.parse(firstResponseDue),
                instant(resolved), instant(closed), reopens, rating);
    }

    private static Instant instant(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
