package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class StaleClassificationSweepTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintClassificationUpdater updater;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestSessions sessions;

    private Cookie swati;
    private Long swatiFlat;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swati = sessions.sessionFor(TestSessions.SWATI);
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
    }

    @Test
    void aComplaintTheAiNeverAnsweredIsGivenUpOnAndSentToReview() throws Exception {
        long stale = raise("Water is leaking from the bathroom ceiling.");
        long fresh = raise("The corridor light outside my door is flickering all night.");
        entityManager.flush();
        jdbcTemplate.update("UPDATE complaint SET created_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(3 * 60)), stale);
        entityManager.clear();

        assertThat(updater.giveUpOnStaleClassifications()).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        Complaint gaveUp = complaintRepository.findById(stale).orElseThrow();
        assertThat(gaveUp.getClassificationStatus()).isEqualTo(ClassificationStatus.FAILED);
        assertThat(gaveUp.isNeedsReview()).isTrue();
        assertThat(gaveUp.getCategory()).isEqualTo(ComplaintCategory.PLUMBING);
        assertThat(gaveUp.getAiReason()).isEqualTo("AI unavailable: the AI did not answer in time. "
                + "Keyword rules matched leak, water.");
        assertThat(complaintRepository.findById(fresh).orElseThrow().getClassificationStatus())
                .isEqualTo(ClassificationStatus.PENDING);
    }

    private long raise(String description) throws Exception {
        String body = mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat)
                        .param("description", description).with(sameOriginCsrf()).cookie(swati))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
