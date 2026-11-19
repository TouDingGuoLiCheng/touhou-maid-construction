package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldPathLockPolicyTest {
    private static final List<ScaffoldLayout.Cell> PATH = List.of(
            new ScaffoldLayout.Cell(21, -60, -5),
            new ScaffoldLayout.Cell(21, -59, -5),
            new ScaffoldLayout.Cell(21, -58, -5),
            new ScaffoldLayout.Cell(22, -58, -5),
            new ScaffoldLayout.Cell(58, -53, -7));

    @Test
    void standingOnPathCellIsNotOffPath() {
        assertFalse(ScaffoldPathLockPolicy.isOffLockedPath(21, -59, -5, PATH));
    }

    @Test
    void oneBlockBesidePathIsStillOnPath() {
        assertFalse(ScaffoldPathLockPolicy.isOffLockedPath(22, -59, -5, PATH));
    }

    @Test
    void farAwayFromEveryPathCellIsOffPath() {
        assertTrue(ScaffoldPathLockPolicy.isOffLockedPath(21, -39, -5, PATH));
        assertTrue(ScaffoldPathLockPolicy.isOffLockedPath(5, -60, 20, PATH));
    }

    @Test
    void emptyLockMeansOffPath() {
        assertTrue(ScaffoldPathLockPolicy.isOffLockedPath(21, -60, -5, List.of()));
        assertTrue(ScaffoldPathLockPolicy.isOffLockedPath(21, -60, -5, null));
    }

    @Test
    void keepExecutingWhenLockedAndOnPathAndPlaceOk() {
        assertTrue(ScaffoldPathLockPolicy.shouldExecuteLockedPath(true, false, false));
    }

    @Test
    void replanWhenPlaceBlocked() {
        assertFalse(ScaffoldPathLockPolicy.shouldExecuteLockedPath(true, true, false));
        assertTrue(ScaffoldPathLockPolicy.shouldReplan(true, true, false));
    }

    @Test
    void replanWhenOffPath() {
        assertFalse(ScaffoldPathLockPolicy.shouldExecuteLockedPath(true, false, true));
        assertTrue(ScaffoldPathLockPolicy.shouldReplan(true, false, true));
    }

    @Test
    void noLockMeansDoNotExecuteLockedPath() {
        assertFalse(ScaffoldPathLockPolicy.shouldExecuteLockedPath(false, false, false));
        assertTrue(ScaffoldPathLockPolicy.shouldReplan(false, false, false));
    }

    @Test
    void placeAlreadyScaffoldIsNotPlaceBlocked() {
        assertFalse(ScaffoldPathLockPolicy.isPlaceBlocked(true, false));
    }

    @Test
    void worldRejectsPlaceIsPlaceBlocked() {
        assertTrue(ScaffoldPathLockPolicy.isPlaceBlocked(false, true));
    }
}
