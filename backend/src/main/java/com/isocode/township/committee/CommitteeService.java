package com.isocode.township.committee;

import com.isocode.township.committee.CommitteeFactsQuery.EscalationsNow;
import com.isocode.township.committee.CommitteeFactsQuery.OnTimeCounts;
import com.isocode.township.committee.CommitteeFactsQuery.QueueNow;
import com.isocode.township.committee.DuesAgeing.AgedFlat;
import com.isocode.township.committee.DuesAgeing.BucketTotal;
import com.isocode.township.committee.dto.AgeingFlatsResponse;
import com.isocode.township.committee.dto.CommitteeOverview;
import com.isocode.township.committee.dto.CommitteeOverview.Ageing;
import com.isocode.township.committee.dto.CommitteeOverview.AgeingLine;
import com.isocode.township.committee.dto.CommitteeOverview.Collections;
import com.isocode.township.committee.dto.CommitteeOverview.Complaints;
import com.isocode.township.committee.dto.CommitteeOverview.Matching;
import com.isocode.township.committee.dto.CommitteeOverview.Reconciliation;
import com.isocode.township.common.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitteeService {

    private final CommitteeFactsQuery facts;
    private final CommitteeProperties properties;
    private final Clock clock;

    public CommitteeService(CommitteeFactsQuery facts, CommitteeProperties properties, Clock clock) {
        this.facts = facts;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CommitteeOverview overview(String monthText) {
        ReportMonth month = ReportMonth.parse(monthText, clock);
        Instant now = clock.instant();
        LedgerTotals ledger = facts.ledgerTotals(month);
        DuesAgeing ageing = new DuesAgeing(month.asOf(), facts.flatBalances(month.asOf()));
        return new CommitteeOverview(month.month(), month.asOf(), now, collections(month, ledger),
                reconciliation(ledger), ageing(ageing, ledger), matching(month, now), complaints(month, now));
    }

    @Transactional(readOnly = true)
    public AgeingFlatsResponse ageingBucket(String bucketSlug, String monthText) {
        AgeingBucket bucket = AgeingBucket.fromSlug(bucketSlug).orElseThrow(() -> new NotFoundException(
                "Unknown ageing bucket. Use not-yet-due, 1-30, 31-60, 61-90, 90-plus or in-credit."));
        ReportMonth month = ReportMonth.parse(monthText, clock);
        DuesAgeing ageing = new DuesAgeing(month.asOf(), facts.flatBalances(month.asOf()));
        List<AgedFlat> flats = ageing.flatsIn(bucket);
        BucketTotal total = ageing.buckets().get(bucket.ordinal());
        return new AgeingFlatsResponse(month.month(), month.asOf(), bucket.slug(), bucket.label(), total.flats(),
                total.amount(), flats);
    }

    private Collections collections(ReportMonth month, LedgerTotals ledger) {
        OnTimeCounts onTime = facts.onTimeCounts(month);
        return new Collections(ledger.billed(), ledger.lateFeesCharged(), ledger.lateFeeReversals(),
                ledger.collections(), ledger.totalDue(), ledger.collectionRate(), onTime.due(), onTime.onTime(),
                Percentages.share(onTime.onTime(), onTime.due()), properties.onTimeTarget());
    }

    private static Reconciliation reconciliation(LedgerTotals ledger) {
        return new Reconciliation(ledger.opening(), ledger.billed(), ledger.lateFeesCharged(),
                ledger.lateFeeReversals(), ledger.collections(), ledger.expectedClosing(), ledger.closing(),
                ledger.balanced());
    }

    private static Ageing ageing(DuesAgeing ageing, LedgerTotals ledger) {
        List<AgeingLine> lines = ageing.buckets().stream()
                .map(line -> new AgeingLine(line.bucket().slug(), line.bucket().label(), line.flats(), line.amount()))
                .toList();
        return new Ageing(ageing.asOf(), lines, ageing.total(), ageing.total().compareTo(ledger.closing()) == 0);
    }

    private Matching matching(ReportMonth month, Instant now) {
        ImportTotals imports = facts.importTotals(month);
        QueueNow queue = facts.queueNow();
        Instant lastImportCutoff = month.windowEnd().isBefore(now) ? month.windowEnd() : now;
        Long oldestQueuedDays = queue.oldestQueuedAt() == null ? null
                : Duration.between(queue.oldestQueuedAt(), now).toDays();
        return new Matching(facts.lastImportBefore(lastImportCutoff).orElse(null), imports.imports(),
                imports.newPaymentRows(), imports.autoPosted(), imports.autoPostShare(), queue.size(),
                queue.oldestQueuedAt(), oldestQueuedDays);
    }

    private Complaints complaints(ReportMonth month, Instant now) {
        ComplaintMetrics metrics = ComplaintMetrics.compute(facts.complaintsTouching(month), month.windowStart(),
                month.windowEnd(), now);
        EscalationsNow escalations = facts.escalationsNow();
        return new Complaints(metrics.opened(), metrics.resolved(), metrics.medianFirstResponseMinutes(),
                metrics.medianResolutionMinutes(), metrics.firstResponseMeasured(), metrics.firstResponseBreached(),
                metrics.firstResponseBreachShare(), metrics.reopened(), metrics.reopenRate(),
                properties.reopenTarget(), metrics.ratings(), metrics.averageRating(),
                facts.openComplaintsByCategory(), escalations.active(), escalations.unacknowledged());
    }
}
