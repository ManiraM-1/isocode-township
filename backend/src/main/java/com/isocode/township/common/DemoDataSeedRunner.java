package com.isocode.township.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    public DemoDataSeedRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seeder.seedIfEmpty();
    }
}
