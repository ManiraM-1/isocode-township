package com.isocode.township;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "app.demo-mode=false",
        "app.jwt.secret=${random.uuid}${random.uuid}",
        "app.gemini.api-key=",
        "app.gemini.retry-delay=0s"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class, TestSessions.class,
        ScriptedAiConfiguration.class})
public @interface IntegrationTest {
}
