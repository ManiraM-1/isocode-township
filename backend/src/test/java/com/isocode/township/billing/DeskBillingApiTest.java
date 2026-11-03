package com.isocode.township.billing;

import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class DeskBillingApiTest {

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
    void helpDeskRunsBillingForAMonth() throws Exception {
        runBilling(sessions.sessionFor(TestSessions.ANJALI), "{\"month\":\"2026-11\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.billsCreated").value(48))
                .andExpect(jsonPath("$.lateInterestPostedUpTo").value("2026-10-31"))
                .andExpect(jsonPath("$.lateFeeTotal").value("0.00"));
    }

    @Test
    void residentCannotRunBilling() throws Exception {
        runBilling(sessions.sessionFor(TestSessions.NEERAJ), "{\"month\":\"2026-11\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void missingMonthIsAFieldError() throws Exception {
        runBilling(sessions.sessionFor(TestSessions.ANJALI), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("month"));
    }

    @Test
    void malformedMonthIsRejected() throws Exception {
        runBilling(sessions.sessionFor(TestSessions.ANJALI), "{\"month\":\"2026-13\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void futureMonthIsRejectedWithAReason() throws Exception {
        runBilling(sessions.sessionFor(TestSessions.ANJALI), "{\"month\":\"2026-12\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bills can only be generated up to the current month."));
    }

    private ResultActions runBilling(Cookie session, String body) throws Exception {
        return mockMvc.perform(post("/api/desk/billing-runs").with(sameOriginCsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
