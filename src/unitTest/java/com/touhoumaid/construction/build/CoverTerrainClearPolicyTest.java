package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverTerrainClearPolicyTest {
    @Test
    void neverClearsWhenCoverTerrainIsOff() {
        assertFalse(CoverTerrainClearPolicy.mayClear(false, true, false, false, true));
    }

    @Test
    void neverClearsOutsideTheBlueprintAabb() {
        assertFalse(CoverTerrainClearPolicy.mayClear(true, false, false, false, true));
    }

    @Test
    void neverClearsScaffoldOrAlreadyPlannedBlocks() {
        assertFalse(CoverTerrainClearPolicy.mayClear(true, true, true, false, true));
        assertFalse(CoverTerrainClearPolicy.mayClear(true, true, false, true, true));
    }

    @Test
    void neverClearsACellThatAlreadyMatchesTheSchematicEvenWhenIgnoreOverlaps() {
        assertFalse(CoverTerrainClearPolicy.mayClear(
                true, true, true, false, true, true, true));
        assertFalse(CoverTerrainClearPolicy.mayClear(
                false, true, true, false, true, false, true));
    }

    @Test
    void neverClearsNonCoverableBlocks() {
        assertFalse(CoverTerrainClearPolicy.mayClear(true, true, false, false, false));
    }

    @Test
    void clearsCoverableTerrainInsideTheBox() {
        assertTrue(CoverTerrainClearPolicy.mayClear(true, true, false, false, true));
    }

    @Test
    void clearsDestroyableOverlapWhenIgnoreOverlapsEvenIfCoverOff() {
        assertTrue(CoverTerrainClearPolicy.mayClear(
                false, true, true, false, false, false, true));
    }

    @Test
    void neverClearsUnbreakableEvenWhenIgnoreOverlaps() {
        assertFalse(CoverTerrainClearPolicy.mayClear(
                false, true, true, false, false, false, false));
    }

    @Test
    void neverClearsWhenCoverAndIgnoreAreBothOff() {
        assertFalse(CoverTerrainClearPolicy.mayClear(
                false, false, true, false, false, true, true));
    }

    @Test
    void exclusiveAabbMatchesBlueprintFootprint() {
        assertTrue(CoverTerrainClearPolicy.inExclusiveAabb(0, 64, 0, 10, 80, 10, 0, 64, 0));
        assertTrue(CoverTerrainClearPolicy.inExclusiveAabb(0, 64, 0, 10, 80, 10, 9, 79, 9));
        assertFalse(CoverTerrainClearPolicy.inExclusiveAabb(0, 64, 0, 10, 80, 10, 10, 64, 0));
        assertFalse(CoverTerrainClearPolicy.inExclusiveAabb(0, 64, 0, 10, 80, 10, 0, 80, 0));
        assertFalse(CoverTerrainClearPolicy.inExclusiveAabb(0, 64, 0, 10, 80, 10, -1, 64, 0));
    }

    @Test
    void tickBudgetCapsAtFourClears() {
        assertEquals(4, CoverTerrainClearPolicy.remainingClears(0));
        assertEquals(3, CoverTerrainClearPolicy.remainingClears(1));
        assertEquals(0, CoverTerrainClearPolicy.remainingClears(4));
        assertEquals(0, CoverTerrainClearPolicy.remainingClears(9));
    }

    @Test
    void occupancyHeadIsTheCellAboveFeet() {
        assertEquals(65, CoverTerrainClearPolicy.occupancyHeadY(64));
    }

    @Test
    void corridorHeadroomIsTheCellAboveTheHit() {
        assertEquals(-59, CoverTerrainClearPolicy.corridorHeadroomY(-60));
    }
}
