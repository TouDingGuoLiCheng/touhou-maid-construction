package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildNoWorkStandPlacePolicyTest {
    @Test
    void noWorkStandAllowsImmediateVaultPlace() {
        assertTrue(BuildNoWorkStandPlacePolicy.shouldImmediateVaultPlace(false));
    }

    @Test
    void existingWorkStandDoesNotForceImmediateVaultPlace() {
        assertFalse(BuildNoWorkStandPlacePolicy.shouldImmediateVaultPlace(true));
    }
}
