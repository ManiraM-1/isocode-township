package com.isocode.township.committee;

import com.isocode.township.committee.CommitteeFactsQuery.CategoryCount;
import com.isocode.township.committee.CommitteeFactsQuery.LastImport;
import com.isocode.township.committee.dto.CommitteeOverview;
import com.isocode.township.committee.dto.CommitteeOverview.AgeingLine;
import com.isocode.township.committee.dto.CommitteeOverview.Collections;
import com.isocode.township.committee.dto.CommitteeOverview.Complaints;
import com.isocode.township.committee.dto.CommitteeOverview.Matching;
import com.isocode.township.committee.dto.CommitteeOverview.Reconciliation;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public final class CommitteeReportCsv {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader("Section", "Metric", "Value")
            .setRecordSeparator("\r\n")
            .get();

    private CommitteeReportCsv() {
    }

    public static String render(CommitteeOverview overview) {
        StringWriter out = new StringWriter();
        try (CSVPrinter csv = new CSVPrinter(out, FORMAT)) {
            csv.printRecord("Report", "Month", overview.month());
            csv.printRecord("Report", "As at", overview.asOf());
            collections(csv, overview.collections());
            reconciliation(csv, overview.reconciliation());
            for (AgeingLine line : overview.ageing().buckets()) {
                csv.printRecord("Dues ageing", line.label() + " flats", line.flats());
                csv.printRecord("Dues ageing", line.label() + " amount", line.amount());
            }
            csv.printRecord("Dues ageing", "Total", overview.ageing().total());
            csv.printRecord("Dues ageing", "Matches closing outstanding", yesNo(overview.ageing().matchesClosingOutstanding()));
            matching(csv, overview.matching());
            complaints(csv, overview.complaints());
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return out.toString();
    }

    private static void collections(CSVPrinter csv, Collections collections) throws IOException {
        csv.printRecord("Collections", "Billed", collections.billed());
        csv.printRecord("Collections", "Late fees charged", collections.lateFeesCharged());
        csv.printRecord("Collections", "Late-fee reversals", collections.lateFeeReversals());
        csv.printRecord("Collections", "Collected", collections.collected());
        csv.printRecord("Collections", "Total due", collections.totalDue());
        csv.printRecord("Collections", "Collection rate %", value(collections.collectionRate()));
        csv.printRecord("Collections", "Bills due", collections.billsDue());
        csv.printRecord("Collections", "Bills paid on time", collections.billsPaidOnTime());
        csv.printRecord("Collections", "On-time rate %", value(collections.onTimeRate()));
        csv.printRecord("Collections", "On-time target %", collections.onTimeTarget());
    }

    private static void reconciliation(CSVPrinter csv, Reconciliation reconciliation) throws IOException {
        csv.printRecord("Reconciliation", "Opening outstanding", reconciliation.openingOutstanding());
        csv.printRecord("Reconciliation", "Plus billed", reconciliation.billed());
        csv.printRecord("Reconciliation", "Plus late fees", reconciliation.lateFeesCharged());
        csv.printRecord("Reconciliation", "Less late-fee reversals", reconciliation.lateFeeReversals());
        csv.printRecord("Reconciliation", "Less collections", reconciliation.collections());
        csv.printRecord("Reconciliation", "Closing outstanding", reconciliation.closingOutstanding());
        csv.printRecord("Reconciliation", "Balanced", yesNo(reconciliation.balanced()));
    }

    private static void matching(CSVPrinter csv, Matching matching) throws IOException {
        LastImport last = matching.lastImport();
        csv.printRecord("Payment matching", "Last import", last == null ? "" : last.fileName());
        csv.printRecord("Payment matching", "Last import at", last == null ? "" : last.uploadedAt());
        csv.printRecord("Payment matching", "Imports this month", matching.importsThisMonth());
        csv.printRecord("Payment matching", "New payment rows", matching.newPaymentRows());
        csv.printRecord("Payment matching", "Auto-posted", matching.autoPosted());
        csv.printRecord("Payment matching", "Auto-post share %", value(matching.autoPostShare()));
        csv.printRecord("Payment matching", "Queue now", matching.queueSize());
        csv.printRecord("Payment matching", "Oldest queued days", value(matching.oldestQueuedDays()));
    }

    private static void complaints(CSVPrinter csv, Complaints complaints) throws IOException {
        csv.printRecord("Complaints", "Opened", complaints.opened());
        csv.printRecord("Complaints", "Resolved", complaints.resolved());
        csv.printRecord("Complaints", "Median first response minutes", value(complaints.medianFirstResponseMinutes()));
        csv.printRecord("Complaints", "Median resolution minutes", value(complaints.medianResolutionMinutes()));
        csv.printRecord("Complaints", "First response breached %", value(complaints.firstResponseBreachShare()));
        csv.printRecord("Complaints", "Reopened", complaints.reopened());
        csv.printRecord("Complaints", "Reopen rate %", value(complaints.reopenRate()));
        csv.printRecord("Complaints", "Reopen target %", complaints.reopenTarget());
        csv.printRecord("Complaints", "Average rating", value(complaints.averageRating()));
        for (CategoryCount open : complaints.openByCategory()) {
            csv.printRecord("Complaints", "Open now: " + open.category().name(), open.open());
        }
        csv.printRecord("Complaints", "Active safety escalations", complaints.activeSafetyEscalations());
    }

    private static Object value(Object nullable) {
        if (nullable instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return nullable == null ? "" : nullable;
    }

    private static String yesNo(boolean flag) {
        return flag ? "Yes" : "No";
    }
}
