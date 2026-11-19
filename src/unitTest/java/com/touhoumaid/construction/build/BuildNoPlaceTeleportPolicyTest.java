package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildNoPlaceTeleportPolicyTest {
    @Test
    void triggersAfterTwentySecondsWithoutPlacement() {
        assertTrue(BuildNoPlaceTeleportPolicy.shouldTeleport(
                BuildNoPlaceTeleportPolicy.NO_PLACE_TELEPORT_TICKS,
                ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS));
    }

    @Test
    void respectsTeleportCooldown() {
        assertFalse(BuildNoPlaceTeleportPolicy.shouldTeleport(
                BuildNoPlaceTeleportPolicy.NO_PLACE_TELEPORT_TICKS,
                ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS - 1));
    }

    @Test
    void doesNotTriggerEarly() {
        assertFalse(BuildNoPlaceTeleportPolicy.shouldTeleport(
                BuildNoPlaceTeleportPolicy.NO_PLACE_TELEPORT_TICKS - 1,
                ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS));
    }
}
