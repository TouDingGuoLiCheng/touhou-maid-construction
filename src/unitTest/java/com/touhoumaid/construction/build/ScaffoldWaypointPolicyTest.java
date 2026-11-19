package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldWaypointPolicyTest {
    @Test
    void extractsStartColumnTopsAndWorkFeet() {
        List<ScaffoldLayout.Cell> path = List.of(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 65, 0),
                new ScaffoldLayout.Cell(0, 66, 0),
                new ScaffoldLayout.Cell(1, 66, 0),
                new ScaffoldLayout.Cell(2, 66, 0));
        ScaffoldLayout.Cell work = new ScaffoldLayout.Cell(2, 66, 1);
        List<ScaffoldLayout.Cell> waypoints = ScaffoldWaypointPolicy.extractCells(path, work);
        assertEquals(5, waypoints.size());
        assertEquals(new ScaffoldLayout.Cell(0, 64, 0), waypoints.get(0));
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), waypoints.get(1));
        assertEquals(new ScaffoldLayout.Cell(1, 66, 0), waypoints.get(2));
        assertEquals(new ScaffoldLayout.Cell(2, 66, 0), waypoints.get(3));
        assertEquals(work, waypoints.get(4));
    }

    @Test
    void initialIndexSkipsAlreadyReachedNodes() {
        List<ScaffoldLayout.Cell> waypoints = List.of(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 66, 0),
                new ScaffoldLayout.Cell(2, 66, 0));
        assertEquals(1, ScaffoldWaypointPolicy.initialIndexCells(waypoints, new ScaffoldLayout.Cell(0, 65, 0)));
        assertEquals(2, ScaffoldWaypointPolicy.initialIndexCells(waypoints, new ScaffoldLayout.Cell(0, 66, 0)));
    }

    @Test
    void hasArrivedAllowsOneBlockVerticalOffset() {
        assertTrue(ScaffoldWaypointPolicy.hasArrivedCells(
                new ScaffoldLayout.Cell(1, 65, 2),
                new ScaffoldLayout.Cell(1, 66, 2)));
    }
}
