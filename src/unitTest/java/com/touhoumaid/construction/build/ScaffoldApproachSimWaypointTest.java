package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldApproachSimWaypointTest {
    @Test
    void waypointPathClimbsPreferredColumnBeforeBridgingToStand() {
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 60, 0);
        ScaffoldLayout.Cell climbTop = new ScaffoldLayout.Cell(5, 65, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(6, 65, 0);
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldPathfinder.key(from));
        Set<Long> air = new HashSet<>();
        for (int y = 60; y <= 65; y++) {
            air.add(ScaffoldLayout.packCell(5, y, 0));
        }
        for (int x = 0; x <= 5; x++) {
            air.add(ScaffoldLayout.packCell(x, 60, 0));
        }
        air.add(ScaffoldLayout.packCell(6, 65, 0));
        Optional<ScaffoldApproachSim.Result> result = ScaffoldApproachSim.simulateViaWaypoint(
                from, climbTop, stand, existing, air, 4096);
        assertTrue(result.isPresent());
        List<ScaffoldLayout.Cell> path = result.get().path();
        assertFalse(path.isEmpty());
        assertEquals(stand, path.get(path.size() - 1));
        boolean touchedClimbColumn = path.stream().anyMatch(c -> c.x() == 5 && c.z() == 0 && c.y() >= 64);
        assertTrue(touchedClimbColumn);
    }
}
