package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildWorkGoalStallPolicyTest {
    private static BuildWorkGoalStallPolicy.State advance(
            BuildWorkGoalStallPolicy.State state, int distance, int ticks) {
        BuildWorkGoalStallPolicy.State current = state;
        for (int i = 0; i < ticks; i++) {
            current = BuildWorkGoalStallPolicy.onTick(current, distance);
        }
        return current;
    }

    @Test
    void stallsWhenDistanceDoesNotShrink() {
        BuildWorkGoalStallPolicy.State state = advance(
                BuildWorkGoalStallPolicy.State.EMPTY, 12, BuildWorkGoalStallPolicy.STALL_TICKS);
        assertTrue(BuildWorkGoalStallPolicy.isStalled(state));
    }

    @Test
    void resetsWhenDistanceShrinks() {
        BuildWorkGoalStallPolicy.State state = advance(
                BuildWorkGoalStallPolicy.State.EMPTY, 12, BuildWorkGoalStallPolicy.STALL_TICKS - 1);
        state = BuildWorkGoalStallPolicy.onTick(state, 8);
        assertFalse(BuildWorkGoalStallPolicy.isStalled(state));
    }

    @Test
    void wanderingWithoutClosingDistanceStillStalls() {
        BuildWorkGoalStallPolicy.State state = BuildWorkGoalStallPolicy.State.EMPTY;
        for (int i = 0; i < BuildWorkGoalStallPolicy.STALL_TICKS; i++) {
            state = BuildWorkGoalStallPolicy.onTick(state, 10 + (i % 2));
        }
        assertTrue(BuildWorkGoalStallPolicy.isStalled(state));
    }
}
