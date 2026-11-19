package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPathNavFailPolicyTest {
    @Test
    void oneNavFailureWithStallTriggersVaultPlaceOnlyWithoutWorkStand() {
        assertFalse(BuildPathNavFailPolicy.shouldVaultDirectPlace(0, 60, 0, false));
        assertFalse(BuildPathNavFailPolicy.shouldVaultDirectPlace(1, 10, 10, false));
        assertTrue(BuildPathNavFailPolicy.shouldVaultDirectPlace(1, 60, 0, false));
        assertTrue(BuildPathNavFailPolicy.shouldVaultDirectPlace(1, 0, 60, false));
        assertTrue(BuildPathNavFailPolicy.shouldVaultDirectPlace(2, 60, 0, false));
    }

    @Test
    void workStandBlocksVaultSoTeleportRunsFirst() {
        assertFalse(BuildPathNavFailPolicy.shouldVaultDirectPlace(1, 60, 60, true));
        assertFalse(BuildPathNavFailPolicy.shouldVaultDirectPlace(5, 200, 200, true));
    }
}
