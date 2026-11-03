package com.isocode.township.payment;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(BigDecimal maxAmountAboveOutstanding) {
}
