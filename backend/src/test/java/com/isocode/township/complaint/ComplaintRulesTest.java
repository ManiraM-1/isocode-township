package com.isocode.township.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.common.ConflictException;
import com.isocode.township.complaint.AutoAssigner.StaffLoad;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComplaintRulesTest {

    private static final Instant RAISED = Instant.parse("2026-11-03T04:30:00Z");
    private static final SlaTargets A14 = SlaTargets.of(Map.of(
            Urgency.CRITICAL, new int[] {15, 240},
            Urgency.HIGH, new int[] {120, 1440},
            Urgency.MEDIUM, new int[] {480, 4320},
            Urgency.LOW, new int[] {1440, 10080}));

    private static final StaffLoad ANJALI = new StaffLoad(1L, EnumSet.allOf(ComplaintCategory.class), 0);
    private static final StaffLoad RAVI = new StaffLoad(2L, EnumSet.of(ComplaintCategory.PLUMBING, ComplaintCategory.CIVIL), 5);
    private static final StaffLoad PLUMBER_TWO = new StaffLoad(7L, EnumSet.of(ComplaintCategory.PLUMBING), 2);
    private static final StaffLoad PRAKASH = new StaffLoad(4L, EnumSet.of(ComplaintCategory.LIFT), 1);

    @Test
    void responseTargetsFollowTheUrgency() {
        assertThat(A14.firstResponseDue(RAISED, Urgency.CRITICAL)).isEqualTo(RAISED.plusSeconds(15 * 60));
        assertThat(A14.resolveDue(RAISED, Urgency.CRITICAL)).isEqualTo(RAISED.plusSeconds(4 * 3600));
        assertThat(A14.firstResponseDue(RAISED, Urgency.MEDIUM)).isEqualTo(RAISED.plusSeconds(8 * 3600));
        assertThat(A14.resolveDue(RAISED, Urgency.LOW)).isEqualTo(RAISED.plusSeconds(7 * 24 * 3600));
    }

    @Test
    void theSpecialistWithFewestOpenComplaintsGetsIt() {
        assertThat(AutoAssigner.pick(ComplaintCategory.PLUMBING, List.of(ANJALI, RAVI, PLUMBER_TWO, PRAKASH)))
                .contains(PLUMBER_TWO);
    }

    @Test
    void theGeneralDeskTakesCategoriesNoSpecialistCovers() {
        assertThat(AutoAssigner.pick(ComplaintCategory.NOISE, List.of(ANJALI, RAVI, PRAKASH))).contains(ANJALI);
    }

    @Test
    void theGeneralDeskDoesNotCompeteWithABusySpecialist() {
        assertThat(AutoAssigner.pick(ComplaintCategory.LIFT, List.of(ANJALI, PRAKASH))).contains(PRAKASH);
    }

    @Test
    void tiesGoToTheLongestServingStaffMember() {
        StaffLoad newer = new StaffLoad(9L, EnumSet.of(ComplaintCategory.LIFT), 1);

        assertThat(AutoAssigner.pick(ComplaintCategory.LIFT, List.of(newer, PRAKASH))).contains(PRAKASH);
    }

    @Test
    void nobodyToAssignLeavesItOpen() {
        assertThat(AutoAssigner.pick(ComplaintCategory.NOISE, List.of(RAVI))).isEmpty();
    }

    @Test
    void aSpecialistWithTheSkillSuitsTheCategory() {
        List<StaffLoad> staff = List.of(ANJALI, RAVI, PLUMBER_TWO, PRAKASH);

        assertThat(AutoAssigner.suits(ComplaintCategory.CIVIL, RAVI.staffId(), staff)).isTrue();
        assertThat(AutoAssigner.suits(ComplaintCategory.LIFT, RAVI.staffId(), staff)).isFalse();
    }

    @Test
    void theGeneralDeskSuitsOnlyCategoriesNoSpecialistCovers() {
        List<StaffLoad> staff = List.of(ANJALI, RAVI, PRAKASH);

        assertThat(AutoAssigner.suits(ComplaintCategory.NOISE, ANJALI.staffId(), staff)).isTrue();
        assertThat(AutoAssigner.suits(ComplaintCategory.LIFT, ANJALI.staffId(), staff)).isFalse();
        assertThat(AutoAssigner.suits(ComplaintCategory.LIFT, null, staff)).isFalse();
    }

    @Test
    void changingUrgencyKeepsTheResolutionClockStartAndAppliesTheNewTarget() {
        Instant reopenedAt = RAISED.plusSeconds(3 * 24 * 3600);
        Instant mediumDue = A14.resolveDue(reopenedAt, Urgency.MEDIUM);

        assertThat(A14.rescheduleResolve(mediumDue, Urgency.MEDIUM, Urgency.HIGH))
                .isEqualTo(A14.resolveDue(reopenedAt, Urgency.HIGH));
        assertThat(A14.rescheduleResolve(mediumDue, Urgency.MEDIUM, Urgency.MEDIUM)).isEqualTo(mediumDue);
    }

    @Test
    void aClosedComplaintCannotBeRecategorised() {
        Complaint complaint = new Complaint("CMP-2611-0001", 1L, 2L, "Tap is dripping all day", new Complaint.Classification(
                ComplaintCategory.PLUMBING, Urgency.LOW, null, null, null, null, null, ClassifiedBy.AI, false, List.of()),
                RAISED, RAISED, RAISED);
        complaint.assignTo(RAVI.staffId());
        complaint.moveTo(ComplaintStatus.RESOLVED, ComplaintStateMachine.Actor.STAFF, RAISED);
        complaint.close(5, null, RAISED);

        assertThatThrownBy(() -> complaint.recategorise(ComplaintCategory.CIVIL, Urgency.LOW, RAISED, RAISED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A closed complaint cannot be recategorised.");
    }

    @Test
    void photosAreRecognisedByTheirFirstBytesNotTheirName() {
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0})).contains(ImageType.JPEG);
        assertThat(ImageType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contains(ImageType.PNG);
        assertThat(ImageType.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).contains(ImageType.WEBP);
    }

    @Test
    void anythingElseIsNotAPhoto() {
        assertThat(ImageType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}
