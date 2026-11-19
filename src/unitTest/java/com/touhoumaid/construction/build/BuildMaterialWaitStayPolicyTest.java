package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildMaterialWaitStayPolicyTest {
    @Test
    void freezesInPlaceOnlyWhileWaitingForMaterials() {
        assertTrue(BuildMaterialWaitStayPolicy.shouldFreezeInPlace(true));
        assertFalse(BuildMaterialWaitStayPolicy.shouldFreezeInPlace(false));
    }

    @Test
    void skipsSiteRescueWhileWaitingForMaterials() {
        assertFalse(BuildMaterialWaitStayPolicy.shouldRunSiteRescue(true));
        assertTrue(BuildMaterialWaitStayPolicy.shouldRunSiteRescue(false));
    }

    @Test
    void skipsBodyClearWhileWaitingForMaterials() {
        assertFalse(BuildMaterialWaitStayPolicy.shouldClearBody(true));
        assertTrue(BuildMaterialWaitStayPolicy.shouldClearBody(false));
    }

    @Test
    void doesNotTightenRestrictDuringWaitBecauseThatCausesInPlaceStomping() {
        // radius-1 restrict made TLM path toward the pin cell every tick (foot-stomp).
        assertFalse(BuildMaterialWaitStayPolicy.shouldTightenRestrict(true));
        assertFalse(BuildMaterialWaitStayPolicy.shouldTightenRestrict(false));
    }

    @Test
    void blocksOwnerTeleportWhileConstructionPriorityIsActive() {
        assertTrue(BuildMaterialWaitStayPolicy.shouldBlockOwnerTeleport(true));
        assertFalse(BuildMaterialWaitStayPolicy.shouldBlockOwnerTeleport(false));
    }

    @Test
    void resetsNavigationWhileWaitingSoMoveControlStopsKicking() {
        assertTrue(BuildMaterialWaitStayPolicy.shouldResetNavigation(true));
        assertFalse(BuildMaterialWaitStayPolicy.shouldResetNavigation(false));
    }

    @Test
    void onlyStopsVerticalMotionOnceStanding() {
        assertTrue(BuildMaterialWaitStayPolicy.shouldZeroVerticalMotion(true));
        assertFalse(BuildMaterialWaitStayPolicy.shouldZeroVerticalMotion(false));
    }
}
