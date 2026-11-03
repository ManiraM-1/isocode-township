package com.isocode.township.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record PaymentIntentRequest(
        @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Amount must be more than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most two decimals")
        BigDecimal amount,

        @NotBlank(message = "A payment attempt key is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "Payment attempt key must be 8 to 64 letters, digits, - or _")
        String idempotencyKey) {
}
