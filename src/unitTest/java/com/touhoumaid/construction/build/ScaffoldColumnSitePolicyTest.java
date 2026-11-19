package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldColumnSitePolicyTest {
    @Test
    void prefersStartingEdgeWhenShaftIsClear() {
        Set<Long> blocked = Set.of();
        Optional<ScaffoldLayout.Cell> picked = ScaffoldColumnSitePolicy.pick(
                10, 64, 20,
                10, 19,
                60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z)),
                8);
        assertTrue(picked.isPresent());
        assertEquals(10, picked.get().x());
        assertEquals(20, picked.get().z());
    }

    @Test
    void nudgesOutwardAwayFromTargetWhenShaftBlocked() {
        // Target (10,19), preferred edge (10,20). Shaft blocked → step further +Z to (10,21).
        Set<Long> blocked = new HashSet<>();
        for (int y = 60; y <= 70; y++) {
            blocked.add(ScaffoldLayout.packCell(10, y, 20));
        }
        Optional<ScaffoldLayout.Cell> picked = ScaffoldColumnSitePolicy.pick(
                10, 64, 20,
                10, 19,
                60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z)),
                8);
        assertTrue(picked.isPresent());
        assertEquals(10, picked.get().x());
        assertEquals(21, picked.get().z());
    }

    @Test
    void keepsNudgingUntilShaftClear() {
        Set<Long> blocked = new HashSet<>();
        for (int z = 20; z <= 22; z++) {
            for (int y = 60; y <= 70; y++) {
                blocked.add(ScaffoldLayout.packCell(10, y, z));
            }
        }
        Optional<ScaffoldLayout.Cell> picked = ScaffoldColumnSitePolicy.pick(
                10, 64, 20,
                10, 19,
                60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z)),
                8);
        assertTrue(picked.isPresent());
        assertEquals(10, picked.get().x());
        assertEquals(23, picked.get().z());
    }

    @Test
    void emptyWhenEveryOutwardRingBlocked() {
        Set<Long> blocked = new HashSet<>();
        for (int z = 20; z <= 28; z++) {
            for (int y = 60; y <= 70; y++) {
                blocked.add(ScaffoldLayout.packCell(10, y, z));
            }
        }
        Optional<ScaffoldLayout.Cell> picked = ScaffoldColumnSitePolicy.pick(
                10, 64, 20,
                10, 19,
                60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z)),
                3);
        assertTrue(picked.isEmpty());
    }

    @Test
    void shaftClearIgnoresCellsOutsideClimbRange() {
        Set<Long> blocked = Set.of(ScaffoldLayout.packCell(10, 80, 20));
        assertTrue(ScaffoldColumnSitePolicy.shaftClear(
                10, 20, 60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z))));
    }

    @Test
    void onTargetXzDefaultsOutwardToPlusX() {
        Set<Long> blocked = new HashSet<>();
        for (int y = 60; y <= 70; y++) {
            blocked.add(ScaffoldLayout.packCell(10, y, 20));
        }
        Optional<ScaffoldLayout.Cell> picked = ScaffoldColumnSitePolicy.pick(
                10, 64, 20,
                10, 20,
                60, 70,
                (x, y, z) -> blocked.contains(ScaffoldLayout.packCell(x, y, z)),
                8);
        assertTrue(picked.isPresent());
        assertEquals(11, picked.get().x());
        assertEquals(20, picked.get().z());
    }
}
