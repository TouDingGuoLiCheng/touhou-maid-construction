package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deferred / remaining-need policy used when refreshing the work queue. */
class BuildDeferredRecoveryPolicyTest {
    @Test
    void stillNeedsVaultMaterial_requiresPendingCells() {
        assertTrue(BuildMaterialNeedPolicy.stillNeedsVaultMaterial(10, 8, 2));
        assertFalse(BuildMaterialNeedPolicy.stillNeedsVaultMaterial(10, 8, 0));
    }

    @Test
    void shouldSkipPendingTask_whenFullyCounted() {
        assertTrue(BuildMaterialNeedPolicy.shouldSkipPendingTask(10, 10));
        assertFalse(BuildMaterialNeedPolicy.shouldSkipPendingTask(10, 9));
    }
}
