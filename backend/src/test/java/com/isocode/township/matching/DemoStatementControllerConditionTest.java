package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoStatementControllerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DemoStatementGenerator.class, () -> mock(DemoStatementGenerator.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(DemoStatementController.class);

    @Test
    void controllerExistsInDemoMode() {
        runner.withPropertyValues("app.demo-mode=true")
                .run(context -> assertThat(context).hasSingleBean(DemoStatementController.class));
    }

    @Test
    void controllerDoesNotExistOutsideDemoMode() {
        runner.withPropertyValues("app.demo-mode=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoStatementController.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoStatementController.class));
    }
}
