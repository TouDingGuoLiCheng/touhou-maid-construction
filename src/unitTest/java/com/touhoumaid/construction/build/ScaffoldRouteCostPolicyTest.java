package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldRouteCostPolicyTest {
    @Test
    void fewerNewBlocksCostsLess() {
        List<ScaffoldLayout.Cell> shortPlace = List.of(new ScaffoldLayout.Cell(0, 65, 0));
        List<ScaffoldLayout.Cell> longPlace = List.of(
                new ScaffoldLayout.Cell(0, 65, 0),
                new ScaffoldLayout.Cell(0, 66, 0),
                new ScaffoldLayout.Cell(0, 67, 0));
        long shortCost = ScaffoldRouteCostPolicy.costCells(shortPlace, shortPlace, Set.of());
        long longCost = ScaffoldRouteCostPolicy.costCells(longPlace, longPlace, Set.of());
        assertTrue(shortCost < longCost);
    }

    @Test
    void reusingExistingColumnCostsLessThanNewColumn() {
        List<ScaffoldLayout.Cell> extend = List.of(new ScaffoldLayout.Cell(0, 66, 0));
        List<ScaffoldLayout.Cell> fresh = List.of(new ScaffoldLayout.Cell(5, 66, 5));
        long extendCost = ScaffoldRouteCostPolicy.costCells(
                extend, extend, Set.of(ScaffoldLayout.packXz(0, 0)));
        long freshCost = ScaffoldRouteCostPolicy.costCells(fresh, fresh, Set.of(ScaffoldLayout.packXz(0, 0)));
        assertTrue(extendCost < freshCost);
    }
}
