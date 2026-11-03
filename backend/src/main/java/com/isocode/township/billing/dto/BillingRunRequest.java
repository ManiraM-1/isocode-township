package com.isocode.township.billing.dto;

import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

public record BillingRunRequest(@NotNull(message = "Choose a month, for example 2026-11") YearMonth month) {
}
