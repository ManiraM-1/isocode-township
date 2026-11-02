package com.isocode.township.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationErrorListsEachInvalidField() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void notFoundBecomes404WithDetail() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Flat T99-0001 not found"));
    }

    @Test
    void conflictBecomes409() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already resolved"));
    }

    @Test
    void accessDeniedBecomes403WithoutInternalDetail() throws Exception {
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have access to this resource."));
    }

    @Test
    void unexpectedErrorHidesInternalMessage() throws Exception {
        mockMvc.perform(get("/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Please try again in a moment."));
    }

    record NameRequest(@NotBlank String name) {
    }

    @RestController
    static class FailingController {

        @PostMapping("/validated")
        void validated(@Valid @RequestBody NameRequest request) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Flat T99-0001 not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Already resolved");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("member check failed for flat 42");
        }

        @GetMapping("/crash")
        void crash() {
            throw new IllegalStateException("database password rejected");
        }
    }
}
