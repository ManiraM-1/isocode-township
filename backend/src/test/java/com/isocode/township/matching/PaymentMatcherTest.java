package com.isocode.township.matching;

import static com.isocode.township.matching.MatchFixtures.AAMIR;
import static com.isocode.township.matching.MatchFixtures.NEERAJ;
import static com.isocode.township.matching.MatchFixtures.SWATI;
import static com.isocode.township.matching.MatchFixtures.T07_1401;
import static com.isocode.township.matching.MatchFixtures.T07_1407;
import static com.isocode.township.matching.MatchFixtures.T12_1108;
import static com.isocode.township.matching.MatchFixtures.T15_0702;
import static com.isocode.township.matching.MatchFixtures.T15_0903;
import static com.isocode.township.matching.MatchFixtures.T15_1605;
import static com.isocode.township.matching.MatchFixtures.T21_0104;
import static com.isocode.township.matching.MatchFixtures.T21_0303;
import static com.isocode.township.matching.MatchFixtures.T21_1402;
import static com.isocode.township.matching.MatchFixtures.T26_1203;
import static com.isocode.township.matching.MatchFixtures.T26_2007;
import static com.isocode.township.matching.MatchFixtures.credit;
import static com.isocode.township.matching.MatchFixtures.debit;
import static com.isocode.township.matching.MatchFixtures.seedLikeContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.isocode.township.matching.MatchResult.Candidate;
import com.isocode.township.matching.MatchResult.DuplicateOf;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PaymentMatcherTest {

    private final MatchContext context = seedLikeContext().build();

    @Nested
    class DecisionTableRows {

        @Test
        void row1_utrAlreadyPostedIsADuplicateThatIsNeitherPostedNorQueued() {
            MatchResult result = match(credit(2, "UPI/CR/699747290912/NEERAJ KULKARNI/T07-1203 NOV", "699747290912", "7475.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.DUPLICATE);
            assertThat(result.decision().isPostedAutomatically()).isFalse();
            assertThat(result.decision().goesToAccountsQueue()).isFalse();
            assertThat(result.reasons()).containsExactly("UTR 699747290912 was already posted as receipt RCPT-202611-00103");
        }

        @Test
        void row2_debitIsNonCredit() {
            assertThat(match(debit(2, "NEFT DR-MSEDCL BILL PAYMENT", "125640.00")).decision())
                    .isEqualTo(MatchDecision.NON_CREDIT);
        }

        @Test
        void row3_sameRowHashWithoutUtrIsAPossibleDuplicateThatIsQueuedNotPosted() {
            StatementRow cash = credit(19, "CASH DEP T15-0801 MAINT KOREGAON PARK BR", "", "3500.00");
            StatementRow repeat = credit(20, "CASH DEP T15-0801 MAINT KOREGAON PARK BR", "", "3500.00");

            List<MatchResult> results = PaymentMatcher.matchAll(List.of(cash, repeat), context);

            assertThat(results.get(0).decision()).isEqualTo(MatchDecision.SUGGESTED);
            MatchResult second = results.get(1);
            assertThat(second.decision()).isEqualTo(MatchDecision.POSSIBLE_DUPLICATE);
            assertThat(second.decision().goesToAccountsQueue()).isTrue();
            assertThat(second.duplicateOf()).contains(DuplicateOf.thisImport(19));
            assertThat(second.flatId()).contains(MatchFixtures.T15_0801);
            assertThat(second.reasons().getFirst())
                    .isEqualTo("Same date, description, reference and amount as row 19 of this file, and no UTR to tell them apart");
        }

        @Test
        void row4_gatewayReferenceWithEqualAmountReconcilesToTheExistingPayment() {
            MatchResult result = match(credit(2, "PG SETTLEMENT PGTEST-JD9EP9VAU8CP TESTPAY", "PGTEST-JD9EP9VAU8CP", "3000.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.RECONCILED_GATEWAY);
            assertThat(result.decision().isPostedAutomatically()).isFalse();
            assertThat(result.gatewayPaymentId()).contains(501L);
            assertThat(result.flatId()).contains(T15_0903);
        }

        @Test
        void row5_gatewayReferenceWithADifferentAmountIsAConflict() {
            MatchResult result = match(credit(2, "PG SETTLEMENT PGTEST-SLAMTBLQNZXA TESTPAY", "PGTEST-SLAMTBLQNZXA", "10412.50"));

            assertThat(result.decision()).isEqualTo(MatchDecision.CONFLICT);
            assertThat(result.reasons()).containsExactly("Gateway reference PGTEST-SLAMTBLQNZXA is receipt "
                    + "RCPT-202611-00102 for ₹10,432.50 (T03-0904), but the bank shows ₹10,412.50");
        }

        @Test
        void row6_ambiguousReferenceIsAConflictWithBothFlatsAsCandidates() {
            MatchResult result = match(credit(2, "NEFT CR-ICIC0000456-MAINT T07-1401 AND T07-1407", "N307260022233344", "3000.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.CONFLICT);
            assertThat(result.candidates()).extracting(Candidate::flatId).containsExactly(T07_1401, T07_1407);
        }

        @Test
        void row6_referenceAndUpiIdNamingDifferentFlatsIsAConflict() {
            MatchResult result = match(credit(2, "UPI/CR/631702334620/AAMIR SHAIKH/aamir.s@okicici/T07-1203 for Neeraj",
                    "631702334620", "2500.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.CONFLICT);
            assertThat(result.candidates()).extracting(Candidate::flatId).containsExactly(NEERAJ, AAMIR);
            assertThat(result.reasons()).contains("Reference and UPI ID name different flats: T07-1203 and T21-1601");
        }

        @Test
        void row7_referenceAndUpiIdAgreeingIsAutoPostedWithHighConfidence() {
            MatchResult result = match(credit(2, "UPI/CR/630112457788/AAMIR SHAIKH/aamir.s@okicici/T21-1601 Oct maintenance",
                    "630112457788", "5660.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(AAMIR);
            assertThat(result.confidence()).contains(Confidence.HIGH);
            assertThat(result.reasons()).containsExactly(
                    "Reference T21-1601 found",
                    "UPI aamir.s@okicici is known for T21-1601",
                    "Amount ₹5,660.00 equals the remaining amount of T21-1601's October 2026 bill");
        }

        @Test
        void row8_referenceConfirmedByAUniqueAmountOnTheSameFlatIsAutoPosted() {
            MatchResult result = match(credit(2, "NEFT CR-HDFC0001234-SWATI JOSHI-T12/0408 SEP MAINTENANCE",
                    "N305260011122233", "6370.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(SWATI);
        }

        @Test
        void row9_referenceAloneIsASuggestion() {
            MatchResult result = match(credit(2, "IMPS/P2A/631245678901/TOWER 15 FLAT 0702/MAINTENANCE", "631245678901", "3000.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T15_0702);
            assertThat(result.confidence()).contains(Confidence.MEDIUM);
        }

        @Test
        void row10_uniqueAmountAloneIsASuggestion() {
            MatchResult result = match(credit(2, "UPI/CR/631590113478/SHREE ENTERPRISES/shree.ent@ybl/MAINTENANCE",
                    "631590113478", "4070.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T12_1108);
            assertThat(result.reasons()).containsExactly(
                    "UPI shree.ent@ybl is not linked to any flat yet",
                    "Amount ₹4,070.00 equals the remaining amount of T12-1108's October 2026 bill");
        }

        @Test
        void row11_amountOnSeveralFlatsIsUnmatchedWithEveryFlatAsACandidate() {
            MatchResult result = match(credit(2, "IMPS/P2A/631688224519/MAINT NOV", "631688224519", "6207.50"));

            assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
            assertThat(result.confidence()).contains(Confidence.LOW);
            assertThat(result.candidates()).extracting(Candidate::flatId).containsExactlyInAnyOrder(T21_0104, T21_1402);
        }

        @Test
        void row12_nothingMatchesIsUnmatched() {
            MatchResult result = match(credit(2, "NEFT CR-SBIN0004321-SHREE SAI CATERERS-DEPOSIT REFUND",
                    "N307260033344455", "123450.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
            assertThat(result.candidates()).isEmpty();
            assertThat(result.reasons()).containsExactly("No flat reference, known UPI ID or matching amount");
        }
    }

    @Nested
    class AmountIsOnlyASupportingSignal {

        @Test
        void referenceAndUpiIdAgreeWhileTheAmountPointsElsewhere() {
            MatchResult result = match(credit(2, "UPI/CR/631911445731/KALE/kale.family@okaxis/T21-0303 part",
                    "631911445731", "4070.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(T21_0303);
            assertThat(result.reasons()).contains("Amount ₹4,070.00 also equals the remaining amount of T12-1108's "
                    + "October 2026 bill; ignored because reference and UPI ID agree");
        }

        @Test
        void referenceAloneWithTheAmountPointingElsewhereStaysASuggestion() {
            MatchResult result = match(credit(2, "IMPS/P2A/632000000001/T15-0702 MAINT", "632000000001", "4070.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T15_0702);
            assertThat(result.reasons()).contains("Amount ₹4,070.00 also equals the remaining amount of T12-1108's "
                    + "October 2026 bill; ignored because the reference names T15-0702");
        }

        @Test
        void upiIdAloneWithTheAmountPointingElsewhereStaysASuggestionNotAConflict() {
            MatchResult result = match(credit(2, "UPI/CR/632000000002/DHOLE/dhole.home@okaxis/NOV", "632000000002", "4070.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T26_2007);
            assertThat(result.reasons()).contains("Amount ₹4,070.00 also equals the remaining amount of T12-1108's "
                    + "October 2026 bill; ignored because the UPI ID is known for T26-2007");
        }

        @Test
        void upiIdAloneIsASuggestion() {
            MatchResult result = match(credit(2, "UPI/CR/631377002145/DHOLE/dhole.home@okaxis/NOV", "631377002145", "1500.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T26_2007);
        }

        @Test
        void referencePlusUniqueAmountOnTheSameFlatIsAutoPosted() {
            MatchResult result = match(credit(2, "upi/cr/631804512267/a wagh/a.wagh@okhdfcbank/maint t26 1203 october",
                    "631804512267", "6190.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(T26_1203);
        }

        @Test
        void upiIdPlusUniqueAmountOnTheSameFlatIsAutoPosted() {
            MatchResult result = match(credit(2, "UPI/CR/632000000003/NENE/nene.home@okicici/NOV", "632000000003", "7670.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(T15_1605);
            assertThat(result.reasons()).contains("Amount ₹7,670.00 equals the remaining amount of T15-1605's November 2026 bill");
        }

        @Test
        void amountOnSeveralFlatsIncludingTheReferencedOneIsNotAgreement() {
            MatchResult result = match(credit(2, "IMPS/P2A/632245778064/T21 1402 NOV", "632245778064", "6207.50"));

            assertThat(result.decision()).isEqualTo(MatchDecision.SUGGESTED);
            assertThat(result.flatId()).contains(T21_1402);
            assertThat(result.reasons()).contains(
                    "Amount ₹6,207.50 matches more than one flat (T21-0104, T21-1402), so it does not confirm T21-1402");
        }

        @Test
        void amountEqualToATotalOutstandingCountsAsAnAmountSignal() {
            MatchResult result = match(credit(2, "IMPS/P2A/632000000004/T21-1601 ALL DUES", "632000000004", "16368.85"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.reasons()).contains("Amount ₹16,368.85 equals T21-1601's total outstanding");
        }
    }

    @Nested
    class OtherCases {

        @Test
        void partPaymentWithReferenceAndKnownUpiIdIsAutoPosted() {
            MatchResult result = match(credit(2, "UPI/CR/632000000005/AAMIR/aamir.s@okicici/T21-1601 part", "632000000005", "2000.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(AAMIR);
        }

        @Test
        void overpaymentWithTwoAgreeingSignalsIsAutoPosted() {
            MatchResult result = match(credit(2, "UPI/CR/632023556842/NENE/nene.home@okicici/T15-1605 NOV DEC",
                    "632023556842", "10000.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.flatId()).contains(T15_1605);
        }

        @Test
        void utrRepeatedInsideOneFileIsADuplicateOfTheEarlierRow() {
            StatementRow first = credit(21, "NEFT CR-SBIN0004321-SHREE SAI CATERERS-DEPOSIT REFUND", "N307260033344455", "123450.00");
            StatementRow repeat = credit(22, "NEFT CR-SBIN0004321-SHREE SAI CATERERS-DEPOSIT REFUND", "N307260033344455", "123450.00");

            List<MatchResult> results = PaymentMatcher.matchAll(List.of(repeat, first), context);

            assertThat(results).extracting(MatchResult::rowNo, MatchResult::decision).containsExactly(
                    tuple(21, MatchDecision.UNMATCHED),
                    tuple(22, MatchDecision.DUPLICATE));
            assertThat(results.get(1).duplicateOf()).contains(DuplicateOf.thisImport(21));
        }

        @Test
        void repeatedRowWithAUtrIsADuplicateNotAPossibleDuplicate() {
            StatementRow first = credit(3, "UPI/CR/630112457788/AAMIR/aamir.s@okicici/T21-1601", "630112457788", "5660.00");

            List<MatchResult> results = PaymentMatcher.matchAll(List.of(first, credit(4,
                    "UPI/CR/630112457788/AAMIR/aamir.s@okicici/T21-1601", "630112457788", "5660.00")), context);

            assertThat(results.get(1).decision()).isEqualTo(MatchDecision.DUPLICATE);
        }

        @Test
        void sameRowAsAnEarlierImportWithoutUtrIsAPossibleDuplicate() {
            StatementRow cash = credit(5, "CASH DEP T15-0801 MAINT", "", "3500.00");
            MatchContext withHistory = seedLikeContext().earlierBankRow(77L, cash.rowHash(), null, null).build();

            MatchResult result = PaymentMatcher.match(cash, withHistory);

            assertThat(result.decision()).isEqualTo(MatchDecision.POSSIBLE_DUPLICATE);
            assertThat(result.duplicateOf()).contains(DuplicateOf.earlierImport(77L));
        }

        @Test
        void gatewayReferenceAlreadyReconciledByAnEarlierImportIsADuplicate() {
            MatchContext withHistory = seedLikeContext()
                    .earlierBankRow(78L, "other-hash", null, "PGTEST-JD9EP9VAU8CP").build();

            MatchResult result = PaymentMatcher.match(
                    credit(2, "PG SETTLEMENT PGTEST-JD9EP9VAU8CP", "PGTEST-JD9EP9VAU8CP", "3000.00"), withHistory);

            assertThat(result.decision()).isEqualTo(MatchDecision.DUPLICATE);
            assertThat(result.reasons()).containsExactly("Gateway reference PGTEST-JD9EP9VAU8CP is already in an earlier import");
        }

        @Test
        void unknownGatewayReferenceFallsThroughToTheOtherSignals() {
            MatchResult result = match(credit(2, "PG SETTLEMENT PGTEST-UNKNOWN1 T21-1601", "PGTEST-UNKNOWN1", "5660.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.AUTO_POSTED);
            assertThat(result.reasons()).startsWith("Gateway reference PGTEST-UNKNOWN1 does not match any payment");
        }

        @Test
        void lowercaseReferenceWithSpacesStillMatches() {
            MatchResult result = match(credit(2, "maint t26 1203 october", "", "6190.00"));

            assertThat(result.flatId()).contains(T26_1203);
        }

        @Test
        void referenceToAFlatThatDoesNotExistIsExplainedAndIgnored() {
            MatchResult result = match(credit(2, "IMPS T99-0101 MAINT", "632000000006", "999.00"));

            assertThat(result.decision()).isEqualTo(MatchDecision.UNMATCHED);
            assertThat(result.reasons()).containsExactly(
                    "Reference T99-0101 is not a flat in this township",
                    "No flat reference, known UPI ID or matching amount");
        }
    }

    private MatchResult match(StatementRow row) {
        return PaymentMatcher.match(row, context);
    }
}
