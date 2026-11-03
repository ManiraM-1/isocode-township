package com.isocode.township.matching;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public record StatementRow(
        int rowNo,
        LocalDate txnDate,
        String description,
        String reference,
        BigDecimal credit,
        BigDecimal debit,
        RowKind kind,
        Optional<String> utr,
        Optional<String> upiId,
        Optional<String> gatewayRef,
        String rowHash) {
}
