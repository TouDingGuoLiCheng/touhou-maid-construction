package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartLayerPolicyTest {
    @Test
    void clampKeepsZeroWhenDefault() {
        assertEquals(0, StartLayerPolicy.clamp(0, 10));
    }

    @Test
    void clampCapsAtHeightMinusOne() {
        assertEquals(9, StartLayerPolicy.clamp(99, 10));
        assertEquals(0, StartLayerPolicy.clamp(-3, 10));
        assertEquals(0, StartLayerPolicy.clamp(5, 1));
    }

    @Test
    void includesLocalYFromStartLayer() {
        assertFalse(StartLayerPolicy.includesLocalY(0, 3));
        assertFalse(StartLayerPolicy.includesLocalY(2, 3));
        assertTrue(StartLayerPolicy.includesLocalY(3, 3));
        assertTrue(StartLayerPolicy.includesLocalY(5, 3));
    }

    @Test
    void canStartOnlyWhenIncludedNonAirExists() {
        assertTrue(StartLayerPolicy.canStartBuild(2, 10, true));
        assertFalse(StartLayerPolicy.canStartBuild(2, 10, false));
    }
}
