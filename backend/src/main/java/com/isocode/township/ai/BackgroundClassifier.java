package com.isocode.township.ai;

import com.isocode.township.complaint.ComplaintClassificationUpdater;
import com.isocode.township.complaint.ComplaintRaised;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BackgroundClassifier {

    private static final Logger log = LoggerFactory.getLogger(BackgroundClassifier.class);

    private final ClassificationService classificationService;
    private final ComplaintClassificationUpdater updater;

    public BackgroundClassifier(ClassificationService classificationService, ComplaintClassificationUpdater updater) {
        this.classificationService = classificationService;
        this.updater = updater;
    }

    @Async(ClassificationExecutorConfiguration.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void classifyAfterCommit(ComplaintRaised raised) {
        try {
            updater.apply(raised.complaintId(), classificationService.classify(raised.description()));
        } catch (RuntimeException failure) {
            log.error("Background classification of complaint {} failed; the stale sweep will close it",
                    raised.complaintId(), failure);
        }
    }
}
