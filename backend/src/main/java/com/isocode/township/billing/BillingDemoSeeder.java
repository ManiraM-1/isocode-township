package com.isocode.township.billing;

import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BillingDemoSeeder {

    private static final int MONTHS_OF_HISTORY = 3;

    private final BillingRunService billingRunService;
    private final Clock clock;

    public BillingDemoSeeder(BillingRunService billingRunService, Clock clock) {
        this.billingRunService = billingRunService;
        this.clock = clock;
    }

    @Transactional
    public void seed() {
        YearMonth currentMonth = YearMonth.now(clock);
        for (int monthsAgo = MONTHS_OF_HISTORY - 1; monthsAgo >= 0; monthsAgo--) {
            billingRunService.run(currentMonth.minusMonths(monthsAgo), null);
        }
    }
}
