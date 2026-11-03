package com.isocode.township.common;

import com.isocode.township.billing.BillingDemoSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder coreSeeder;
    private final BillingDemoSeeder billingSeeder;

    public DemoDataSeedRunner(DemoDataSeeder coreSeeder, BillingDemoSeeder billingSeeder) {
        this.coreSeeder = coreSeeder;
        this.billingSeeder = billingSeeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (coreSeeder.seedIfEmpty()) {
            billingSeeder.seed();
        }
    }
}
