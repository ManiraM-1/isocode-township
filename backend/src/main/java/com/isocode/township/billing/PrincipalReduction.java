package com.isocode.township.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrincipalReduction(LocalDate on, BigDecimal amount) {
}
