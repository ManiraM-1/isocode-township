package com.isocode.township.complaint;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public static final Set<ComplaintStatus> ACTIVE = EnumSet.of(OPEN, ASSIGNED, IN_PROGRESS);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
