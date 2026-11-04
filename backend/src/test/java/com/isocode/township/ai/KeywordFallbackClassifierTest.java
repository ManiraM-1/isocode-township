package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.isocode.township.complaint.ComplaintCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeywordFallbackClassifierTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Water is leaking from the bathroom ceiling | PLUMBING",
            "Pani nahi aa raha subah se | PLUMBING",
            "The lift is not working since morning | LIFT",
            "Corridor lights are flickering | ELECTRICAL",
            "कचरा दो दिन से नहीं उठाया | HOUSEKEEPING",
            "Cockroaches all over the kitchen | PEST_CONTROL",
            "Someone parked in my slot again | PARKING",
            "Loud music from upstairs every night | NOISE",
            "Cracks in the balcony wall plaster | CIVIL",
            "The guard at the gate was asleep | SECURITY",
            "Please share the meeting date | OTHER"
    })
    void picksTheCategoryWithTheMostKeywordHits(String description, ComplaintCategory expected) {
        assertThat(KeywordFallbackClassifier.classify(description).category()).isEqualTo(expected);
    }

    @Test
    void theReasonNamesTheWordsItMatched() {
        assertThat(KeywordFallbackClassifier.classify("Water leaking from the tap").reason())
                .isEqualTo("Keyword rules matched leak, water, tap");
        assertThat(KeywordFallbackClassifier.classify("Please share the meeting date").reason())
                .isEqualTo("Keyword rules found no category words");
    }
}
