package com.isocode.township.common;

import com.isocode.township.payment.DemoHistorySeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder coreSeeder;
    private final DemoHistorySeeder historySeeder;

    public DemoDataSeedRunner(DemoDataSeeder coreSeeder, DemoHistorySeeder historySeeder) {
        this.coreSeeder = coreSeeder;
        this.historySeeder = historySeeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (coreSeeder.seedIfEmpty()) {
            historySeeder.seed();
        }
    }
}
