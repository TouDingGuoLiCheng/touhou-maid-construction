package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldPlacementPhasePolicyTest {
    @Test
    void allowsVerticalExtensionAhead() {
        List<ScaffoldLayout.Cell> path = List.of(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 65, 0));
        Set<Long> present = Set.of(ScaffoldLayout.packCell(0, 64, 0));
        assertTrue(ScaffoldPlacementPhasePolicy.mayPlaceNowCells(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 65, 0),
                path,
                present));
    }

    @Test
    void defersHorizontalUntilMaidOnColumnTop() {
        List<ScaffoldLayout.Cell> path = List.of(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(0, 65, 0),
                new ScaffoldLayout.Cell(1, 65, 0));
        Set<Long> present = Set.of(
                ScaffoldLayout.packCell(0, 64, 0),
                ScaffoldLayout.packCell(0, 65, 0));
        assertFalse(ScaffoldPlacementPhasePolicy.mayPlaceNowCells(
                new ScaffoldLayout.Cell(0, 64, 0),
                new ScaffoldLayout.Cell(1, 65, 0),
                path,
                present));
        assertTrue(ScaffoldPlacementPhasePolicy.mayPlaceNowCells(
                new ScaffoldLayout.Cell(0, 65, 0),
                new ScaffoldLayout.Cell(1, 65, 0),
                path,
                present));
    }
}
