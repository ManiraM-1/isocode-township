package com.isocode.township.complaint;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.duplicates")
public record DuplicateProperties(@NotNull Duration liftWindow, @NotNull Duration textWindow,
                                  @DecimalMin("0.0") @DecimalMax("1.0") double minimumSimilarity) {

    public DuplicateDetector detector() {
        return new DuplicateDetector(liftWindow, textWindow, minimumSimilarity);
    }
}
