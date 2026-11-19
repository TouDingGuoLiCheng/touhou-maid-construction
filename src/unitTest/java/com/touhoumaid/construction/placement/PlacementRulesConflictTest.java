package com.touhoumaid.construction.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRulesConflictTest {

    @Test
    void unbreakableWhenDestroyTimeNegative() {
        assertTrue(PlacementRules.isUnbreakableDestroyTime(-1.0f));
        assertFalse(PlacementRules.isUnbreakableDestroyTime(0.0f));
        assertFalse(PlacementRules.isUnbreakableDestroyTime(1.5f));
    }

    @Test
    void emptyCellIsNeverConflict() {
        assertFalse(PlacementRules.isHardConflict(true, false, false, false, false));
        assertFalse(PlacementRules.isHardConflict(true, true, true, true, true));
    }

    @Test
    void solidBlockConflictsWithoutCoverOrIgnore() {
        assertTrue(PlacementRules.isHardConflict(false, false, false, false, false));
    }

    @Test
    void coverExemptsCoverableTerrain() {
        assertFalse(PlacementRules.isHardConflict(false, true, true, false, false));
        assertTrue(PlacementRules.isHardConflict(false, true, false, false, false));
    }

    @Test
    void ignoreExemptsDestroyableOverlaps() {
        assertFalse(PlacementRules.isHardConflict(false, false, false, true, false));
    }

    @Test
    void ignoreStillConflictsOnUnbreakable() {
        assertTrue(PlacementRules.isHardConflict(false, false, false, true, true));
        assertTrue(PlacementRules.isHardConflict(false, true, true, true, true));
    }
}
