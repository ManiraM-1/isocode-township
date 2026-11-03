package com.isocode.township.billing.dto;

import com.isocode.township.billing.BillStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record BillResponse(
        Long id,
        YearMonth month,
        int areaSqft,
        BigDecimal rate,
        BigDecimal amount,
        BigDecimal amountPaid,
        BigDecimal balance,
        LocalDate dueDate,
        BillStatus status) {
}
