package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildWorkPositionFindPolicyTest {
    @Test
    void capsPathReachChecksFarBelowUnboundedCandidateFanOut() {
        assertTrue(BuildWorkPositionFindPolicy.MAX_PATH_REACH_CHECKS <= 8);
        assertTrue(BuildWorkPositionFindPolicy.MAX_PATH_REACH_CHECKS >= 1);
    }

    @Test
    void mayCheckNextStopsAtCap() {
        int cap = BuildWorkPositionFindPolicy.MAX_PATH_REACH_CHECKS;
        assertTrue(BuildWorkPositionFindPolicy.mayCheckPathReach(0));
        assertTrue(BuildWorkPositionFindPolicy.mayCheckPathReach(cap - 1));
        assertFalse(BuildWorkPositionFindPolicy.mayCheckPathReach(cap));
        assertFalse(BuildWorkPositionFindPolicy.mayCheckPathReach(cap + 10));
    }

    @Test
    void pathReachBudgetNeverExceedsCandidateCountOrCap() {
        int cap = BuildWorkPositionFindPolicy.MAX_PATH_REACH_CHECKS;
        assertEquals(0, BuildWorkPositionFindPolicy.pathReachBudget(0));
        assertEquals(1, BuildWorkPositionFindPolicy.pathReachBudget(1));
        assertEquals(cap, BuildWorkPositionFindPolicy.pathReachBudget(cap));
        assertEquals(cap, BuildWorkPositionFindPolicy.pathReachBudget(cap + 50));
    }
}
