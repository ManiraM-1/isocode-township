package com.isocode.township.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.isocode.township.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SpaForwardTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/home", "/login", "/complaints/12", "/desk/committee/ageing/31-60",
            "/receipts/RCPT-202611-00001"})
    void clientRoutesServeTheApp(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Township test shell")));
    }

    @Test
    void theRootForwardsToTheApp() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void builtAssetsAreServedAsThemselves() throws Exception {
        mockMvc.perform(get("/assets/app-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/missing-file.js", "/api/unknown", "/api/desk/nothing-here", "/favicon.ico"})
    void missingFilesAndUnknownApiPathsAreNotFound(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().is4xxClientError());
    }
}
