package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldFallSafetyTest {
    @Test
    void holdingOrReleasingClearsAccumulatedFallDistance() {
        assertEquals(0.0F, ScaffoldFallSafety.clearedFallDistance());
        assertTrue(ScaffoldFallSafety.shouldClearFallDistance(true));
        assertTrue(ScaffoldFallSafety.shouldClearFallDistance(false));
    }

    @Test
    void immunizeFallDamageOnlyOnActiveConstructionJob() {
        assertFalse(ScaffoldFallSafety.shouldImmunizeFallDamage(false, false));
        assertFalse(ScaffoldFallSafety.shouldImmunizeFallDamage(true, false));
        assertFalse(ScaffoldFallSafety.shouldImmunizeFallDamage(false, true));
        assertTrue(ScaffoldFallSafety.shouldImmunizeFallDamage(true, true));
    }
}
