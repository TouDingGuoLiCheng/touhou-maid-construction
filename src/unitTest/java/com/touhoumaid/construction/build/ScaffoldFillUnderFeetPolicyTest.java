package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldFillUnderFeetPolicyTest {
    @Test
    void skipsNotNeededWhenStandIsOnATallerNeighborColumn() {
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(2, 3));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(0, 3));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(2, 1));
    }

    @Test
    void keepsAdjacentStandsEligibleSoSheWalksOntoAWallTop() {
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(1, 2));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(1, 3));
    }

    @Test
    void fillsWhenStuckBesideATallerStand() {
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillWhenStuckBeside(1, 3, 20));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldFillWhenStuckBeside(1, 3, 19));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldFillWhenStuckBeside(2, 3, 40));
    }

    @Test
    void fillsTheGapWhenHoveringAboveColumnTop() {
        assertEquals(-49, ScaffoldFillUnderFeetPolicy.fillY(-48, -48, -50, false));
    }

    @Test
    void fillsCurrentCellToStartALocalColumn() {
        assertEquals(-60, ScaffoldFillUnderFeetPolicy.fillY(-60, -57, Integer.MIN_VALUE, false));
    }

    @Test
    void raisesExistingColumnWhenAlreadyStandingInScaffold() {
        assertEquals(-56, ScaffoldFillUnderFeetPolicy.fillY(-57, -55, -57, true));
    }

    @Test
    void doesNotFillWhenWorkIsNotHigher() {
        assertEquals(
                Integer.MIN_VALUE,
                ScaffoldFillUnderFeetPolicy.fillY(-60, -60, Integer.MIN_VALUE, false));
    }

    @Test
    void recentersWhenHuggingScaffoldPosts() {
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldCenterInScaffoldCell(0.08D, 0.5D));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldCenterInScaffoldCell(0.5D, 0.5D));
    }

    @Test
    void waitsForStableFootingOnScaffoldBeforeMoving() {
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldWaitForStableFooting(0.08D, 0.5D, 0.0D));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldWaitForStableFooting(0.5D, 0.5D, 0.03D));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldWaitForStableFooting(0.5D, 0.5D, 0.0D));
    }

    @Test
    void doesNotFillFarFromWorkColumnWhileWalkingIn() {
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(5, 4, false, false, false, 0));
        assertFalse(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(1, 4, false, false, false, 0));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(1, 4, false, false, false, 20));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(1, 4, false, false, true, 0));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(3, 4, true, false, false, 0));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(4, 4, true, false, false, 0));
        assertTrue(ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(5, 4, false, true, false, 0));
    }
}
