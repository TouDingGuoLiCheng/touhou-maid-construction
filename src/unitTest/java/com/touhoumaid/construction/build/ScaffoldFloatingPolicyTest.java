package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldFloatingPolicyTest {
    @Test
    void solidFloorIsNeverFloatingEvenBesideALowerColumn() {
        // Maid walked off the tower onto the wall she just built: a nearby column top two blocks
        // below must not drag her back.
        assertFalse(ScaffoldFloatingPolicy.isFloating(false, true, true, -54, -56));
    }

    @Test
    void scaffoldSupportIsNeverFloating() {
        assertFalse(ScaffoldFloatingPolicy.isFloating(true, false, true, -54, -60));
    }

    @Test
    void airAboveColumnTopIsFloating() {
        assertTrue(ScaffoldFloatingPolicy.isFloating(false, false, true, -54, -56));
    }

    @Test
    void standingExactlyOnColumnTopIsNotFloating() {
        assertFalse(ScaffoldFloatingPolicy.isFloating(false, false, true, -55, -56));
    }

    @Test
    void airWithoutAnyColumnNearbyIsNotFloating() {
        assertFalse(ScaffoldFloatingPolicy.isFloating(false, false, false, -54, Integer.MIN_VALUE));
    }
}
