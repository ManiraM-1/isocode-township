package com.isocode.township.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties appProperties) {
        return Clock.system(appProperties.timezone());
    }
}
