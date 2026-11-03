package com.isocode.township.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bank_row")
public class BankRow {

    public static final String PARSE_ERROR = "PARSE_ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "row_no", nullable = false)
    private int rowNo;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String reference;

    @Column(length = 40)
    private String utr;

    @Column(length = 100)
    private String vpa;

    @Column(name = "gateway_ref", length = 40)
    private String gatewayRef;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal debit;

    @Column(name = "row_hash", length = 64)
    private String rowHash;

    @Column(name = "duplicate_of_row_id")
    private Long duplicateOfRowId;

    @Column(nullable = false, length = 24)
    private String decision;

    @Column(name = "suggested_flat_id")
    private Long suggestedFlatId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> candidates;

    @Column(length = 8)
    private String confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BankRowResolution resolution;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "parse_error", length = 300)
    private String parseError;

    protected BankRow() {
    }

    public static BankRow matched(Long importId, StatementRow row, MatchResult result) {
        BankRow bankRow = new BankRow();
        bankRow.importId = importId;
        bankRow.rowNo = row.rowNo();
        bankRow.txnDate = row.txnDate();
        bankRow.description = truncate(row.description(), 500);
        bankRow.reference = truncate(row.reference(), 100);
        bankRow.utr = row.utr().orElse(null);
        bankRow.vpa = row.upiId().orElse(null);
        bankRow.gatewayRef = row.gatewayRef().orElse(null);
        bankRow.amount = row.credit();
        bankRow.debit = row.debit();
        bankRow.rowHash = row.rowHash();
        bankRow.decision = result.decision().name();
        bankRow.suggestedFlatId = result.flatId().orElse(null);
        bankRow.candidates = result.candidates().stream()
                .map(candidate -> Map.<String, Object>of("flatId", candidate.flatId(), "flatCode", candidate.flatCode()))
                .toList();
        bankRow.confidence = result.confidence().map(Enum::name).orElse(null);
        bankRow.reasons = result.reasons();
        bankRow.paymentId = result.decision() == MatchDecision.RECONCILED_GATEWAY
                ? result.gatewayPaymentId().orElse(null)
                : null;
        return bankRow;
    }

    public static BankRow unreadable(Long importId, RowError error) {
        BankRow bankRow = new BankRow();
        bankRow.importId = importId;
        bankRow.rowNo = error.rowNo();
        bankRow.decision = PARSE_ERROR;
        bankRow.candidates = List.of();
        bankRow.reasons = List.of();
        bankRow.parseError = truncate(error.message(), 300);
        return bankRow;
    }

    public void linkDuplicateOf(Long earlierRowId) {
        duplicateOfRowId = earlierRowId;
    }

    public void linkPayment(Long postedPaymentId) {
        paymentId = postedPaymentId;
    }

    public void holdForReview(String reason) {
        decision = MatchDecision.SUGGESTED.name();
        confidence = Confidence.MEDIUM.name();
        reasons = Stream.concat(reasons.stream(), Stream.of(reason)).toList();
    }

    public void resolve(BankRowResolution how, Long staffUserId, Instant at, String note) {
        resolution = how;
        resolvedBy = staffUserId;
        resolvedAt = at;
        resolutionNote = note;
    }

    public boolean isOpenInQueue() {
        return resolution == null && decision().filter(MatchDecision::goesToAccountsQueue).isPresent();
    }

    public Optional<MatchDecision> decision() {
        return PARSE_ERROR.equals(decision) ? Optional.empty() : Optional.of(MatchDecision.valueOf(decision));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getImportId() {
        return importId;
    }

    public int getRowNo() {
        return rowNo;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public Optional<String> getUtr() {
        return Optional.ofNullable(utr);
    }

    public Optional<String> getVpa() {
        return Optional.ofNullable(vpa);
    }

    public Optional<String> getGatewayRef() {
        return Optional.ofNullable(gatewayRef);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public String getRowHash() {
        return rowHash;
    }

    public Long getDuplicateOfRowId() {
        return duplicateOfRowId;
    }

    public String getDecisionName() {
        return decision;
    }

    public Long getSuggestedFlatId() {
        return suggestedFlatId;
    }

    public List<Map<String, Object>> getCandidates() {
        return candidates;
    }

    public String getConfidence() {
        return confidence;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public BankRowResolution getResolution() {
        return resolution;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getParseError() {
        return parseError;
    }
}
