package com.isocode.township.matching.dto;

import java.time.Instant;

public record BankImportSummary(
        Long id,
        String fileName,
        Instant uploadedAt,
        String uploadedBy,
        int totalRows,
        int autoPosted,
        int reconciled,
        int suggested,
        int conflicts,
        int unmatched,
        int duplicates,
        int possibleDuplicates,
        int nonCredit,
        int errors,
        Instant sameFileImportedBefore) {
}
