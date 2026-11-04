package com.isocode.township.ai;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.safety")
public record SafetyProperties(List<String> terms) {

    public SafetyProperties {
        if (terms == null || terms.isEmpty()) {
            throw new IllegalStateException("app.safety.terms must list at least one safety word");
        }
    }
}
