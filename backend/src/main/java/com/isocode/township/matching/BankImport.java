package com.isocode.township.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "bank_import")
public class BankImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "auto_posted", nullable = false)
    private int autoPosted;

    @Column(nullable = false)
    private int reconciled;

    @Column(nullable = false)
    private int suggested;

    @Column(nullable = false)
    private int conflicts;

    @Column(nullable = false)
    private int unmatched;

    @Column(nullable = false)
    private int duplicates;

    @Column(name = "possible_duplicates", nullable = false)
    private int possibleDuplicates;

    @Column(name = "non_credit", nullable = false)
    private int nonCredit;

    @Column(nullable = false)
    private int errors;

    protected BankImport() {
    }

    public BankImport(String fileName, String fileSha256, Long uploadedBy, Instant uploadedAt) {
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void recordCounts(Map<String, Integer> counts, int parseErrors) {
        autoPosted = counts.getOrDefault(MatchDecision.AUTO_POSTED.name(), 0);
        reconciled = counts.getOrDefault(MatchDecision.RECONCILED_GATEWAY.name(), 0);
        suggested = counts.getOrDefault(MatchDecision.SUGGESTED.name(), 0);
        conflicts = counts.getOrDefault(MatchDecision.CONFLICT.name(), 0);
        unmatched = counts.getOrDefault(MatchDecision.UNMATCHED.name(), 0);
        duplicates = counts.getOrDefault(MatchDecision.DUPLICATE.name(), 0);
        possibleDuplicates = counts.getOrDefault(MatchDecision.POSSIBLE_DUPLICATE.name(), 0);
        nonCredit = counts.getOrDefault(MatchDecision.NON_CREDIT.name(), 0);
        errors = parseErrors;
        totalRows = autoPosted + reconciled + suggested + conflicts + unmatched + duplicates + possibleDuplicates
                + nonCredit + errors;
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getAutoPosted() {
        return autoPosted;
    }

    public int getReconciled() {
        return reconciled;
    }

    public int getSuggested() {
        return suggested;
    }

    public int getConflicts() {
        return conflicts;
    }

    public int getUnmatched() {
        return unmatched;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public int getPossibleDuplicates() {
        return possibleDuplicates;
    }

    public int getNonCredit() {
        return nonCredit;
    }

    public int getErrors() {
        return errors;
    }
}
