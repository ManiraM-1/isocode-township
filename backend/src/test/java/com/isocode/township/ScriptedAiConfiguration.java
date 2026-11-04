package com.isocode.township;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ScriptedAiConfiguration {

    @Bean
    @Primary
    ScriptedAiModel scriptedAiModel() {
        return new ScriptedAiModel();
    }
}
