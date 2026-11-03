package com.isocode.township.payment.dto;

import com.isocode.township.payment.PaymentIntentStatus;
import java.math.BigDecimal;

public record PaymentIntentResponse(
        Long id,
        Long flatId,
        BigDecimal amount,
        PaymentIntentStatus status,
        String gatewayRef,
        String receiptNo) {
}
