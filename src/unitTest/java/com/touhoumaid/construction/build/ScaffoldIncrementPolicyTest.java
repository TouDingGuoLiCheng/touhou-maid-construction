package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScaffoldIncrementPolicyTest {
    @Test
    void firstCellAtFeetLevelCanBePlacedFromBeside() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(63, 0, 0, 0, 70, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(
                path, Set.of(), new ScaffoldLayout.Cell(1, 64, 0));
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 64, 0), next.cell());
    }

    @Test
    void firstCellAtFeetLevelCanBePlacedWhileStandingInIt() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(63, 0, 0, 0, 70, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(
                path, Set.of(), new ScaffoldLayout.Cell(0, 64, 0));
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 64, 0), next.cell());
    }

    @Test
    void firstMissingCellRequiresStandingBesideTheBase() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(1, 64, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, Set.of(), maid);
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), next.cell());
    }

    @Test
    void cannotPlaceTheWholeTowerFromTheGround() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        present.add(ScaffoldLayout.packCell(0, 65, 0));
        ScaffoldLayout.Cell maidOnGround = new ScaffoldLayout.Cell(1, 64, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidOnGround);
        assertEquals(ScaffoldIncrementPolicy.Kind.APPROACH, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), next.stand());
    }

    @Test
    void needsClimbWhenStandAboveIsAlreadyPresentOnSameColumn() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        present.add(ScaffoldLayout.packCell(0, 65, 0));
        present.add(ScaffoldLayout.packCell(0, 66, 0));
        ScaffoldLayout.Cell maidOnGround = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidOnGround);
        assertEquals(ScaffoldIncrementPolicy.Kind.CLIMB, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), next.cell());
    }

    @Test
    void standingOnPlacedCellPlacesTheNextOneUp() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        present.add(ScaffoldLayout.packCell(0, 65, 0));
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(0, 65, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maid);
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), next.cell());
    }

    @Test
    void standingBesidePreviousScaffoldCanPlaceNextUp() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        present.add(ScaffoldLayout.packCell(0, 65, 0));
        ScaffoldLayout.Cell maidBeside = new ScaffoldLayout.Cell(1, 65, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidBeside);
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), next.cell());
    }

    @Test
    void completePathMeansClimbToWorkFeet() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        for (ScaffoldLayout.Cell cell : path) {
            present.add(ScaffoldLayout.packCell(cell.x(), cell.y(), cell.z()));
        }
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(0, 65, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maid);
        assertEquals(ScaffoldIncrementPolicy.Kind.CLIMB, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 70, 0), next.cell());
    }

    @Test
    void completePathButMaidOffScaffoldApproachesNearestPathCell() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        for (ScaffoldLayout.Cell cell : path) {
            present.add(ScaffoldLayout.packCell(cell.x(), cell.y(), cell.z()));
        }
        ScaffoldLayout.Cell maidOffPath = new ScaffoldLayout.Cell(2, 64, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidOffPath);
        assertEquals(ScaffoldIncrementPolicy.Kind.APPROACH, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), next.cell());
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), next.stand());
    }

    @Test
    void completePathOffByOneNearTopDoesNotForceReturnToBottom() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        for (ScaffoldLayout.Cell cell : path) {
            present.add(ScaffoldLayout.packCell(cell.x(), cell.y(), cell.z()));
        }
        ScaffoldLayout.Cell maidNearTop = new ScaffoldLayout.Cell(1, 69, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidNearTop);
        assertEquals(ScaffoldIncrementPolicy.Kind.APPROACH, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 69, 0), next.cell());
        assertEquals(new ScaffoldLayout.Cell(0, 69, 0), next.stand());
    }

    @Test
    void emptyPathFails() {
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(
                List.of(), Set.of(), new ScaffoldLayout.Cell(0, 64, 0));
        assertEquals(ScaffoldIncrementPolicy.Kind.FAILED, next.kind());
        assertNull(next.cell());
    }

    @Test
    void standingInMissingCellAbovePreviousPlacesItInsteadOfApproachingTop() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        Set<Long> present = new HashSet<>();
        present.add(ScaffoldLayout.packCell(0, 65, 0));
        // Maid somehow stands in the next missing cell (partial tower / stacked crew).
        ScaffoldLayout.Cell maidInMissing = new ScaffoldLayout.Cell(0, 66, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(path, present, maidInMissing);
        assertEquals(ScaffoldIncrementPolicy.Kind.PLACE, next.kind());
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), next.cell());
    }

    @Test
    void doesNotPlaceScaffoldOnSolidWorkStandAtPathEnd() {
        ScaffoldLayout.Cell workStand = new ScaffoldLayout.Cell(0, 70, 0);
        List<ScaffoldLayout.Cell> path = List.of(
                new ScaffoldLayout.Cell(0, 65, 0),
                new ScaffoldLayout.Cell(0, 66, 0),
                new ScaffoldLayout.Cell(0, 67, 0),
                new ScaffoldLayout.Cell(0, 68, 0),
                new ScaffoldLayout.Cell(0, 69, 0),
                workStand);
        Set<Long> present = new HashSet<>();
        for (int y = 65; y <= 69; y++) {
            present.add(ScaffoldLayout.packCell(0, y, 0));
        }
        ScaffoldLayout.Cell maidOnTopScaffold = new ScaffoldLayout.Cell(0, 69, 0);
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(
                path, present, maidOnTopScaffold, workStand);
        assertEquals(ScaffoldIncrementPolicy.Kind.CLIMB, next.kind());
        assertEquals(workStand, next.cell());
    }

    @Test
    void approachWalkTargetIsStandNotUnfinishedWorkFeet() {
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(0, 65, 0);
        ScaffoldLayout.Cell missing = new ScaffoldLayout.Cell(0, 66, 0);
        ScaffoldLayout.Cell unfinishedTop = new ScaffoldLayout.Cell(0, 70, 0);
        ScaffoldIncrementPolicy.Next approach =
                new ScaffoldIncrementPolicy.Next(ScaffoldIncrementPolicy.Kind.APPROACH, missing, stand);
        assertEquals(stand, ScaffoldIncrementPolicy.approachWalkCell(approach, unfinishedTop));
    }
}
