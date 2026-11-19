package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionWalkPolicyTest {
    @Test
    void usesScaffoldOnlyWhenARealRouteExists() {
        assertTrue(ConstructionWalkPolicy.useScaffoldRoute(3));
        assertFalse(ConstructionWalkPolicy.useScaffoldRoute(1));
        assertFalse(ConstructionWalkPolicy.useScaffoldRoute(0));
    }

    @Test
    void neverPinsTheMaidOnTheCurrentScaffoldCell() {
        assertFalse(ConstructionWalkPolicy.holdInPlaceOnScaffold());
    }

    @Test
    void refreshesVanillaPathWhenDestinationChanges() {
        assertTrue(ConstructionWalkPolicy.shouldRefreshVanillaPath(true, true, false, 8, 0));
    }

    @Test
    void doesNotRefreshVanillaPathEveryTickWhileWalking() {
        assertFalse(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, true, false, 8, 0));
    }

    @Test
    void refreshesVanillaPathWhenNavigationNeverStarted() {
        assertTrue(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, false, false, 8, 0));
    }

    @Test
    void doesNotRepathEveryTickWhenNavReportsDone() {
        assertFalse(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, true, true, 2, 0));
        assertFalse(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, false, true, 0, 40));
    }

    @Test
    void repathsNearStandOnlyAfterCooldownWhenNavDone() {
        assertFalse(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, false, true, 2, 14));
        assertTrue(ConstructionWalkPolicy.shouldRefreshVanillaPath(false, false, true, 2, 15));
    }

    @Test
    void usesExactPathRangeWhenAlreadyNear() {
        assertEquals(0, ConstructionWalkPolicy.vanillaPathRange(1));
        assertEquals(0, ConstructionWalkPolicy.vanillaPathRange(2));
        assertEquals(1, ConstructionWalkPolicy.vanillaPathRange(8));
    }

    @Test
    void neverTeleportsOntoAnAdjacentWorkStand() {
        assertFalse(ConstructionWalkPolicy.shouldSnapAdjacentWork(1, 20));
        assertFalse(ConstructionWalkPolicy.shouldSnapAdjacentWork(2, 40));
        assertFalse(ConstructionWalkPolicy.shouldSnapAdjacentWork(1, 19));
    }


    @Test
    void keepsScaffoldHoldAtRouteEndWhenStillOnScaffold() {
        assertTrue(ConstructionWalkPolicy.shouldHoldAtScaffoldRouteEnd(1, true));
        assertFalse(ConstructionWalkPolicy.shouldHoldAtScaffoldRouteEnd(1, false));
        assertFalse(ConstructionWalkPolicy.shouldHoldAtScaffoldRouteEnd(0, true));
        assertFalse(ConstructionWalkPolicy.shouldHoldAtScaffoldRouteEnd(2, true));
    }

    @Test
    void defersLandNavWhileOnScaffoldWithoutRoute() {
        // Same-column empty route: stay on scaffold (climb/descend), do not land-BFS.
        assertTrue(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(true, 0, 0));
        assertTrue(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(true, 0, 1));
        assertFalse(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(true, 2, 0));
        assertFalse(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(false, 0, 0));
    }

    @Test
    void allowsLandNavOffScaffoldWhenWorkIsOnAnotherColumn() {
        // Empty scaffold route + target elsewhere → walk off the tower on land, do not pin.
        assertFalse(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(true, 0, 2));
        assertFalse(ConstructionWalkPolicy.shouldDeferLandNavWhileOnScaffold(true, 0, 8));
    }

    @Test
    void blocksVerticalScaffoldSnapWhenWorkIsOffColumn() {
        assertFalse(ConstructionWalkPolicy.shouldAllowVerticalScaffoldSnap(2));
        assertFalse(ConstructionWalkPolicy.shouldAllowVerticalScaffoldSnap(8));
        assertTrue(ConstructionWalkPolicy.shouldAllowVerticalScaffoldSnap(0));
        assertTrue(ConstructionWalkPolicy.shouldAllowVerticalScaffoldSnap(1));
    }

    @Test
    void descendsToRelocateWhenOnScaffoldCannotInteract() {
        assertTrue(ConstructionWalkPolicy.shouldDescendToRelocate(true, false, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(true, true, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(false, false, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(true, false, 3));
    }

    @Test
    void lowersWorkStandWhenStuckOnUselessTowerTop() {
        // Same-column stand at feet, wall at same Y, cannot interact → step down.
        assertTrue(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -48, -48, 0, -48));
    }

    @Test
    void doesNotLowerWorkStandWhileClimbingSameColumn() {
        // 幸存者酒狐: APPROACH one cell above.
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -58, -59, 1, -46));
        // 精灵酒狐: planned high stand / reuse pinned at feet while wall is above.
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -46, -52, 0, -46));
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -51, -51, 0, -46));
    }

    @Test
    void lowersWorkStandWhenPlannedStandIsFarAndAlreadyBelow() {
        // 花嫁酒狐: feet Y=-48 z=326, workFeet=67,-60,261 (dxz=65) must still start climbing down.
        assertTrue(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -60, -48, 65, -46));
        assertTrue(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -60, -48, 2, -46));
    }

    @Test
    void doesNotStealSameColumnDescentWhenWorkIsAlreadyBelow() {
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -60, -48, 0, -60));
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, false, -60, -48, 1, -60));
    }

    @Test
    void doesNotLowerWorkStandWhenCanInteractOrOffScaffold() {
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(true, true, -60, -48, 65, -46));
        assertFalse(ConstructionWalkPolicy.shouldLowerWorkStandToDescend(false, false, -60, -48, 65, -46));
    }

    @Test
    void stepsDownSameColumnWhenWorkIsOneCellBelow() {
        assertTrue(ConstructionWalkPolicy.shouldDescendOnScaffold(true, 0, -1, 0, -48, -48));
        assertFalse(ConstructionWalkPolicy.shouldDescendOnScaffold(true, 0, -12, 65, -48, -48));
    }

    @Test
    void keepsLocalDescendStandWhenReplanPointsAtAnotherColumn() {
        assertTrue(ConstructionWalkPolicy.shouldKeepLocalDescendAgainstOffColumnReplan(
                true, 67, -48, 326, 67, -49, 326, 67, -60, 261));
        assertFalse(ConstructionWalkPolicy.shouldKeepLocalDescendAgainstOffColumnReplan(
                true, 67, -48, 326, 67, -49, 326, 67, -49, 326));
        assertFalse(ConstructionWalkPolicy.shouldKeepLocalDescendAgainstOffColumnReplan(
                false, 67, -48, 326, 67, -49, 326, 67, -60, 261));
    }

    @Test
    void doesNotKeepLocalDescendStandWhenReplanIsAClimb() {
        assertFalse(ConstructionWalkPolicy.shouldKeepLocalDescendAgainstOffColumnReplan(
                true, 67, -59, 254, 67, -60, 254, 67, -58, 255));
    }

    @Test
    void nudgesFlatGroundWhenRouteEmptyAndNear() {
        assertTrue(ConstructionWalkPolicy.shouldNudgeFlatGround(false, 0, 2, 0));
        assertFalse(ConstructionWalkPolicy.shouldNudgeFlatGround(true, 0, 2, 0));
        assertFalse(ConstructionWalkPolicy.shouldNudgeFlatGround(false, 0, 5, 0));
    }

    @Test
    void nudgesScaffoldWalkwayWhenSidewaysOnSameLayer() {
        assertTrue(ConstructionWalkPolicy.shouldNudgeScaffoldWalkway(true, 0, 9, 0, 9));
        assertTrue(ConstructionWalkPolicy.shouldNudgeScaffoldWalkway(true, 0, 4, 1, 2));
        assertFalse(ConstructionWalkPolicy.shouldNudgeScaffoldWalkway(true, 0, 9, 10, 5));
        assertFalse(ConstructionWalkPolicy.shouldNudgeScaffoldWalkway(true, 2, 9, 0, 9));
        assertFalse(ConstructionWalkPolicy.shouldNudgeScaffoldWalkway(false, 0, 9, 0, 9));
    }

    @Test
    void prefersDirectColumnClimbBeforeGraphRoute() {
        assertTrue(ConstructionWalkPolicy.shouldPreferDirectColumnClimb(true, 1, 0, -51, -50));
        assertTrue(ConstructionWalkPolicy.shouldPreferDirectColumnClimb(true, 2, 1, -52, -50));
        assertFalse(ConstructionWalkPolicy.shouldPreferDirectColumnClimb(true, 0, 0, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldPreferDirectColumnClimb(true, 1, 2, -51, -50));
    }

    @Test
    void stepsOffScaffoldHorizontallyWhenWorkIsSideways() {
        assertTrue(ConstructionWalkPolicy.shouldStepOffScaffoldHorizontally(true, 1, 0));
        assertTrue(ConstructionWalkPolicy.shouldStepOffScaffoldHorizontally(true, 3, -1));
        assertFalse(ConstructionWalkPolicy.shouldStepOffScaffoldHorizontally(true, 0, 1));
        assertFalse(ConstructionWalkPolicy.shouldStepOffScaffoldHorizontally(false, 1, 0));
    }

    @Test
    void snapsAdjacentWorkAfterShortStall() {
        assertFalse(ConstructionWalkPolicy.shouldSnapAdjacentWork(1, 20));
    }

    @Test
    void escalatesRepeatedNotNeededWalk() {
        assertFalse(ConstructionWalkPolicy.shouldEscalateNotNeededWalk(1));
        assertTrue(ConstructionWalkPolicy.shouldEscalateNotNeededWalk(2));
    }

    @Test
    void climbsTowardHigherWorkWhenRouteEmptyOnScaffold() {
        assertTrue(ConstructionWalkPolicy.shouldClimbTowardHigherWork(true, 0, 10, 0));
        assertTrue(ConstructionWalkPolicy.shouldClimbTowardHigherWork(true, 0, 10, 1));
        assertFalse(ConstructionWalkPolicy.shouldClimbTowardHigherWork(true, 0, 10, 2));
        assertFalse(ConstructionWalkPolicy.shouldClimbTowardHigherWork(true, 0, 0, 0));
        assertFalse(ConstructionWalkPolicy.shouldClimbTowardHigherWork(false, 0, 10, 0));
        assertFalse(ConstructionWalkPolicy.shouldClimbTowardHigherWork(true, 3, 10, 0));
    }

    @Test
    void seeksAlternateStandOnlyWhenAdjacent() {
        assertTrue(ConstructionWalkPolicy.shouldSeekAlternateStand(true, 2));
        assertFalse(ConstructionWalkPolicy.shouldSeekAlternateStand(true, 3));
        assertFalse(ConstructionWalkPolicy.shouldSeekAlternateStand(false, 1));
    }

    @Test
    void ignoresApproachStandWellBelowFeetWhileOnScaffold() {
        assertTrue(ConstructionWalkPolicy.shouldIgnoreLowerApproachStand(true, -60, -50));
        assertFalse(ConstructionWalkPolicy.shouldIgnoreLowerApproachStand(true, -51, -50));
        assertFalse(ConstructionWalkPolicy.shouldIgnoreLowerApproachStand(false, -60, -50));
    }

    @Test
    void retainsClimbWorkFeetWhenReplanSwitchesColumn() {
        assertTrue(ConstructionWalkPolicy.shouldRetainClimbWorkFeet(
                true, 162, -58, 204, 162, -50, 204, 162, -53, 203));
        assertTrue(ConstructionWalkPolicy.shouldRetainClimbWorkFeet(
                true, 162, -58, 204, 162, -50, 204, 162, -55, 204));
        assertFalse(ConstructionWalkPolicy.shouldRetainClimbWorkFeet(
                true, 162, -58, 204, 162, -50, 204, 162, -50, 204));
        assertFalse(ConstructionWalkPolicy.shouldRetainClimbWorkFeet(
                false, 162, -58, 204, 162, -50, 204, 162, -53, 203));
    }

    @Test
    void retainsHigherClimbTargetWhenReplanDowngradesApproach() {
        assertTrue(ConstructionWalkPolicy.shouldRetainHigherClimbTarget(true, -58, -49, -52));
        assertFalse(ConstructionWalkPolicy.shouldRetainHigherClimbTarget(true, -58, -52, -49));
        assertFalse(ConstructionWalkPolicy.shouldRetainHigherClimbTarget(false, -58, -49, -52));
    }

    @Test
    void blocksUpwardScaffoldStepWhileSidewaysOfWork() {
        assertTrue(ConstructionWalkPolicy.shouldBlockUpwardScaffoldStep(2, -58, -57));
        assertFalse(ConstructionWalkPolicy.shouldBlockUpwardScaffoldStep(0, -58, -57));
        assertFalse(ConstructionWalkPolicy.shouldBlockUpwardScaffoldStep(2, -58, -59));
    }

    @Test
    void snapsScaffoldProgressWithSmallXzOffset() {
        assertTrue(ConstructionWalkPolicy.shouldSnapScaffoldProgress(true, true, 6, 2, 20, -58, -52, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapScaffoldProgress(true, true, 6, 3, 20, -58, -52, -50));
        assertTrue(ConstructionWalkPolicy.shouldSnapScaffoldProgress(true, false, 1, 0, 20, -51, -50, -50));
    }

    @Test
    void snapsClimbStallMidColumn() {
        assertTrue(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 8, 0, 20, -58, -50, -50));
        assertTrue(ConstructionWalkPolicy.shouldSnapScaffoldProgress(true, true, 8, 0, 20, -58, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 8, 0, 19, -58, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 8, 2, 40, -58, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 0, 0, 40, -50, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 0, 0, 40, -49, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(true, true, 8, 0, 20, -49, -40, -50));
    }

    @Test
    void rebuildsScaffoldAfterNavStall() {
        assertTrue(ConstructionWalkPolicy.shouldRebuildScaffoldWhileNav(true, 40, false));
        assertFalse(ConstructionWalkPolicy.shouldRebuildScaffoldWhileNav(true, 40, true));
        assertTrue(ConstructionWalkPolicy.shouldRebuildScaffoldWhileNav(true, 80, true));
        assertFalse(ConstructionWalkPolicy.shouldRebuildScaffoldWhileNav(true, 39, false));
        assertFalse(ConstructionWalkPolicy.shouldRebuildScaffoldWhileNav(false, 40, false));
    }

    @Test
    void onlyHardStopsMotionWhenReleasingNoGravity() {
        assertTrue(ConstructionWalkPolicy.shouldHardStopOnScaffoldRelease(true));
        assertFalse(ConstructionWalkPolicy.shouldHardStopOnScaffoldRelease(false));
    }

    @Test
    void releasesHoverOutsideScaffoldMovePhase() {
        assertTrue(ConstructionWalkPolicy.shouldReleaseScaffoldHover(true, false));
        assertFalse(ConstructionWalkPolicy.shouldReleaseScaffoldHover(false, false));
        assertFalse(ConstructionWalkPolicy.shouldReleaseScaffoldHover(true, true));
    }

    @Test
    void forceDisablesHoverWhenNoGravityRemainsOffScaffold() {
        assertTrue(ConstructionWalkPolicy.shouldForceDisableHover(true, false, false));
        assertTrue(ConstructionWalkPolicy.shouldForceDisableHover(true, false, true));
        assertFalse(ConstructionWalkPolicy.shouldForceDisableHover(true, true, false));
        assertFalse(ConstructionWalkPolicy.shouldForceDisableHover(false, false, false));
    }
}
