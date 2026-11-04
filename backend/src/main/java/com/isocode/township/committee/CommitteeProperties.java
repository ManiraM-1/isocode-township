package com.isocode.township.committee;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.committee")
public record CommitteeProperties(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal onTimeTarget,
                                  @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal reopenTarget) {
}
