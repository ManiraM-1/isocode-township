package com.isocode.township.complaint;

import com.isocode.township.common.ConflictException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ComplaintStateMachine {

    public enum Actor {
        STAFF,
        RESIDENT
    }

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> STAFF_MOVES = Map.of(
            ComplaintStatus.OPEN, EnumSet.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS),
            ComplaintStatus.ASSIGNED, EnumSet.of(ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED),
            ComplaintStatus.IN_PROGRESS, EnumSet.of(ComplaintStatus.RESOLVED),
            ComplaintStatus.RESOLVED, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.CLOSED, EnumSet.noneOf(ComplaintStatus.class));

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> RESIDENT_MOVES = Map.of(
            ComplaintStatus.OPEN, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.ASSIGNED, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.IN_PROGRESS, EnumSet.noneOf(ComplaintStatus.class),
            ComplaintStatus.RESOLVED, EnumSet.of(ComplaintStatus.CLOSED, ComplaintStatus.ASSIGNED),
            ComplaintStatus.CLOSED, EnumSet.noneOf(ComplaintStatus.class));

    private ComplaintStateMachine() {
    }

    public static Set<ComplaintStatus> nextFor(ComplaintStatus from, Actor actor) {
        Set<ComplaintStatus> next = (actor == Actor.STAFF ? STAFF_MOVES : RESIDENT_MOVES).get(from);
        return next.isEmpty() ? EnumSet.noneOf(ComplaintStatus.class) : EnumSet.copyOf(next);
    }

    public static boolean canMove(ComplaintStatus from, ComplaintStatus to, Actor actor) {
        return nextFor(from, actor).contains(to);
    }

    public static void requireMove(ComplaintStatus from, ComplaintStatus to, Actor actor) {
        if (!canMove(from, to, actor)) {
            throw new IllegalTransitionException(from, to, actor);
        }
    }

    public static final class IllegalTransitionException extends ConflictException {

        public IllegalTransitionException(ComplaintStatus from, ComplaintStatus to, Actor actor) {
            super(describe(from, to, actor));
        }

        private static String describe(ComplaintStatus from, ComplaintStatus to, Actor actor) {
            String who = actor == Actor.STAFF ? "The help desk" : "A resident";
            return who + " cannot move a complaint from " + label(from) + " to " + label(to) + ".";
        }

        private static String label(ComplaintStatus status) {
            return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }
}
