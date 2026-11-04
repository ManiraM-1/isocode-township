package com.isocode.township.committee;

import java.time.Instant;

public record ComplaintFacts(Instant createdAt, Instant firstResponseAt, Instant firstResponseDueAt,
                             Instant resolvedAt, Instant closedAt, int reopenCount, Integer rating) {
}
