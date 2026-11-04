package com.isocode.township.complaint;

import com.isocode.township.common.SocietySettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintClassificationUpdater {

    private static final Logger log = LoggerFactory.getLogger(ComplaintClassificationUpdater.class);
    static final String GAVE_UP_REASON = "AI unavailable: the AI did not answer in time. ";

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final ComplaintEscalator escalator;
    private final ComplaintHandover handover;
    private final DuplicateLinker duplicateLinker;
    private final SocietySettingsRepository settingsRepository;
    private final Duration giveUpAfter;
    private final Clock clock;

    public ComplaintClassificationUpdater(ComplaintRepository complaintRepository,
                                          ComplaintEventRepository eventRepository, ComplaintEscalator escalator,
                                          ComplaintHandover handover, DuplicateLinker duplicateLinker,
                                          SocietySettingsRepository settingsRepository,
                                          @Value("${app.classification.give-up-after}") Duration giveUpAfter,
                                          Clock clock) {
        this.complaintRepository = complaintRepository;
        this.eventRepository = eventRepository;
        this.escalator = escalator;
        this.handover = handover;
        this.duplicateLinker = duplicateLinker;
        this.settingsRepository = settingsRepository;
        this.giveUpAfter = giveUpAfter;
        this.clock = clock;
    }

    @Transactional
    public void apply(Long complaintId, Complaint.Classification result) {
        Complaint complaint = complaintRepository.findLockedById(complaintId).orElse(null);
        if (complaint == null || complaint.getClassificationStatus() != ClassificationStatus.PENDING) {
            return;
        }
        Instant now = clock.instant();
        boolean staffActed = complaint.hasStaffResponse();
        if (result.status() != ClassificationStatus.DONE) {
            complaint.markClassificationFailed(result.aiReason(), !staffActed);
            return;
        }
        String suggested = label(result.category(), result.urgency());
        if (staffActed) {
            complaint.recordAiSuggestion(result);
            record(complaint, ComplaintEventType.AI_SUGGESTED, label(complaint.getCategory(), complaint.getUrgency()),
                    suggested, result.aiReason(), now);
        } else {
            String provisional = label(complaint.getCategory(), complaint.getUrgency());
            SlaTargets targets = SlaTargets.from(settingsRepository.current());
            complaint.applyAiClassification(result, targets.firstResponseDue(complaint.getCreatedAt(), result.urgency()),
                    targets.resolveDue(complaint.getCreatedAt(), result.urgency()));
            record(complaint, ComplaintEventType.CLASSIFIED, provisional, suggested, result.aiReason(), now);
            handover.handOverIfOwnerLacksTheSkill(complaint, now);
            duplicateLinker.relinkAfterCategoryChange(complaint, now);
        }
        escalator.escalate(complaint, now);
    }

    @Scheduled(fixedDelayString = "${app.classification.sweep-every-ms}",
            initialDelayString = "${app.classification.sweep-every-ms}")
    @Transactional
    public int giveUpOnStaleClassifications() {
        Instant cutoff = clock.instant().minus(giveUpAfter);
        int gaveUp = 0;
        for (Complaint stale : complaintRepository.findByClassificationStatusAndCreatedAtBefore(
                ClassificationStatus.PENDING, cutoff)) {
            stale.markClassificationFailed(GAVE_UP_REASON + stale.getAiReason(), !stale.hasStaffResponse());
            gaveUp++;
        }
        if (gaveUp > 0) {
            log.warn("Gave up waiting for the AI on {} complaint(s); they keep the keyword classification", gaveUp);
        }
        return gaveUp;
    }

    private void record(Complaint complaint, ComplaintEventType type, String from, String to, String note,
                        Instant at) {
        eventRepository.save(new ComplaintEvent(complaint.getId(), type, null, from, to, note, at));
    }

    private static String label(ComplaintCategory category, Urgency urgency) {
        return category.name() + "/" + urgency.name();
    }
}
