package com.isocode.township.committee;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FlatBalance(Long flatId, String flatCode, BigDecimal balance, LocalDate oldestUnpaidDue) {
}
