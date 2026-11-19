package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldVanillaNavPolicyTest {
    @Test
    void teleportsToWorkAfterThreeSecondPathStall() {
        assertFalse(ScaffoldVanillaNavPolicy.shouldTeleportToWork(
                ScaffoldVanillaNavPolicy.PATH_STUCK_TELEPORT_TICKS - 1));
        assertTrue(ScaffoldVanillaNavPolicy.shouldTeleportToWork(
                ScaffoldVanillaNavPolicy.PATH_STUCK_TELEPORT_TICKS));
    }

    @Test
    void teleportsWhenEitherNeighborhoodOrWorkGoalStallHitsThreshold() {
        int need = ScaffoldVanillaNavPolicy.PATH_STUCK_TELEPORT_TICKS;
        assertFalse(ScaffoldVanillaNavPolicy.shouldTeleportToWork(need - 1, need - 1));
        assertTrue(ScaffoldVanillaNavPolicy.shouldTeleportToWork(need, 0));
        assertTrue(ScaffoldVanillaNavPolicy.shouldTeleportToWork(0, need));
        assertTrue(ScaffoldVanillaNavPolicy.shouldTeleportToWork(need, need));
    }

    @Test
    void stallThresholdAllowsSnapEvenWhenNavAlreadyGaveUp() {
        assertFalse(ScaffoldVanillaNavPolicy.maySnapToWorkDespiteNav(false, false));
        assertTrue(ScaffoldVanillaNavPolicy.maySnapToWorkDespiteNav(true, false));
        assertTrue(ScaffoldVanillaNavPolicy.maySnapToWorkDespiteNav(false, true));
    }

    @Test
    void solidWalkableStandIsEligibleTeleportDestination() {
        assertFalse(ScaffoldVanillaNavPolicy.isTeleportStandEligible(false, true));
        assertFalse(ScaffoldVanillaNavPolicy.isTeleportStandEligible(true, false));
        assertTrue(ScaffoldVanillaNavPolicy.isTeleportStandEligible(true, true));
    }

    @Test
    void withWorkStandIdleHoldMayStillSnapToStand() {
        assertTrue(ScaffoldVanillaNavPolicy.maySnapWhileHoldingWorkStand(true, true));
        assertFalse(ScaffoldVanillaNavPolicy.maySnapWhileHoldingWorkStand(false, true));
        assertFalse(ScaffoldVanillaNavPolicy.maySnapWhileHoldingWorkStand(true, false));
    }
}
