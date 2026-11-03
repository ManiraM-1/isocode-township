package com.isocode.township.payment.dto;

import com.isocode.township.payment.PaymentMethod;
import com.isocode.township.payment.PaymentSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReceiptResponse(
        String receiptNo,
        Long flatId,
        String flatCode,
        BigDecimal amount,
        LocalDate paidOn,
        PaymentMethod method,
        PaymentSource source,
        String reference,
        List<Line> appliedTo,
        BigDecimal heldAsAdvance,
        Instant recordedAt) {

    public record Line(String description, BigDecimal amount, LocalDate appliedOn) {
    }
}
