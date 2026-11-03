package com.isocode.township.billing;

import java.math.BigDecimal;

public record LateFeePostingResult(int chargesPosted, BigDecimal total) {
}
