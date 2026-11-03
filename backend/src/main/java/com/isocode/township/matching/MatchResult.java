package com.isocode.township.matching;

import java.util.List;
import java.util.Optional;

public record MatchResult(
        int rowNo,
        MatchDecision decision,
        Optional<Long> flatId,
        List<Candidate> candidates,
        Optional<Confidence> confidence,
        List<String> reasons,
        Optional<DuplicateOf> duplicateOf,
        Optional<Long> gatewayPaymentId) {

    public record Candidate(Long flatId, String flatCode) {
    }

    public record DuplicateOf(Long earlierBankRowId, Integer rowNoInThisImport) {

        public static DuplicateOf earlierImport(Long bankRowId) {
            return new DuplicateOf(bankRowId, null);
        }

        public static DuplicateOf thisImport(int rowNo) {
            return new DuplicateOf(null, rowNo);
        }

        public String describe() {
            return rowNoInThisImport != null
                    ? "row " + rowNoInThisImport + " of this file"
                    : "a row of an earlier import";
        }
    }
}
