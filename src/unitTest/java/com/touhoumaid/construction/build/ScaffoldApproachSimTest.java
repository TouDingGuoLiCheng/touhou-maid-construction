package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldApproachSimTest {
    @Test
    void climbsAirColumnThenStepsOntoSolidWorkStand() {
        // Maid at column base; solid work stand adjacent at top; air column is future scaffold.
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(1, 70, 0);
        Set<Long> existing = Set.of(ScaffoldLayout.packCell(0, 64, 0));
        Set<Long> air = new HashSet<>();
        for (int y = 65; y <= 70; y++) {
            air.add(ScaffoldLayout.packCell(0, y, 0));
        }

        Optional<ScaffoldApproachSim.Result> result =
                ScaffoldApproachSim.simulate(from, stand, existing, air, 512);
        assertTrue(result.isPresent());
        assertEquals(stand, result.get().workStand());
        assertFalse(result.get().toPlace().contains(stand));
        assertEquals(6, result.get().toPlace().size());
        assertEquals(new ScaffoldLayout.Cell(0, 70, 0), result.get().toPlace().get(5));
    }

    @Test
    void emptyWhenNoSimulatedRouteToSolidStand() {
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(5, 70, 5);
        Set<Long> existing = Set.of(ScaffoldLayout.packCell(0, 64, 0));
        Set<Long> air = Set.of(ScaffoldLayout.packCell(0, 65, 0));

        assertTrue(ScaffoldApproachSim.simulate(from, stand, existing, air, 256).isEmpty());
    }

    @Test
    void reusesExistingScaffoldWithoutReplacingIt() {
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(0, 66, 0);
        Set<Long> existing = new HashSet<>();
        existing.add(ScaffoldLayout.packCell(0, 64, 0));
        existing.add(ScaffoldLayout.packCell(0, 65, 0));
        existing.add(ScaffoldLayout.packCell(0, 66, 0));
        // stand is solid destination also listed in existing for pathing onto it
        Set<Long> air = Set.of();

        Optional<ScaffoldApproachSim.Result> result =
                ScaffoldApproachSim.simulate(from, stand, existing, air, 256);
        assertTrue(result.isPresent());
        assertTrue(result.get().toPlace().isEmpty());
        assertEquals(stand, result.get().workStand());
    }

    @Test
    void stillPlacesThroughMaidOccupiedAirMarkedExisting() {
        // passableFromLedger adds feet + head as existing without real scaffolds there.
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(1, 70, 0);
        Set<Long> existing = Set.of(
                ScaffoldLayout.packCell(0, 64, 0),
                ScaffoldLayout.packCell(0, 65, 0));
        Set<Long> air = new HashSet<>();
        for (int y = 64; y <= 70; y++) {
            air.add(ScaffoldLayout.packCell(0, y, 0));
        }

        Optional<ScaffoldApproachSim.Result> result =
                ScaffoldApproachSim.simulate(from, stand, existing, air, 512);
        assertTrue(result.isPresent());
        // Without the fix, head y=65 is skipped and toPlace starts at y=66 (size 5).
        assertEquals(7, result.get().toPlace().size());
        assertEquals(new ScaffoldLayout.Cell(0, 64, 0), result.get().toPlace().get(0));
        assertEquals(new ScaffoldLayout.Cell(0, 70, 0), result.get().toPlace().get(6));
    }

    @Test
    void solidWorkStandNeverListedInToPlaceEvenIfAlsoMarkedAir() {
        ScaffoldLayout.Cell from = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(0, 65, 0);
        Set<Long> existing = Set.of(ScaffoldLayout.packCell(0, 64, 0));
        Set<Long> air = Set.of(ScaffoldLayout.packCell(0, 65, 0));

        Optional<ScaffoldApproachSim.Result> result =
                ScaffoldApproachSim.simulate(from, stand, existing, air, 64);
        assertTrue(result.isPresent());
        assertTrue(result.get().toPlace().isEmpty());
    }
}
