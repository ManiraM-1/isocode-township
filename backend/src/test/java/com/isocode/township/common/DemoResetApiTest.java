package com.isocode.township.common;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.FixedClockConfiguration;
import com.isocode.township.ScriptedAiConfiguration;
import com.isocode.township.TestSessions;
import com.isocode.township.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.demo-mode=true",
        "app.jwt.secret=${random.uuid}${random.uuid}",
        "app.gemini.api-key=",
        "app.gemini.retry-delay=0s"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class, TestSessions.class,
        ScriptedAiConfiguration.class})
class DemoResetApiTest {

    private static final String COUNTS = """
            SELECT (SELECT count(*) FROM flat) AS flats, (SELECT count(*) FROM app_user) AS users,
                   (SELECT count(*) FROM bill) AS bills, (SELECT count(*) FROM payment) AS payments,
                   (SELECT count(*) FROM complaint) AS complaints, (SELECT count(*) FROM bank_import) AS imports,
                   (SELECT sum(debit - credit) FROM ledger_entry) AS outstanding
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestSessions sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void resetWipesChangesAndRestoresTheSeededState() throws Exception {
        Map<String, Object> seeded = jdbc.queryForMap(COUNTS);
        jdbc.update("INSERT INTO bank_import (file_name, file_sha256, uploaded_by, uploaded_at, total_rows, auto_posted, "
                + "reconciled, suggested, conflicts, unmatched, duplicates, possible_duplicates, non_credit, errors) "
                + "SELECT 'extra.csv', md5('a') || md5('b'), id, now(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 "
                + "FROM app_user WHERE role = 'HELPDESK' LIMIT 1");
        assertThat(jdbc.queryForMap(COUNTS)).isNotEqualTo(seeded);

        mockMvc.perform(post("/api/desk/demo/reset").with(sameOriginCsrf())
                        .cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetAt").value(FixedClockConfiguration.NOW.toString()));

        assertThat(jdbc.queryForMap(COUNTS)).isEqualTo(seeded);
        assertThat(seeded.get("flats")).isEqualTo(48L);
        assertThat(seeded.get("complaints")).isEqualTo(39L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Long.class))
                .isPositive();
    }

    @Test
    void residentsCannotResetTheDemo() throws Exception {
        mockMvc.perform(post("/api/desk/demo/reset").with(sameOriginCsrf())
                        .cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isForbidden());
    }
}
