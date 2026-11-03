package com.isocode.township.billing.dto;

import com.isocode.township.billing.LedgerEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerLineResponse(
        Long id,
        LocalDate date,
        LedgerEntryType type,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal runningBalance,
        String receiptNo) {
}
