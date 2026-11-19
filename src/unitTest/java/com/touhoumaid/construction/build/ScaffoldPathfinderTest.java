package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldPathfinderTest {
    @Test
    void climbsAScaffoldColumnToTheTop() {
        Set<Long> passable = new HashSet<>();
        for (int y = 64; y <= 70; y++) {
            passable.add(ScaffoldLayout.packCell(0, y, 0));
        }
        List<ScaffoldLayout.Cell> path = ScaffoldPathfinder.findPath(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 70, 0),
                passable,
                256);
        assertEquals(7, path.size());
        assertEquals(new ScaffoldLayout.Cell(0, 70, 0), path.get(path.size() - 1));
    }

    @Test
    void emptyWhenDestinationIsNotPassable() {
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(0, 64, 0));
        List<ScaffoldLayout.Cell> path = ScaffoldPathfinder.findPath(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 70, 0),
                passable,
                256);
        assertTrue(path.isEmpty());
    }

    @Test
    void walksAHorizontalArmThenStops() {
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(0, 70, 0));
        passable.add(ScaffoldLayout.packCell(1, 70, 0));
        passable.add(ScaffoldLayout.packCell(2, 70, 0));
        List<ScaffoldLayout.Cell> path = ScaffoldPathfinder.findPath(
                new ScaffoldLayout.Cell(0, 70, 0),
                new ScaffoldLayout.Cell(2, 70, 0),
                passable,
                64);
        assertEquals(3, path.size());
        assertEquals(new ScaffoldLayout.Cell(2, 70, 0), path.get(2));
    }
}
