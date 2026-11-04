package com.isocode.township.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoResetConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(Flyway.class, () -> mock(Flyway.class))
            .withBean(DemoDataSeedRunner.class, () -> mock(DemoDataSeedRunner.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(DemoResetService.class, DemoResetController.class);

    @Test
    void resetExistsInDemoMode() {
        runner.withPropertyValues("app.demo-mode=true").run(context -> {
            assertThat(context).hasSingleBean(DemoResetService.class);
            assertThat(context).hasSingleBean(DemoResetController.class);
        });
    }

    @Test
    void resetDoesNotExistOutsideDemoMode() {
        runner.withPropertyValues("app.demo-mode=false").run(context -> {
            assertThat(context).doesNotHaveBean(DemoResetService.class);
            assertThat(context).doesNotHaveBean(DemoResetController.class);
        });
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoResetController.class));
    }
}
