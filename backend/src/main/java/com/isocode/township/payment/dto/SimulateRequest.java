package com.isocode.township.payment.dto;

import com.isocode.township.payment.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;

public record SimulateRequest(@NotNull(message = "Choose SUCCESS or FAILURE") SimulatedOutcome outcome) {
}
