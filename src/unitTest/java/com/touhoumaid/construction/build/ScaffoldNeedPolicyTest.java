package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldNeedPolicyTest {
    @Test
    void aStandLevelWithHerFeetNeedsNoScaffold() {
        assertTrue(ScaffoldNeedPolicy.isTriviallyReachable(2, 0, 1));
    }

    @Test
    void aSingleStepUpOrDownNeedsNoScaffold() {
        assertTrue(ScaffoldNeedPolicy.isTriviallyReachable(1, 1, 0));
        assertTrue(ScaffoldNeedPolicy.isTriviallyReachable(1, -1, 0));
    }

    @Test
    void twoBlocksUpIsARealClimb() {
        assertFalse(ScaffoldNeedPolicy.isTriviallyReachable(1, 2, 0));
        assertFalse(ScaffoldNeedPolicy.isTriviallyReachable(0, 2, 0));
    }

    @Test
    void farAwayStandsAreNotObviouslyWalkable() {
        assertFalse(ScaffoldNeedPolicy.isTriviallyReachable(3, 1, 0));
        assertFalse(ScaffoldNeedPolicy.isTriviallyReachable(0, 0, 3));
        assertTrue(ScaffoldNeedPolicy.isTriviallyReachable(2, 1, 0));
    }

    @Test
    void onScaffoldOnlyAdjacentStandsCountAsTrivial() {
        assertTrue(ScaffoldNeedPolicy.isTriviallyReachable(1, 0, 0, true));
        assertFalse(ScaffoldNeedPolicy.isTriviallyReachable(2, 0, 0, true));
    }

    @Test
    void vanillaReachTrustStopsAtTwoBlocksVertical() {
        assertTrue(ScaffoldNeedPolicy.trustVanillaPathReach(2));
        assertTrue(ScaffoldNeedPolicy.trustVanillaPathReach(-2));
        assertFalse(ScaffoldNeedPolicy.trustVanillaPathReach(3));
        assertFalse(ScaffoldNeedPolicy.trustVanillaPathReach(-3));
    }

    @Test
    void forceScaffoldWhenStillCannotInteractAcrossLargeVerticalGap() {
        assertFalse(ScaffoldNeedPolicy.shouldForceScaffoldForVerticalGap(-60, -58, true));
        assertFalse(ScaffoldNeedPolicy.shouldForceScaffoldForVerticalGap(-60, -58, false));
        assertTrue(ScaffoldNeedPolicy.shouldForceScaffoldForVerticalGap(-60, -56, false));
    }

    @Test
    void scaffoldPlanningDisabledOnFirstTwoRelativeLayers() {
        assertFalse(ScaffoldNeedPolicy.allowsScaffoldPlanning(0));
        assertFalse(ScaffoldNeedPolicy.allowsScaffoldPlanning(1));
        assertFalse(ScaffoldNeedPolicy.allowsScaffoldPlanning(-1));
    }

    @Test
    void scaffoldPlanningAllowedFromThirdRelativeLayer() {
        assertTrue(ScaffoldNeedPolicy.allowsScaffoldPlanning(2));
        assertTrue(ScaffoldNeedPolicy.allowsScaffoldPlanning(5));
    }
}
