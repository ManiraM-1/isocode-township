package com.isocode.township.committee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

public final class Medians {

    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    private Medians() {
    }

    public static Long minutes(List<Duration> durations) {
        if (durations.isEmpty()) {
            return null;
        }
        List<Long> seconds = durations.stream().map(Duration::toSeconds).sorted().toList();
        int middle = seconds.size() / 2;
        BigDecimal medianSeconds = seconds.size() % 2 == 1
                ? BigDecimal.valueOf(seconds.get(middle))
                : BigDecimal.valueOf(seconds.get(middle - 1) + seconds.get(middle)).divide(BigDecimal.TWO);
        return medianSeconds.divide(SECONDS_PER_MINUTE, 0, RoundingMode.HALF_UP).longValueExact();
    }
}
