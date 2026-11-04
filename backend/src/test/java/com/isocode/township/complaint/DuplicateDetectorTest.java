package com.isocode.township.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.complaint.DuplicateDetector.Candidate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

    private static final Instant NOW = Instant.parse("2026-11-03T04:30:00Z");
    private static final long T07 = 7L;
    private static final long T12 = 12L;
    private final DuplicateDetector detector = new DuplicateDetector(Duration.ofHours(24), Duration.ofHours(72), 0.35);

    @Test
    void liftComplaintsInTheSameTowerAreLinkedOnTowerAndCategoryAlone() {
        Candidate first = lift(1L, 101L, T07, NOW.minus(Duration.ofHours(3)), "Lift is not working since morning");
        Candidate next = lift(2L, 102L, T07, NOW, "Elevator stopped on the ground floor, doors do not open");

        assertThat(detector.findParent(next, List.of(first))).contains(1L);
    }

    @Test
    void aLiftComplaintInAnotherTowerOrAfterADayIsNotLinked() {
        Candidate otherTower = lift(1L, 201L, T12, NOW.minus(Duration.ofHours(1)), "Lift is not working");
        Candidate dayOld = lift(3L, 103L, T07, NOW.minus(Duration.ofHours(25)), "Lift is not working");
        Candidate next = lift(2L, 102L, T07, NOW, "Lift is not working");

        assertThat(detector.findParent(next, List.of(otherTower, dayOld))).isEmpty();
    }

    @Test
    void similarPlumbingTextFromAnotherFlatIsLinkedButDissimilarTextIsNot() {
        Candidate outage = plumbing(1L, 101L, NOW.minus(Duration.ofHours(5)), "No water supply in our flat since morning");
        Candidate sameOutage = plumbing(2L, 102L, NOW, "No water supply since morning in the whole flat");
        Candidate tap = plumbing(3L, 103L, NOW, "Kitchen tap is dripping all night");

        assertThat(detector.findParent(sameOutage, List.of(outage))).contains(1L);
        assertThat(detector.findParent(tap, List.of(outage))).isEmpty();
    }

    @Test
    void similarTextIsNotLinkedAfterThreeDays() {
        Candidate old = plumbing(1L, 101L, NOW.minus(Duration.ofHours(73)), "No water supply since morning");

        assertThat(detector.findParent(plumbing(2L, 102L, NOW, "No water supply since morning"), List.of(old))).isEmpty();
    }

    @Test
    void aClosedComplaintTheSameFlatOrAnotherCategoryIsNeverAParent() {
        Candidate closed = new Candidate(1L, 101L, T07, ComplaintCategory.LIFT, ComplaintStatus.CLOSED,
                NOW.minus(Duration.ofHours(1)), "Lift stuck", null);
        Candidate sameFlat = lift(3L, 102L, T07, NOW.minus(Duration.ofHours(1)), "Lift stuck");
        Candidate otherCategory = new Candidate(4L, 104L, T07, ComplaintCategory.ELECTRICAL, ComplaintStatus.ASSIGNED,
                NOW.minus(Duration.ofHours(1)), "Lift lobby light is off", null);

        assertThat(detector.findParent(lift(2L, 102L, T07, NOW, "Lift stuck"), List.of(closed, sameFlat, otherCategory)))
                .isEmpty();
    }

    @Test
    void aResolvedComplaintCanStillBeAParent() {
        Candidate resolved = new Candidate(1L, 101L, T07, ComplaintCategory.LIFT, ComplaintStatus.RESOLVED,
                NOW.minus(Duration.ofHours(2)), "Lift stuck", null);

        assertThat(detector.findParent(lift(2L, 102L, T07, NOW, "Lift stuck again"), List.of(resolved))).contains(1L);
    }

    @Test
    void itLinksToTheEarliestRootSoThereAreNoChains() {
        Candidate root = lift(1L, 101L, T07, NOW.minus(Duration.ofHours(4)), "Lift not working");
        Candidate child = new Candidate(2L, 102L, T07, ComplaintCategory.LIFT, ComplaintStatus.ASSIGNED,
                NOW.minus(Duration.ofHours(3)), "Lift not working", 1L);
        Candidate childOfOutsideRoot = new Candidate(5L, 105L, T07, ComplaintCategory.LIFT, ComplaintStatus.ASSIGNED,
                NOW.minus(Duration.ofHours(2)), "Lift dead", 1L);

        assertThat(detector.findParent(lift(3L, 103L, T07, NOW, "Lift dead"), List.of(child, childOfOutsideRoot)))
                .contains(1L);
        assertThat(detector.findParent(lift(3L, 103L, T07, NOW, "Lift dead"), List.of(child, root))).contains(1L);
    }

    @Test
    void aComplaintIsNeverLinkedToItselfOrToItsOwnChild() {
        Candidate self = lift(1L, 101L, T07, NOW, "Lift not working");
        Candidate ownChild = new Candidate(2L, 102L, T07, ComplaintCategory.LIFT, ComplaintStatus.ASSIGNED,
                NOW, "Lift not working", 1L);

        assertThat(detector.findParent(self, List.of(self, ownChild))).isEmpty();
    }

    @Test
    void similarityIgnoresCasePunctuationAndStopwordsAndWorksInDevanagari() {
        assertThat(DuplicateDetector.similarity("Water is LEAKING, from the ceiling!", "water leaking ceiling"))
                .isEqualTo(1.0);
        assertThat(DuplicateDetector.similarity("पानी नहीं आ रहा", "पानी नहीं आ रहा है")).isEqualTo(1.0);
        assertThat(DuplicateDetector.similarity("", "water")).isZero();
    }

    private static Candidate lift(Long id, Long flatId, long towerId, Instant at, String text) {
        return new Candidate(id, flatId, towerId, ComplaintCategory.LIFT, ComplaintStatus.ASSIGNED, at, text, null);
    }

    private static Candidate plumbing(Long id, Long flatId, Instant at, String text) {
        return new Candidate(id, flatId, T07, ComplaintCategory.PLUMBING, ComplaintStatus.ASSIGNED, at, text, null);
    }
}
