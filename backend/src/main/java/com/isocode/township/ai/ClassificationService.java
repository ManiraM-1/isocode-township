package com.isocode.township.ai;

import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ClassificationStatus;
import com.isocode.township.complaint.ClassifiedBy;
import com.isocode.township.complaint.Complaint;
import com.isocode.township.complaint.ComplaintClassifier;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClassificationService implements ComplaintClassifier {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final AiComplaintModel aiModel;
    private final SafetyRule safetyRule;
    private final SocietySettingsRepository settingsRepository;
    private final Duration retryDelay;

    public ClassificationService(AiComplaintModel aiModel, SafetyProperties safetyProperties,
                                 SocietySettingsRepository settingsRepository, GeminiProperties geminiProperties) {
        this.aiModel = aiModel;
        this.safetyRule = new SafetyRule(safetyProperties.terms());
        this.settingsRepository = settingsRepository;
        this.retryDelay = geminiProperties.retryDelay() == null ? Duration.ZERO : geminiProperties.retryDelay();
    }

    @Override
    public Complaint.Classification provisional(String description) {
        List<String> safetyTerms = safetyRule.check(description);
        KeywordFallbackClassifier.Verdict fallback = KeywordFallbackClassifier.classify(description);
        return keywordClassification(fallback, safetyTerms, fallback.reason() + ".", false, ClassificationStatus.PENDING);
    }

    @Override
    public Complaint.Classification classify(String description) {
        List<String> safetyTerms = safetyRule.check(description);
        try {
            AiVerdict verdict = askWithOneRetry(description);
            BigDecimal threshold = settingsRepository.current().getAiConfidenceThreshold();
            boolean unsure = verdict.confidence().compareTo(threshold) < 0;
            return new Complaint.Classification(verdict.category(), escalated(verdict.urgency(), safetyTerms),
                    verdict.category(), verdict.urgency(), verdict.confidence(), verdict.summary(), verdict.reason(),
                    ClassifiedBy.AI, unsure, safetyTerms, ClassificationStatus.DONE);
        } catch (AiUnavailableException unavailable) {
            log.warn("AI classification unavailable ({}); using keyword rules", unavailable.reasonCode());
            KeywordFallbackClassifier.Verdict fallback = KeywordFallbackClassifier.classify(description);
            String reason = "AI unavailable: " + unavailable.reasonCode().explanation() + ". " + fallback.reason() + ".";
            return keywordClassification(fallback, safetyTerms, reason, true, ClassificationStatus.FAILED);
        }
    }

    private AiVerdict askWithOneRetry(String description) {
        try {
            return aiModel.classify(description);
        } catch (AiUnavailableException first) {
            if (first.reasonCode() == Cause.NOT_CONFIGURED) {
                throw first;
            }
            log.info("AI classification failed ({}); retrying once", first.reasonCode());
            pauseBeforeRetry();
            return aiModel.classify(description);
        }
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(Cause.ERROR, "interrupted before retry");
        }
    }

    private static Complaint.Classification keywordClassification(KeywordFallbackClassifier.Verdict fallback,
                                                                  List<String> safetyTerms, String reason,
                                                                  boolean needsReview, ClassificationStatus status) {
        return new Complaint.Classification(fallback.category(), escalated(Urgency.MEDIUM, safetyTerms),
                fallback.category(), Urgency.MEDIUM, KeywordFallbackClassifier.CONFIDENCE, null, reason,
                ClassifiedBy.RULES_FALLBACK, needsReview, safetyTerms, status);
    }

    private static Urgency escalated(Urgency suggested, List<String> safetyTerms) {
        return safetyTerms.isEmpty() ? suggested : Urgency.CRITICAL;
    }
}
