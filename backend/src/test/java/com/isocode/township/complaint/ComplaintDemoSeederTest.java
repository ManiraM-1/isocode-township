package com.isocode.township.complaint;

import static com.isocode.township.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.IntegrationTest;
import com.isocode.township.common.DemoDataSeeder;
import com.isocode.township.flat.FlatRepository;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class ComplaintDemoSeederTest {

    @Autowired
    private DemoDataSeeder coreSeeder;

    @Autowired
    private ComplaintDemoSeeder complaintSeeder;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintEventRepository eventRepository;

    @Autowired
    private FlatRepository flatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        coreSeeder.seedIfEmpty();
        complaintSeeder.seed();
    }

    @Test
    void aboutThirtyComplaintsCoverEveryStatusAfterAssignmentAndMostCategories() {
        List<Complaint> complaints = complaintRepository.findAll();

        assertThat(complaints).hasSize(30);
        assertThat(complaints).extracting(Complaint::getStatus)
                .containsAll(EnumSet.complementOf(EnumSet.of(ComplaintStatus.OPEN)))
                .doesNotContain(ComplaintStatus.OPEN);
        assertThat(complaints.stream().map(Complaint::getCategory).collect(Collectors.toSet())).hasSizeGreaterThanOrEqualTo(9);
        assertThat(complaints).extracting(Complaint::getRefNo).allMatch(ref -> ref.matches("CMP-\\d{4}-\\d{4}"))
                .doesNotHaveDuplicates();
    }

    @Test
    void swatiHasExactlyOneOpenComplaintAndItIsTheWaterLeak() {
        List<Complaint> open = active(ComplaintDemoSeeder.SWATI_FLAT);

        assertThat(open).singleElement().satisfies(complaint -> {
            assertThat(complaint.getCategory()).isEqualTo(ComplaintCategory.PLUMBING);
            assertThat(complaint.getDescription()).containsIgnoringCase("leaking");
            assertThat(complaint.getFirstResponseAt()).isNotNull();
        });
    }

    @Test
    void neerajAndAamirHaveNoOpenComplaints() {
        assertThat(active(ComplaintDemoSeeder.NEERAJ_FLAT)).isEmpty();
        assertThat(active(ComplaintDemoSeeder.AAMIR_FLAT)).isEmpty();
    }

    @Test
    void closedComplaintsCarryRatingsAndTwoWereReopened() {
        List<Complaint> complaints = complaintRepository.findAll();

        assertThat(complaints).filteredOn(c -> c.getStatus() == ComplaintStatus.CLOSED)
                .hasSizeGreaterThanOrEqualTo(15)
                .allSatisfy(c -> assertThat(c.getRating()).isPresent());
        assertThat(complaints).filteredOn(c -> c.getReopenCount() > 0).hasSize(2)
                .extracting(Complaint::getStatus)
                .containsExactlyInAnyOrder(ComplaintStatus.ASSIGNED, ComplaintStatus.CLOSED);
    }

    @Test
    void someFirstResponsesMissedTheirTargetSoTheMetricsHaveSomethingToShow() {
        Integer breached = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM complaint WHERE first_response_at > first_response_due_at", Integer.class);
        Integer stillWaitingAndLate = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM complaint WHERE first_response_at IS NULL AND first_response_due_at < ?",
                Integer.class, Timestamp.from(NOW));

        assertThat(breached).isBetween(3, 10);
        assertThat(stillWaitingAndLate).isPositive();
    }

    @Test
    void nothingHappensInTheFutureAndEveryTimelineStartsWithCreation() {
        for (Complaint complaint : complaintRepository.findAll()) {
            List<ComplaintEvent> events = eventRepository.findByComplaintIdOrderByCreatedAtAscIdAsc(complaint.getId());
            assertThat(events.getFirst().getType()).isEqualTo(ComplaintEventType.CREATED);
            assertThat(events).allSatisfy(event -> assertThat(event.getCreatedAt()).isBeforeOrEqualTo(NOW));
        }
    }

    @Test
    void noLiftComplaintIsInTowerSevenWhereStepElevenSeedsDuplicates() {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM complaint c JOIN flat f ON f.id = c.flat_id
                WHERE c.category = 'LIFT' AND f.code LIKE 'T07-%'
                """, Integer.class)).isZero();
    }

    private List<Complaint> active(String flatCode) {
        Long flatId = flatRepository.findByCode(flatCode).orElseThrow().getId();
        return complaintRepository.findByFlatIdOrderByCreatedAtDescIdDesc(flatId).stream()
                .filter(complaint -> complaint.getStatus().isActive())
                .toList();
    }
}
