package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static com.isocode.township.SameOriginCsrf.sameOriginCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isocode.township.IntegrationTest;
import com.isocode.township.TestSessions;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import com.isocode.township.flat.FlatRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class ResidentComplaintApiTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final String WATER_LEAK = "Water is leaking from the bathroom ceiling and the wall is getting damp.";

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
    private EntityManager entityManager;

    @Autowired
    private TestSessions sessions;

    private Long swatiFlat;
    private Long neerajFlat;
    private Cookie swati;

    @BeforeEach
    void setUp() {
        seeder.seedIfEmpty();
        swatiFlat = flatRepository.findByCode("T12-0408").orElseThrow().getId();
        neerajFlat = flatRepository.findByCode("T07-1203").orElseThrow().getId();
        swati = sessions.sessionFor(TestSessions.SWATI);
    }

    @Test
    void residentRaisesAComplaintThatIsNumberedAssignedAndTimed() throws Exception {
        raise(swati, swatiFlat, WATER_LEAK, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refNo").value(matchesPattern("CMP-2611-\\d{4}")))
                .andExpect(jsonPath("$.flatCode").value("T12-0408"))
                .andExpect(jsonPath("$.raisedBy").value("Swati Joshi"))
                .andExpect(jsonPath("$.category").value("OTHER"))
                .andExpect(jsonPath("$.urgency").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee.name").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.firstResponseDueAt").value(NOW.plus(Duration.ofHours(8)).toString()))
                .andExpect(jsonPath("$.resolveDueAt").value(NOW.plus(Duration.ofHours(72)).toString()))
                .andExpect(jsonPath("$.firstResponseAt").isEmpty())
                .andExpect(jsonPath("$.timeline[*].type", contains("CREATED", "AUTO_ASSIGNED")))
                .andExpect(jsonPath("$.timeline[1].actorName").value("System"))
                .andExpect(jsonPath("$.timeline[1].to").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.actions.canClose").value(false));
    }

    @Test
    void photoIsStoredAndServedOnlyAsTheDetectedImageType() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, JPEG).andExpect(jsonPath("$.hasPhoto").value(true)));

        mockMvc.perform(get("/api/complaints/{id}/photo", id).cookie(swati))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void aFileThatIsNotAPhotoIsRejectedWhateverItIsCalled() throws Exception {
        mockMvc.perform(multipart("/api/flats/{id}/complaints", swatiFlat)
                        .file(new MockMultipartFile("photo", "leak.jpg", "image/jpeg", "%PDF-1.7 not a photo".getBytes()))
                        .param("description", WATER_LEAK).with(sameOriginCsrf()).cookie(swati))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("photo"))
                .andExpect(jsonPath("$.errors[0].message").value("Attach a JPEG, PNG or WebP photo"));
    }

    @Test
    void anOversizedPhotoIsRejected() throws Exception {
        byte[] huge = new byte[ComplaintService.MAX_PHOTO_BYTES + 1];
        System.arraycopy(JPEG, 0, huge, 0, JPEG.length);

        raise(swati, swatiFlat, WATER_LEAK, huge)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("The photo is larger than 5 MB"));
        assertThat(complaintRepository.count()).isZero();
    }

    @Test
    void descriptionMustBeTenToOneThousandCharacters() throws Exception {
        raise(swati, swatiFlat, "Leak", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        raise(swati, swatiFlat, "x".repeat(1001), null).andExpect(status().isBadRequest());
    }

    @Test
    void residentSeesOnlyTheirOwnFlatsComplaintsAndPhotos() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, JPEG));
        Cookie neeraj = sessions.sessionFor(TestSessions.NEERAJ);

        raise(neeraj, swatiFlat, WATER_LEAK, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/flats/{id}/complaints", swatiFlat).cookie(neeraj)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}", id).cookie(neeraj)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/complaints/{id}/photo", id).cookie(neeraj)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/flats/{id}/complaints", neerajFlat).cookie(neeraj))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void helpDeskCanReadAnyComplaintAndPhoto() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, JPEG));
        Cookie anjali = sessions.sessionFor(TestSessions.ANJALI);

        mockMvc.perform(get("/api/complaints/{id}", id).cookie(anjali))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.canComment").value(true))
                .andExpect(jsonPath("$.actions.statusChanges", contains("IN_PROGRESS", "RESOLVED")));
        mockMvc.perform(get("/api/complaints/{id}/photo", id).cookie(anjali)).andExpect(status().isOk());
    }

    @Test
    void closingWorksOnlyFromResolvedAndNeedsARating() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, null));

        close(swati, id, Map.of("rating", 4)).andExpect(status().isConflict());

        resolveAsStaff(id);
        close(swati, id, Map.of("comment", "fine")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rating"));
        close(swati, id, Map.of("rating", 6)).andExpect(status().isBadRequest());
        close(swati, id, Map.of("rating", 4, "comment", "Fixed the same day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.ratingComment").value("Fixed the same day"))
                .andExpect(jsonPath("$.timeline[-1:].type", contains("CLOSED")));
        close(swati, id, Map.of("rating", 5)).andExpect(status().isConflict());
    }

    @Test
    void reopeningReturnsItToTheSameAssigneeCountsAndRestartsTheResolutionClock() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, null));
        resolveAsStaff(id);

        reopen(swati, id, "Ceiling is still dripping after rain")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee.name").value("Anjali Deshmukh"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.resolveDueAt").value(NOW.plus(Duration.ofHours(72)).toString()))
                .andExpect(jsonPath("$.timeline[-1:].note", contains("Ceiling is still dripping after rain")));
        reopen(swati, id, "Again").andExpect(status().isConflict());

        resolveAsStaff(id);
        reopen(swati, id, "Still not fixed properly").andExpect(jsonPath("$.reopenCount").value(2));
    }

    @Test
    void reopeningNeedsAReason() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, null));
        resolveAsStaff(id);

        reopen(swati, id, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    @Test
    void onlyTheResidentCanCloseOrReopen() throws Exception {
        long id = idOf(raise(swati, swatiFlat, WATER_LEAK, null));
        resolveAsStaff(id);
        Cookie anjali = sessions.sessionFor(TestSessions.ANJALI);

        close(anjali, id, Map.of("rating", 5)).andExpect(status().isForbidden());
        reopen(anjali, id, "Staff cannot reopen").andExpect(status().isForbidden());
        close(sessions.sessionFor(TestSessions.NEERAJ), id, Map.of("rating", 1)).andExpect(status().isForbidden());
    }

    private void resolveAsStaff(long id) {
        entityManager.flush();
        Complaint complaint = complaintRepository.findById(id).orElseThrow();
        if (complaint.getStatus() == ComplaintStatus.ASSIGNED) {
            complaint.moveTo(ComplaintStatus.IN_PROGRESS, Actor.STAFF, NOW);
        }
        complaint.moveTo(ComplaintStatus.RESOLVED, Actor.STAFF, NOW);
        entityManager.flush();
    }

    private ResultActions raise(Cookie session, Long flatId, String description, byte[] photo) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/flats/{id}/complaints", flatId);
        request.param("description", description);
        if (photo != null) {
            request.file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photo));
        }
        return mockMvc.perform(request.with(sameOriginCsrf()).cookie(session));
    }

    private ResultActions close(Cookie session, long id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/complaints/{id}/close", id).with(sameOriginCsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions reopen(Cookie session, long id, String reason) throws Exception {
        return mockMvc.perform(post("/api/complaints/{id}/reopen", id).with(sameOriginCsrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
