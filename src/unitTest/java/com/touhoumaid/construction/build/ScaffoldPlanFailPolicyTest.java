package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaffoldPlanFailPolicyTest {
    @Test
    void firstPlanFailureRetriesTheSameStand() {
        assertEquals(
                ScaffoldPlanFailPolicy.Next.RETRY_MOVE,
                ScaffoldPlanFailPolicy.afterPlanFailed(1));
    }

    @Test
    void secondPlanFailureDirectPlaces() {
        assertEquals(
                ScaffoldPlanFailPolicy.Next.DIRECT_PLACE,
                ScaffoldPlanFailPolicy.afterPlanFailed(2));
    }

    @Test
    void climbingScaffoldNeverDirectPlacesOnPlanFailure() {
        assertEquals(
                ScaffoldPlanFailPolicy.Next.RETRY_MOVE,
                ScaffoldPlanFailPolicy.afterPlanFailed(5, true));
    }

    @Test
    void notNeededWhileStuckEscalatesOnSecondHit() {
        assertEquals(
                ScaffoldPlanFailPolicy.Next.RETRY_MOVE,
                ScaffoldPlanFailPolicy.afterNotNeededWhileStuck(1));
        assertEquals(
                ScaffoldPlanFailPolicy.Next.DIRECT_PLACE,
                ScaffoldPlanFailPolicy.afterNotNeededWhileStuck(2));
    }

    @Test
    void climbingScaffoldNeverDirectPlacesOnNotNeededWhileStuck() {
        assertEquals(
                ScaffoldPlanFailPolicy.Next.RETRY_MOVE,
                ScaffoldPlanFailPolicy.afterNotNeededWhileStuck(5, true));
    }
}
