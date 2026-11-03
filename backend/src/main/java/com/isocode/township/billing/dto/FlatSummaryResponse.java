package com.isocode.township.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FlatSummaryResponse(
        Long flatId,
        String flatCode,
        LocalDate asOf,
        BigDecimal outstanding,
        BigDecimal overdue,
        BigDecimal advance,
        BigDecimal accruedLateFee,
        LocalDate oldestUnpaidDueDate,
        long daysOverdue) {
}
