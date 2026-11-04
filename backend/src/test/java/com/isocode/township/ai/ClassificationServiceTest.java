package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.isocode.township.ai.AiUnavailableException.Cause;
import com.isocode.township.common.SocietySettings;
import com.isocode.township.common.SocietySettingsRepository;
import com.isocode.township.complaint.ClassificationStatus;
import com.isocode.township.complaint.ClassifiedBy;
import com.isocode.township.complaint.Complaint.Classification;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.complaint.Urgency;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClassificationServiceTest {

    private static final List<String> SAFETY_TERMS = List.of("fire", "gas", "gas leak", "lift stuck", "गैस", "गॅसचा");

    private AiComplaintModel ai;
    private ClassificationService service;

    @BeforeEach
    void setUp() {
        ai = mock(AiComplaintModel.class);
        SocietySettings settings = mock(SocietySettings.class);
        when(settings.getAiConfidenceThreshold()).thenReturn(new BigDecimal("0.60"));
        SocietySettingsRepository settingsRepository = mock(SocietySettingsRepository.class);
        when(settingsRepository.current()).thenReturn(settings);
        service = new ClassificationService(ai, new SafetyProperties(SAFETY_TERMS), settingsRepository,
                new GeminiProperties(null, null, null, null, Duration.ZERO));
    }

    @Test
    void safetyWordsMakeItCriticalEvenWhenTheAiSaysLow() {
        aiSays(ComplaintCategory.OTHER, Urgency.LOW, "0.95");

        Classification result = service.classify("Strong gas leak smell near the stairs");

        assertThat(result.urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(result.aiUrgency()).isEqualTo(Urgency.LOW);
        assertThat(result.safetyTerms()).containsExactly("gas", "gas leak");
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.AI);
    }

    @Test
    void hindiAndMarathiSafetyWordsAlsoOverrideTheAi() {
        aiSays(ComplaintCategory.OTHER, Urgency.LOW, "0.95");

        assertThat(service.classify("रसोई में गैस की बदबू").urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(service.classify("स्वयंपाकघरात गॅसचा वास").urgency()).isEqualTo(Urgency.CRITICAL);
    }

    @Test
    void theSafetyRuleStillWorksWhenTheAiIsDown() {
        when(ai.classify(anyString())).thenThrow(new AiUnavailableException(Cause.TIMEOUT, "slow"));

        Classification result = service.classify("Lift stuck with two people inside");

        assertThat(result.urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(result.safetyTerms()).containsExactly("lift stuck");
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULES_FALLBACK);
    }

    @Test
    void anAiTimeoutFallsBackToKeywordsAndGoesToReview() {
        when(ai.classify(anyString())).thenThrow(new AiUnavailableException(Cause.TIMEOUT, "slow"));

        Classification result = service.classify("Water is leaking from the bathroom ceiling");

        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULES_FALLBACK);
        assertThat(result.needsReview()).isTrue();
        assertThat(result.category()).isEqualTo(ComplaintCategory.PLUMBING);
        assertThat(result.urgency()).isEqualTo(Urgency.MEDIUM);
        assertThat(result.aiConfidence()).isEqualTo(new BigDecimal("0.50"));
        assertThat(result.aiReason()).isEqualTo("AI unavailable: the AI did not answer within the time limit. "
                + "Keyword rules matched leak, water.");
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    void everyKindOfAiFailureFallsBackSafely(Cause cause) {
        when(ai.classify(anyString())).thenThrow(new AiUnavailableException(cause, "scripted"));

        Classification result = service.classify("The corridor light is flickering");

        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULES_FALLBACK);
        assertThat(result.needsReview()).isTrue();
        assertThat(result.category()).isEqualTo(ComplaintCategory.ELECTRICAL);
    }

    @Test
    void lowConfidenceGoesToReviewButKeepsTheAiAnswer() {
        aiSays(ComplaintCategory.PARKING, Urgency.LOW, "0.40");

        Classification result = service.classify("Something is wrong near the parking, please send someone");

        assertThat(result.needsReview()).isTrue();
        assertThat(result.category()).isEqualTo(ComplaintCategory.PARKING);
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.AI);
    }

    @Test
    void confidenceAtTheThresholdIsTrusted() {
        aiSays(ComplaintCategory.PARKING, Urgency.LOW, "0.60");

        assertThat(service.classify("Someone parked in my slot").needsReview()).isFalse();
    }

    @Test
    void theAiCanRaiseUrgencyOnItsOwnWithoutSafetyTerms() {
        aiSays(ComplaintCategory.PLUMBING, Urgency.CRITICAL, "0.90");

        Classification result = service.classify("Water is flooding the corridor from a burst pipe");

        assertThat(result.urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(result.safetyTerms()).isEmpty();
    }

    @Test
    void theProvisionalResultUsesKeywordsAndTheSafetyRuleWithoutAskingTheAi() {
        Classification result = service.provisional("Gas leak and water dripping in the kitchen");

        assertThat(result.status()).isEqualTo(ClassificationStatus.PENDING);
        assertThat(result.classifiedBy()).isEqualTo(ClassifiedBy.RULES_FALLBACK);
        assertThat(result.urgency()).isEqualTo(Urgency.CRITICAL);
        assertThat(result.category()).isEqualTo(ComplaintCategory.PLUMBING);
        assertThat(result.needsReview()).isFalse();
        verify(ai, times(0)).classify(anyString());
    }

    @Test
    void aFailedCallIsRetriedOnceAndTheSecondAnswerIsUsed() {
        when(ai.classify(anyString()))
                .thenThrow(new AiUnavailableException(Cause.TIMEOUT, "slow"))
                .thenReturn(new AiVerdict(ComplaintCategory.LIFT, Urgency.HIGH, new BigDecimal("0.90"), "Lift", "Lift"));

        Classification result = service.classify("The lift is making a grinding noise");

        assertThat(result.status()).isEqualTo(ClassificationStatus.DONE);
        assertThat(result.category()).isEqualTo(ComplaintCategory.LIFT);
        verify(ai, times(2)).classify(anyString());
    }

    @Test
    void twoFailuresFallBackAndAMissingKeyIsNotRetried() {
        when(ai.classify(anyString())).thenThrow(new AiUnavailableException(Cause.ERROR, "down"));
        assertThat(service.classify("The lift is making a grinding noise").status())
                .isEqualTo(ClassificationStatus.FAILED);
        verify(ai, times(2)).classify(anyString());

        AiComplaintModel unconfigured = mock(AiComplaintModel.class);
        when(unconfigured.classify(anyString())).thenThrow(new AiUnavailableException(Cause.NOT_CONFIGURED, "no key"));
        SocietySettingsRepository settingsRepository = mock(SocietySettingsRepository.class);
        new ClassificationService(unconfigured, new SafetyProperties(SAFETY_TERMS), settingsRepository,
                new GeminiProperties(null, null, null, null, Duration.ZERO)).classify("The lift is noisy");
        verify(unconfigured, times(1)).classify(anyString());
    }

    private void aiSays(ComplaintCategory category, Urgency urgency, String confidence) {
        when(ai.classify(anyString())).thenReturn(new AiVerdict(category, urgency, new BigDecimal(confidence),
                "Summary", "Reason"));
    }
}
