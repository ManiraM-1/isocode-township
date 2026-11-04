package com.isocode.township.ai;

import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ClassifiedBy;
import com.isocode.township.complaint.Complaint;
import com.isocode.township.complaint.ComplaintClassifier;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
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

    public ClassificationService(AiComplaintModel aiModel, SafetyProperties safetyProperties,
                                 SocietySettingsRepository settingsRepository) {
        this.aiModel = aiModel;
        this.safetyRule = new SafetyRule(safetyProperties.terms());
        this.settingsRepository = settingsRepository;
    }

    @Override
    public Complaint.Classification classify(String description) {
        List<String> safetyTerms = safetyRule.check(description);
        try {
            AiVerdict verdict = aiModel.classify(description);
            BigDecimal threshold = settingsRepository.current().getAiConfidenceThreshold();
            boolean unsure = verdict.confidence().compareTo(threshold) < 0;
            return new Complaint.Classification(verdict.category(), escalated(verdict.urgency(), safetyTerms),
                    verdict.category(), verdict.urgency(), verdict.confidence(), verdict.summary(), verdict.reason(),
                    ClassifiedBy.AI, unsure, safetyTerms);
        } catch (AiUnavailableException unavailable) {
            log.warn("AI classification unavailable ({}); using keyword rules", unavailable.reasonCode());
            KeywordFallbackClassifier.Verdict fallback = KeywordFallbackClassifier.classify(description);
            String reason = "AI unavailable: " + unavailable.reasonCode().explanation() + ". " + fallback.reason() + ".";
            return new Complaint.Classification(fallback.category(), escalated(Urgency.MEDIUM, safetyTerms),
                    fallback.category(), Urgency.MEDIUM, KeywordFallbackClassifier.CONFIDENCE, null, reason,
                    ClassifiedBy.RULES_FALLBACK, true, safetyTerms);
        }
    }

    private static Urgency escalated(Urgency suggested, List<String> safetyTerms) {
        return safetyTerms.isEmpty() ? suggested : Urgency.CRITICAL;
    }
}
