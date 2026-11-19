package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionSiteArrivePolicyTest {
    @Test
    void suppressesStuckTeleportForIdlePickStuck() {
        assertTrue(ConstructionSiteArrivePolicy.shouldSuppressStuckTeleport(true, true));
        assertFalse(ConstructionSiteArrivePolicy.shouldSuppressStuckTeleport(false, true));
        assertFalse(ConstructionSiteArrivePolicy.shouldSuppressStuckTeleport(true, false));
    }

    @Test
    void stuckRescueRequiresAWorkStandEvenWithAHeldClaim() {
        // Log ping-pong: IDLE + claim + work=- → stuck hop 91↔90 every 5s while siteDist=0.
        assertFalse(ConstructionSiteArrivePolicy.shouldAllowStuckRescue(false, false, true));
        assertTrue(ConstructionSiteArrivePolicy.shouldAllowStuckRescue(false, true, true));
        assertFalse(ConstructionSiteArrivePolicy.shouldAllowStuckRescue(true, true, true));
        assertFalse(ConstructionSiteArrivePolicy.shouldAllowStuckRescue(false, true, false));
    }

    @Test
    void rejectsAdjacentStuckHopWhenAlreadyInsideSiteWithoutPreferStand() {
        assertTrue(ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(0.0, false, 1));
        assertTrue(ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(0.0, false, 2));
        assertFalse(ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(0.0, true, 1));
        assertFalse(ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(8.0, false, 1));
        assertFalse(ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(0.0, false, 0));
    }

    @Test
    void rescuesOutOfRangeMaidsWellBeforeTheGenericStuckTimer() {
        int cooldown = ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS;
        assertTrue(ConstructionSiteArrivePolicy.shouldRescueOutOfRange(true, 100, cooldown));
        assertFalse(ConstructionSiteArrivePolicy.shouldRescueOutOfRange(true, 99, cooldown));
        assertFalse(ConstructionSiteArrivePolicy.shouldRescueOutOfRange(false, 400, cooldown));
        assertFalse(ConstructionSiteArrivePolicy.shouldRescueOutOfRange(true, 400, cooldown - 1));
    }

    @Test
    void blocksSameColumnShaftLift() {
        assertTrue(ConstructionSiteArrivePolicy.isUnsafeShaftTeleport(162, -51, 124, 162, -9, 124));
        assertFalse(ConstructionSiteArrivePolicy.isUnsafeShaftTeleport(162, -51, 124, 162, -48, 124));
        assertFalse(ConstructionSiteArrivePolicy.isUnsafeShaftTeleport(162, -51, 124, 170, -9, 124));
    }

    @Test
    void distanceRescueHintYIgnoresSiteCenterAndHighWorkStand() {
        // 花嫁酒狐: ground -60, work stand -43, site-center -9 → must stay at feet.
        assertEquals(-60, ConstructionSiteArrivePolicy.distanceRescueHintY(-60, -43, -9));
        assertEquals(12, ConstructionSiteArrivePolicy.distanceRescueHintY(12, 40, 26));
    }

    @Test
    void heightmapFallbackMustNotLiftOntoBuildingMidHeight() {
        int maxLift = ConstructionSiteArrivePolicy.TELEPORT_MAX_VERTICAL_DELTA;
        assertEquals(-60, ConstructionSiteArrivePolicy.fallbackStandY(-60, -9, -60, maxLift));
        assertEquals(-58, ConstructionSiteArrivePolicy.fallbackStandY(-60, -58, -60, maxLift));
        assertEquals(-60, ConstructionSiteArrivePolicy.fallbackStandY(-60, -62, -60, maxLift));
    }

    @Test
    void verticalDeltaHelperStillDescribesALargeGapButDoesNotGateStandTeleports() {
        int maxDelta = ConstructionSiteArrivePolicy.TELEPORT_MAX_VERTICAL_DELTA;
        assertTrue(ConstructionSiteArrivePolicy.shouldRejectVerticalTeleport(-60, -9, maxDelta));
        assertFalse(ConstructionSiteArrivePolicy.shouldRejectVerticalTeleport(-60, -55, maxDelta));
        assertTrue(ConstructionSiteArrivePolicy.needsVerticalSafety("rescue"));
        assertTrue(ConstructionSiteArrivePolicy.needsVerticalSafety("stuck"));
        assertTrue(ConstructionSiteArrivePolicy.needsVerticalSafety("out-of-range"));
    }
}
