package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProgressStallPolicyTest {
    private static BuildProgressStallPolicy.State advance(
            BuildProgressStallPolicy.State state, int x, int y, int z, int ticks) {
        BuildProgressStallPolicy.State current = state;
        for (int i = 0; i < ticks; i++) {
            current = BuildProgressStallPolicy.onTick(current, x, y, z);
        }
        return current;
    }

    @Test
    void standingStillEventuallyStalls() {
        BuildProgressStallPolicy.State state = advance(
                BuildProgressStallPolicy.State.EMPTY, 131, -58, 205, BuildProgressStallPolicy.STALL_TICKS);
        assertTrue(BuildProgressStallPolicy.isStalled(state));
    }

    @Test
    void oscillatingBetweenTwoCellsStillStalls() {
        // Teleport ping-pong (snap_climb / snap_down) changes the feet cell every tick but makes
        // no progress, so the recovery loop must not keep resetting the timer.
        BuildProgressStallPolicy.State state = BuildProgressStallPolicy.State.EMPTY;
        for (int i = 0; i < BuildProgressStallPolicy.STALL_TICKS; i++) {
            state = BuildProgressStallPolicy.onTick(state, 129, i % 2 == 0 ? -54 : -55, 196);
        }
        assertTrue(BuildProgressStallPolicy.isStalled(state));
    }

    @Test
    void realDisplacementResetsTheTimer() {
        BuildProgressStallPolicy.State state = advance(
                BuildProgressStallPolicy.State.EMPTY, 131, -58, 205, BuildProgressStallPolicy.STALL_TICKS - 1);
        state = BuildProgressStallPolicy.onTick(state, 131, -58, 201);
        assertFalse(BuildProgressStallPolicy.isStalled(state));
    }

    @Test
    void staysWithinToleranceWithoutResetting() {
        BuildProgressStallPolicy.State state = BuildProgressStallPolicy.State.EMPTY;
        state = BuildProgressStallPolicy.onTick(state, 100, 10, 100);
        state = advance(state, 101, 11, 100, BuildProgressStallPolicy.STALL_TICKS);
        assertTrue(BuildProgressStallPolicy.isStalled(state));
    }

    @Test
    void clearedStateIsNotStalled() {
        BuildProgressStallPolicy.State state = advance(
                BuildProgressStallPolicy.State.EMPTY, 131, -58, 205, BuildProgressStallPolicy.STALL_TICKS);
        assertFalse(BuildProgressStallPolicy.isStalled(BuildProgressStallPolicy.State.EMPTY));
        assertTrue(BuildProgressStallPolicy.isStalled(state));
    }
}
