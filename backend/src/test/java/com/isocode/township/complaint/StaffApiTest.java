package com.isocode.township.complaint;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class StaffApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private TestSessions sessions;

    @BeforeEach
    void seed() {
        seeder.seedIfEmpty();
    }

    @Test
    void helpDeskSeesActiveStaffWithSkillsSortedByName() throws Exception {
        mockMvc.perform(get("/api/desk/staff").cookie(sessions.sessionFor(TestSessions.ANJALI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].name").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$[0].skills", hasSize(10)))
                .andExpect(jsonPath("$[?(@.name == 'Kiran Shinde')].dutyManager").value(true));
    }

    @Test
    void residentsCannotListStaff() throws Exception {
        mockMvc.perform(get("/api/desk/staff").cookie(sessions.sessionFor(TestSessions.NEERAJ)))
                .andExpect(status().isForbidden());
    }
}
