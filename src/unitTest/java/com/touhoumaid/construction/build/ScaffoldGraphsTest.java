package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldGraphsTest {
    @Test
    void standCellsAboveScaffoldBlocksConnectHorizontalArms() {
        Set<Long> passable = new HashSet<>();
        for (int z = 150; z <= 160; z++) {
            addScaffoldWithStand(passable, 162, -61, z);
        }
        passable.add(ScaffoldLayout.packCell(162, -60, 151));

        List<ScaffoldLayout.Cell> route = ScaffoldPathfinder.findPath(
                new ScaffoldLayout.Cell(162, -60, 151),
                new ScaffoldLayout.Cell(162, -60, 160),
                passable,
                4096);
        assertTrue(route.size() >= 2, "expected a horizontal route along scaffold stands");
        assertEquals(new ScaffoldLayout.Cell(162, -60, 151), route.get(0));
        assertEquals(new ScaffoldLayout.Cell(162, -60, 160), route.get(route.size() - 1));
    }

    private static void addScaffoldWithStand(Set<Long> cells, int x, int y, int z) {
        cells.add(ScaffoldLayout.packCell(x, y, z));
        cells.add(ScaffoldLayout.packCell(x, y + 1, z));
    }
}
