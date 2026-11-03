package com.isocode.township.complaint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isocode.township.common.ConflictException;
import com.isocode.township.complaint.ComplaintStateMachine.Actor;
import com.isocode.township.complaint.ComplaintStateMachine.IllegalTransitionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ComplaintStateMachineTest {

    private static final Set<String> LEGAL = Set.of(
            "STAFF:OPEN->ASSIGNED",
            "STAFF:OPEN->IN_PROGRESS",
            "STAFF:ASSIGNED->IN_PROGRESS",
            "STAFF:ASSIGNED->RESOLVED",
            "STAFF:IN_PROGRESS->RESOLVED",
            "RESIDENT:RESOLVED->CLOSED",
            "RESIDENT:RESOLVED->ASSIGNED");

    static Stream<Arguments> everyMove() {
        List<Arguments> moves = new ArrayList<>();
        for (Actor actor : Actor.values()) {
            for (ComplaintStatus from : ComplaintStatus.values()) {
                for (ComplaintStatus to : ComplaintStatus.values()) {
                    moves.add(Arguments.of(actor, from, to));
                }
            }
        }
        return moves.stream();
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("everyMove")
    void onlyTheMovesInTheStatusFlowAreAllowed(Actor actor, ComplaintStatus from, ComplaintStatus to) {
        boolean legal = LEGAL.contains(actor + ":" + from + "->" + to);

        assertThat(ComplaintStateMachine.canMove(from, to, actor)).isEqualTo(legal);
        if (legal) {
            ComplaintStateMachine.requireMove(from, to, actor);
        } else {
            assertThatThrownBy(() -> ComplaintStateMachine.requireMove(from, to, actor))
                    .isInstanceOf(IllegalTransitionException.class)
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void closedIsFinalForEveryone() {
        assertThat(ComplaintStateMachine.nextFor(ComplaintStatus.CLOSED, Actor.STAFF)).isEmpty();
        assertThat(ComplaintStateMachine.nextFor(ComplaintStatus.CLOSED, Actor.RESIDENT)).isEmpty();
    }

    @Test
    void onlyTheResidentActsOnAResolvedComplaint() {
        assertThat(ComplaintStateMachine.nextFor(ComplaintStatus.RESOLVED, Actor.STAFF)).isEmpty();
        assertThat(ComplaintStateMachine.nextFor(ComplaintStatus.RESOLVED, Actor.RESIDENT))
                .containsExactlyInAnyOrder(ComplaintStatus.CLOSED, ComplaintStatus.ASSIGNED);
    }

    @Test
    void theMessageNamesWhoTriedWhat() {
        assertThatThrownBy(() -> ComplaintStateMachine.requireMove(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, Actor.STAFF))
                .hasMessage("The help desk cannot move a complaint from resolved to closed.");
    }
}
