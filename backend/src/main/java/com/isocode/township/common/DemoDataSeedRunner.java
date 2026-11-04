package com.isocode.township.common;

import com.isocode.township.complaint.ComplaintDemoSeeder;
import com.isocode.township.matching.KnownUpiIdDemoSeeder;
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
    private final KnownUpiIdDemoSeeder upiIdSeeder;
    private final ComplaintDemoSeeder complaintSeeder;

    public DemoDataSeedRunner(DemoDataSeeder coreSeeder, DemoHistorySeeder historySeeder,
                              KnownUpiIdDemoSeeder upiIdSeeder, ComplaintDemoSeeder complaintSeeder) {
        this.coreSeeder = coreSeeder;
        this.historySeeder = historySeeder;
        this.upiIdSeeder = upiIdSeeder;
        this.complaintSeeder = complaintSeeder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    @Transactional
    public void seedIfEmpty() {
        if (coreSeeder.seedIfEmpty()) {
            historySeeder.seed();
            upiIdSeeder.seed();
            complaintSeeder.seed();
        }
    }
}
