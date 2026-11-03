package com.isocode.township.matching;

public enum MatchDecision {
    DUPLICATE,
    NON_CREDIT,
    POSSIBLE_DUPLICATE,
    RECONCILED_GATEWAY,
    CONFLICT,
    AUTO_POSTED,
    SUGGESTED,
    UNMATCHED;

    public boolean isPostedAutomatically() {
        return this == AUTO_POSTED;
    }

    public boolean goesToAccountsQueue() {
        return this == SUGGESTED || this == CONFLICT || this == UNMATCHED || this == POSSIBLE_DUPLICATE;
    }
}
