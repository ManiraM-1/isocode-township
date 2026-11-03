package com.isocode.township.complaint;

import java.util.EnumSet;
import java.util.Set;

public enum DeskLane {
    SAFETY(EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED)),
    NEEDS_REVIEW(EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED)),
    OPEN(ComplaintStatus.ACTIVE),
    AWAITING_RESIDENT(EnumSet.of(ComplaintStatus.RESOLVED));

    private final Set<ComplaintStatus> statuses;

    DeskLane(Set<ComplaintStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<ComplaintStatus> statuses() {
        return EnumSet.copyOf(statuses);
    }
}
