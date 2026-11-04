package com.isocode.township.common;

import java.time.Clock;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final Flyway flyway;
    private final DemoDataSeedRunner seedRunner;
    private final Clock clock;

    public DemoResetService(Flyway flyway, DemoDataSeedRunner seedRunner, Clock clock) {
        this.flyway = flyway;
        this.seedRunner = seedRunner;
        this.clock = clock;
    }

    public synchronized Instant reset(Long staffUserId) {
        Flyway demoSchema = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .cleanDisabled(false)
                .load();
        demoSchema.clean();
        demoSchema.migrate();
        seedRunner.seedIfEmpty();
        log.info("Demo data reset by staff user {}", staffUserId);
        return clock.instant();
    }
}
