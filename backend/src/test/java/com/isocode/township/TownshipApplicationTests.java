package com.isocode.township;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class TownshipApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Clock clock;

    @Test
    void contextLoadsAgainstPostgres16() {
        String version = new JdbcTemplate(dataSource).queryForObject("SHOW server_version", String.class);

        assertThat(version).startsWith("16.");
    }

    @Test
    void clockUsesIndianTime() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void healthIsPublicAndReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void otherEndpointsRequireSignIn() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isForbidden());
    }
}
