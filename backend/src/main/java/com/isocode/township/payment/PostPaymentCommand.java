package com.isocode.township.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PostPaymentCommand(
        Long flatId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentSource source,
        String gatewayRef,
        String utr,
        LocalDate paidOn,
        Long actorUserId) {
}
