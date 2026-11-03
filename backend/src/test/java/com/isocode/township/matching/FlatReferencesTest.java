package com.isocode.township.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlatReferencesTest {

    private static final Set<String> KNOWN = Set.of("T07-1203", "T07-1401", "T07-1407", "T26-1203");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "T07-1203", "T7-1203", "T07/1203", "T07 1203", "T071203", "TOWER 7 FLAT 1203",
            "t07-1203", "maint t7 1203 nov", "Tower 07, Flat No. 1203", "UPI/CR/630112457788/T07-1203",
            "T 07-1203", "T07 - 1203"
    })
    void everyAcceptedFormNormalisesToTheFlatCode(String text) {
        assertThat(FlatReferences.flatCodesIn(text, "")).containsExactly("T07-1203");
    }

    @Test
    void referenceColumnIsSearchedToo() {
        assertThat(FlatReferences.flatCodesIn("NEFT CR", "T26/1203")).containsExactly("T26-1203");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "NEFT DR-MSEDCL BILL PAYMENT OCT-2026", "SMS ALERT CHARGES SEPT 2026", "PG SETTLEMENT PGTEST-JD9EP9VAU8CP",
            "UPI/CR/631245678901/MAINTENANCE", "N305260011122233", "AT 12 0408", "FLAT 1203", "T123-4567"
    })
    void textWithoutAFlatReferenceFindsNothing(String text) {
        assertThat(FlatReferences.flatCodesIn(text, "")).isEmpty();
    }

    @Test
    void twoKnownFlatsMakeTheReferenceAmbiguous() {
        FlatReferences.ReferenceSignal signal =
                FlatReferences.resolve("MAINT T07-1401 AND T07-1407", "", KNOWN);

        assertThat(signal.isAmbiguous()).isTrue();
        assertThat(signal.knownCodes()).containsExactly("T07-1401", "T07-1407");
    }

    @Test
    void theSameFlatWrittenTwiceIsNotAmbiguous() {
        FlatReferences.ReferenceSignal signal = FlatReferences.resolve("T7-1203 maint", "T07/1203", KNOWN);

        assertThat(signal.namesOneFlat()).isTrue();
    }

    @Test
    void codesThatAreNotFlatsAreKeptApart() {
        FlatReferences.ReferenceSignal signal = FlatReferences.resolve("T99-0101 and T07-1203", "", KNOWN);

        assertThat(signal.knownCodes()).containsExactly("T07-1203");
        assertThat(signal.unknownCodes()).containsExactly("T99-0101");
    }
}
