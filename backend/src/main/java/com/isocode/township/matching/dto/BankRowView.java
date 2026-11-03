package com.isocode.township.matching.dto;

import com.isocode.township.matching.BankRowResolution;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BankRowView(
        Long id,
        Long importId,
        int rowNo,
        LocalDate txnDate,
        String description,
        String reference,
        BigDecimal amount,
        BigDecimal debit,
        String utr,
        String upiId,
        String gatewayRef,
        String decision,
        String confidence,
        FlatRef suggestedFlat,
        List<FlatRef> candidates,
        List<String> reasons,
        DuplicateRef duplicateOf,
        String receiptNo,
        BankRowResolution resolution,
        String resolvedBy,
        Instant resolvedAt,
        String resolutionNote,
        String parseError,
        boolean openInQueue) {

    public record FlatRef(Long id, String code) {
    }

    public record DuplicateRef(Long rowId, Long importId, int rowNo) {
    }
}
