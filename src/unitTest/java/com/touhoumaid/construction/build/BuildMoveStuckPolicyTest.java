package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMoveStuckPolicyTest {
    @Test
    void marksStuckAfterSixtyTicksInNeighbourhood() {
        BuildMoveStuckPolicy.State state = BuildMoveStuckPolicy.State.EMPTY;
        for (int i = 0; i < BuildMoveStuckPolicy.STUCK_TICKS; i++) {
            state = BuildMoveStuckPolicy.onTick(state, 1, 2, 3);
        }
        assertTrue(BuildMoveStuckPolicy.isStuck(state));
        assertEquals(BuildMoveStuckPolicy.STUCK_TICKS, state.stuckTicks());
    }

    @Test
    void smallJitterDoesNotResetCounter() {
        BuildMoveStuckPolicy.State state = BuildMoveStuckPolicy.State.EMPTY;
        for (int i = 0; i < BuildMoveStuckPolicy.STUCK_TICKS - 2; i++) {
            state = BuildMoveStuckPolicy.onTick(state, 1, 2, 3);
        }
        state = BuildMoveStuckPolicy.onTick(state, 2, 2, 3);
        assertEquals(BuildMoveStuckPolicy.STUCK_TICKS - 1, state.stuckTicks());
        assertFalse(BuildMoveStuckPolicy.isStuck(state));
    }

    @Test
    void leavingNeighborhoodResetsCounter() {
        BuildMoveStuckPolicy.State state = BuildMoveStuckPolicy.State.EMPTY;
        for (int i = 0; i < BuildMoveStuckPolicy.STUCK_TICKS - 1; i++) {
            state = BuildMoveStuckPolicy.onTick(state, 1, 2, 3);
        }
        state = BuildMoveStuckPolicy.onTick(state, 10, 2, 3);
        assertFalse(BuildMoveStuckPolicy.isStuck(state));
    }
}
