package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldLayoutTest {
    @Test
    void columnThenArmIsALineNotAVolume() {
        List<ScaffoldLayout.Cell> cells = ScaffoldLayout.columnThenArm(64, 0, 0, 3, 70, 0);
        assertEquals(9, cells.size());
        assertTrue(cells.size() < 20, "must not fill a 3x6x1 slab");
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), cells.get(0));
        assertEquals(new ScaffoldLayout.Cell(0, 70, 0), cells.get(5));
        assertEquals(new ScaffoldLayout.Cell(3, 70, 0), cells.get(8));
    }

    @Test
    void sameXzTowerHasNoArm() {
        List<ScaffoldLayout.Cell> cells = ScaffoldLayout.columnThenArm(64, 5, 8, 5, 70, 8);
        assertEquals(6, cells.size());
        for (ScaffoldLayout.Cell cell : cells) {
            assertEquals(5, cell.x());
            assertEquals(8, cell.z());
        }
    }

    @Test
    void columnOnlyBuildsVerticalCells() {
        List<ScaffoldLayout.Cell> cells = ScaffoldLayout.columnOnly(64, 5, 8, 70);
        assertEquals(6, cells.size());
        for (ScaffoldLayout.Cell cell : cells) {
            assertEquals(5, cell.x());
            assertEquals(8, cell.z());
        }
        assertEquals(new ScaffoldLayout.Cell(5, 65, 8), cells.get(0));
        assertEquals(new ScaffoldLayout.Cell(5, 70, 8), cells.get(5));
    }

    @Test
    void maidFarFromExistingColumnStartsHerOwn() {
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(0, 0));
        int[] col = ScaffoldLayout.pickColumn(5, 0, existing, 7, 8, 4);
        assertEquals(8, col[0]);
        assertEquals(4, col[1]);
    }

    @Test
    void maidAlreadyOnAColumnReusesIt() {
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(0, 0));
        int[] col = ScaffoldLayout.pickColumn(5, 0, existing, 7, 0, 0);
        assertEquals(0, col[0]);
        assertEquals(0, col[1]);
    }

    @Test
    void maidBesideATowerStartsAColumnUnderHerFeet() {
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(0, 0));
        int[] col = ScaffoldLayout.pickColumn(0, 5, existing, 7, 0, 1);
        assertEquals(0, col[0]);
        assertEquals(1, col[1]);
    }

    @Test
    void maidAtAColumnDoesNotReuseItWhenWorkFeetAreBeyondArm() {
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(0, 0));
        int[] col = ScaffoldLayout.pickColumn(20, 0, existing, 7, 0, 0);
        assertEquals(20, col[0]);
        assertEquals(0, col[1]);
    }

    @Test
    void doesNotReuseAColumnAnotherMaidIsWorkingOn() {
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(0, 0));
        Set<Long> blocked = new HashSet<>();
        blocked.add(ScaffoldLayout.packXz(0, 0));
        int[] col = ScaffoldLayout.pickColumn(5, 0, existing, 7, 0, 0, blocked);
        assertTrue(col[0] != 0 || col[1] != 0);
    }

    @Test
    void stepsAsideWhenTheWorkFeetColumnIsTaken() {
        Set<Long> blocked = new HashSet<>();
        blocked.add(ScaffoldLayout.packXz(131, 190));
        int[] col = ScaffoldLayout.pickColumn(131, 190, new HashSet<>(), 7, 131, 205, blocked);
        assertTrue(col[0] != 131 || col[1] != 190);
        assertTrue(Math.abs(col[0] - 131) + Math.abs(col[1] - 190) <= 7);
    }

    @Test
    void keepsHerOwnColumnWhenNobodyElseHasIt() {
        int[] col = ScaffoldLayout.pickColumn(5, 0, new HashSet<>(), 7, 3, 0, new HashSet<>());
        assertEquals(3, col[0]);
        assertEquals(0, col[1]);
    }

    @Test
    void neverTowersAtHerOwnXzWhenSheIsOutsideTheBuildRange() {
        ScaffoldBoundsPolicy.Box box = new ScaffoldBoundsPolicy.Box(0, 60, 0, 10, 70, 10);
        ScaffoldLayout.ColumnFilter inRange = (x, z) -> ScaffoldBoundsPolicy.contains(box, 2, x, 65, z);
        int[] col = ScaffoldLayout.pickColumn(5, 5, new HashSet<>(), 7, 30, 5, Set.of(), inRange);
        assertEquals(5, col[0]);
        assertEquals(5, col[1]);
    }

    @Test
    void skipsAnExistingColumnThatSitsOutsideTheBuildRange() {
        ScaffoldBoundsPolicy.Box box = new ScaffoldBoundsPolicy.Box(0, 60, 0, 10, 70, 10);
        ScaffoldLayout.ColumnFilter inRange = (x, z) -> ScaffoldBoundsPolicy.contains(box, 2, x, 65, z);
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packXz(30, 5));
        int[] col = ScaffoldLayout.pickColumn(5, 5, existing, 7, 30, 5, Set.of(), inRange);
        assertTrue(ScaffoldBoundsPolicy.contains(box, 2, col[0], 65, col[1]));
    }
}
