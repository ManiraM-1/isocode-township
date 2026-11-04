package com.isocode.township.committee;

import com.isocode.township.common.Money;
import com.isocode.township.complaint.ComplaintCategory;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommitteeFactsQuery {

    private static final String QUEUED_DECISIONS = "('SUGGESTED', 'CONFLICT', 'UNMATCHED', 'POSSIBLE_DUPLICATE')";
    private static final String ACTIVE_STATUSES = "('OPEN', 'ASSIGNED', 'IN_PROGRESS')";

    private final JdbcTemplate jdbc;

    public CommitteeFactsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LedgerTotals ledgerTotals(ReportMonth month) {
        return jdbc.queryForObject("""
                SELECT COALESCE(sum(debit - credit) FILTER (WHERE entry_date < ?), 0) AS opening,
                       COALESCE(sum(debit) FILTER (WHERE type = 'BILL' AND entry_date BETWEEN ? AND ?), 0) AS billed,
                       COALESCE(sum(debit) FILTER (WHERE type = 'LATE_FEE' AND entry_date BETWEEN ? AND ?), 0) AS late_fees,
                       COALESCE(sum(credit) FILTER (WHERE type = 'REVERSAL' AND entry_date BETWEEN ? AND ?), 0) AS reversals,
                       COALESCE(sum(credit) FILTER (WHERE type = 'PAYMENT' AND entry_date BETWEEN ? AND ?), 0) AS collections,
                       COALESCE(sum(debit - credit) FILTER (WHERE entry_date <= ?), 0) AS closing
                FROM ledger_entry
                """, (resultSet, rowNum) -> new LedgerTotals(money(resultSet, "opening"), money(resultSet, "billed"),
                        money(resultSet, "late_fees"), money(resultSet, "reversals"), money(resultSet, "collections"),
                        money(resultSet, "closing")),
                date(month.firstDay()), date(month.firstDay()), date(month.asOf()), date(month.firstDay()),
                date(month.asOf()), date(month.firstDay()), date(month.asOf()), date(month.firstDay()),
                date(month.asOf()), date(month.asOf()));
    }

    public OnTimeCounts onTimeCounts(ReportMonth month) {
        return jdbc.queryForObject("""
                SELECT count(*) AS due, count(*) FILTER (WHERE paid_by_due >= amount) AS on_time FROM (
                    SELECT b.amount,
                           COALESCE((SELECT sum(a.amount) FROM payment_allocation a
                                     WHERE a.bill_id = b.id AND a.allocated_on <= b.due_date), 0) AS paid_by_due
                    FROM bill b WHERE b.due_date BETWEEN ? AND ?) AS due_bills
                """, (resultSet, rowNum) -> new OnTimeCounts(resultSet.getInt("due"), resultSet.getInt("on_time")),
                date(month.firstDay()), date(month.lastDueDateCounted()));
    }

    public List<FlatBalance> flatBalances(LocalDate asOf) {
        Date day = date(asOf);
        return jdbc.query("""
                WITH balance AS (
                    SELECT flat_id, sum(debit - credit) AS amount FROM ledger_entry
                    WHERE entry_date <= ? GROUP BY flat_id HAVING sum(debit - credit) <> 0
                ), unpaid_bill AS (
                    SELECT b.flat_id, b.due_date FROM bill b
                    WHERE b.period <= ?
                      AND b.amount > COALESCE((SELECT sum(a.amount) FROM payment_allocation a
                                               WHERE a.bill_id = b.id AND a.allocated_on <= ?), 0)
                ), unpaid_fee AS (
                    SELECT c.flat_id, b.due_date FROM late_fee_charge c JOIN bill b ON b.id = c.bill_id
                    WHERE c.to_date <= ?
                      AND c.amount > COALESCE((SELECT sum(a.amount) FROM payment_allocation a
                                               WHERE a.late_fee_charge_id = c.id AND a.allocated_on <= ?), 0)
                                   + COALESCE((SELECT sum(adj.amount) FROM late_fee_adjustment adj
                                               JOIN ledger_entry l ON l.ref_type = 'LATE_FEE_ADJUSTMENT'
                                                                  AND l.ref_id = adj.id
                                               WHERE adj.late_fee_charge_id = c.id AND l.entry_date <= ?), 0)
                ), oldest AS (
                    SELECT flat_id, min(due_date) AS due_date
                    FROM (SELECT * FROM unpaid_bill UNION ALL SELECT * FROM unpaid_fee) AS items
                    GROUP BY flat_id
                )
                SELECT f.id, f.code, balance.amount, oldest.due_date
                FROM balance JOIN flat f ON f.id = balance.flat_id
                LEFT JOIN oldest ON oldest.flat_id = balance.flat_id
                ORDER BY f.code
                """, (resultSet, rowNum) -> new FlatBalance(resultSet.getLong("id"), resultSet.getString("code"),
                        money(resultSet, "amount"), localDate(resultSet, "due_date")),
                day, day, day, day, day, day);
    }

    public Optional<LastImport> lastImportBefore(Instant end) {
        return jdbc.query("""
                SELECT id, file_name, uploaded_at, total_rows, auto_posted, reconciled, suggested, conflicts,
                       unmatched, duplicates, possible_duplicates, non_credit, errors
                FROM bank_import WHERE uploaded_at < ? ORDER BY uploaded_at DESC, id DESC LIMIT 1
                """, (resultSet, rowNum) -> new LastImport(resultSet.getLong("id"), resultSet.getString("file_name"),
                        instant(resultSet, "uploaded_at"), resultSet.getInt("total_rows"),
                        resultSet.getInt("auto_posted"), resultSet.getInt("reconciled"),
                        resultSet.getInt("suggested"), resultSet.getInt("conflicts"), resultSet.getInt("unmatched"),
                        resultSet.getInt("duplicates"), resultSet.getInt("possible_duplicates"),
                        resultSet.getInt("non_credit"), resultSet.getInt("errors")),
                Timestamp.from(end)).stream().findFirst();
    }

    public ImportTotals importTotals(ReportMonth month) {
        return jdbc.queryForObject("""
                SELECT count(*) AS imports, COALESCE(sum(auto_posted), 0) AS auto_posted,
                       COALESCE(sum(reconciled), 0) AS reconciled, COALESCE(sum(suggested), 0) AS suggested,
                       COALESCE(sum(conflicts), 0) AS conflicts, COALESCE(sum(unmatched), 0) AS unmatched,
                       COALESCE(sum(possible_duplicates), 0) AS possible_duplicates
                FROM bank_import WHERE uploaded_at >= ? AND uploaded_at < ?
                """, (resultSet, rowNum) -> new ImportTotals(resultSet.getInt("imports"),
                        resultSet.getInt("auto_posted"), resultSet.getInt("reconciled"),
                        resultSet.getInt("suggested"), resultSet.getInt("conflicts"), resultSet.getInt("unmatched"),
                        resultSet.getInt("possible_duplicates")),
                Timestamp.from(month.windowStart()), Timestamp.from(month.windowEnd()));
    }

    public QueueNow queueNow() {
        return jdbc.queryForObject("SELECT count(*) AS size, min(i.uploaded_at) AS oldest FROM bank_row r "
                        + "JOIN bank_import i ON i.id = r.import_id "
                        + "WHERE r.resolution IS NULL AND r.decision IN " + QUEUED_DECISIONS,
                (resultSet, rowNum) -> new QueueNow(resultSet.getInt("size"), instant(resultSet, "oldest")));
    }

    public List<ComplaintFacts> complaintsTouching(ReportMonth month) {
        Timestamp start = Timestamp.from(month.windowStart());
        Timestamp end = Timestamp.from(month.windowEnd());
        return jdbc.query("""
                SELECT created_at, first_response_at, first_response_due_at, resolved_at, closed_at, reopen_count, rating
                FROM complaint
                WHERE (created_at >= ? AND created_at < ?)
                   OR (resolved_at >= ? AND resolved_at < ?)
                   OR (closed_at >= ? AND closed_at < ?)
                """, (resultSet, rowNum) -> new ComplaintFacts(instant(resultSet, "created_at"),
                        instant(resultSet, "first_response_at"), instant(resultSet, "first_response_due_at"),
                        instant(resultSet, "resolved_at"), instant(resultSet, "closed_at"),
                        resultSet.getInt("reopen_count"), resultSet.getObject("rating", Integer.class)),
                start, end, start, end, start, end);
    }

    public List<CategoryCount> openComplaintsByCategory() {
        return jdbc.query("SELECT category, count(*) AS open FROM complaint WHERE status IN " + ACTIVE_STATUSES
                        + " GROUP BY category ORDER BY count(*) DESC, category",
                (resultSet, rowNum) -> new CategoryCount(ComplaintCategory.valueOf(resultSet.getString("category")),
                        resultSet.getInt("open")));
    }

    public EscalationsNow escalationsNow() {
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE c.status IN " + ACTIVE_STATUSES + ") AS active, "
                        + "count(*) FILTER (WHERE e.acknowledged_at IS NULL) AS unacknowledged "
                        + "FROM escalation e JOIN complaint c ON c.id = e.complaint_id",
                (resultSet, rowNum) -> new EscalationsNow(resultSet.getInt("active"),
                        resultSet.getInt("unacknowledged")));
    }

    private static BigDecimal money(ResultSet resultSet, String column) throws SQLException {
        return Money.rounded(resultSet.getBigDecimal(column));
    }

    private static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Date date(LocalDate day) {
        return Date.valueOf(day);
    }

    public record OnTimeCounts(int due, int onTime) {
    }

    public record LastImport(Long id, String fileName, Instant uploadedAt, int totalRows, int autoPosted,
                             int reconciled, int suggested, int conflicts, int unmatched, int duplicates,
                             int possibleDuplicates, int nonCredit, int errors) {
    }

    public record QueueNow(int size, Instant oldestQueuedAt) {
    }

    public record CategoryCount(ComplaintCategory category, int open) {
    }

    public record EscalationsNow(int active, int unacknowledged) {
    }
}
