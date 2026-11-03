package com.isocode.township.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillInterest(Bill bill, LocalDate startsAfter, BigDecimal amount) {
}
