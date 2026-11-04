package com.isocode.township.complaint;

import com.isocode.township.common.SocietySettings;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

public final class SlaTargets {

    private final Map<Urgency, Duration> firstResponse = new EnumMap<>(Urgency.class);
    private final Map<Urgency, Duration> resolution = new EnumMap<>(Urgency.class);

    private SlaTargets() {
    }

    public static SlaTargets from(SocietySettings settings) {
        SlaTargets targets = new SlaTargets();
        targets.put(Urgency.CRITICAL, settings.getFirstResponseMinutesCritical(), settings.getResolveMinutesCritical());
        targets.put(Urgency.HIGH, settings.getFirstResponseMinutesHigh(), settings.getResolveMinutesHigh());
        targets.put(Urgency.MEDIUM, settings.getFirstResponseMinutesMedium(), settings.getResolveMinutesMedium());
        targets.put(Urgency.LOW, settings.getFirstResponseMinutesLow(), settings.getResolveMinutesLow());
        return targets;
    }

    public static SlaTargets of(Map<Urgency, int[]> minutesByUrgency) {
        SlaTargets targets = new SlaTargets();
        minutesByUrgency.forEach((urgency, minutes) -> targets.put(urgency, minutes[0], minutes[1]));
        return targets;
    }

    public Instant firstResponseDue(Instant raisedAt, Urgency urgency) {
        return raisedAt.plus(firstResponse.get(urgency));
    }

    public Instant resolveDue(Instant from, Urgency urgency) {
        return from.plus(resolution.get(urgency));
    }

    public Instant rescheduleResolve(Instant currentDue, Urgency previous, Urgency next) {
        return currentDue.minus(resolution.get(previous)).plus(resolution.get(next));
    }

    private void put(Urgency urgency, int firstResponseMinutes, int resolveMinutes) {
        firstResponse.put(urgency, Duration.ofMinutes(firstResponseMinutes));
        resolution.put(urgency, Duration.ofMinutes(resolveMinutes));
    }
}
