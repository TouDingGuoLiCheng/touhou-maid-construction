package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldRouteCostPolicyNearBuildingTest {
    @Test
    void columnCloserToTargetCostsLess() {
        List<ScaffoldLayout.Cell> near = List.of(new ScaffoldLayout.Cell(11, 70, 20));
        List<ScaffoldLayout.Cell> far = List.of(new ScaffoldLayout.Cell(30, 70, 20));
        long nearCost = ScaffoldRouteCostPolicy.costCells(
                near, near, Set.of(), 10, 20);
        long farCost = ScaffoldRouteCostPolicy.costCells(
                far, far, Set.of(), 10, 20);
        assertTrue(nearCost < farCost);
    }
}
