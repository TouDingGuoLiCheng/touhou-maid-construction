package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScaffoldClimbPolicyGreedyTest {
    @Test
    void climbsUpWhenHigherWorkAndColumnExists() {
        Set<Long> passable = new HashSet<>();
        for (int y = -60; y <= -50; y++) {
            passable.add(ScaffoldLayout.packCell(162, y, 151));
        }
        ScaffoldLayout.Cell step = ScaffoldClimbPolicy.greedyStepToward(
                new ScaffoldLayout.Cell(162, -60, 151),
                new ScaffoldLayout.Cell(163, -50, 160),
                passable);
        assertNotNull(step);
        assertEquals(new ScaffoldLayout.Cell(162, -59, 151), step);
    }

    @Test
    void defersHorizontalGreedyStepDuringVerticalOnlyPhase() {
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(162, -60, 151));
        passable.add(ScaffoldLayout.packCell(162, -60, 152));
        passable.add(ScaffoldLayout.packCell(162, -60, 153));
        assertNull(ScaffoldClimbPolicy.greedyStepToward(
                new ScaffoldLayout.Cell(162, -60, 151),
                new ScaffoldLayout.Cell(162, -60, 160),
                passable));
    }

    @Test
    void returnsNullWhenNoLocalProgress() {
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(162, -60, 151));
        assertNull(ScaffoldClimbPolicy.greedyStepToward(
                new ScaffoldLayout.Cell(162, -60, 151),
                new ScaffoldLayout.Cell(163, -50, 160),
                passable));
    }

    @Test
    void climbsWhenWorkIsHigherDuringVerticalOnlyPhase() {
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(162, -50, 155));
        passable.add(ScaffoldLayout.packCell(162, -49, 155));
        passable.add(ScaffoldLayout.packCell(162, -50, 156));
        passable.add(ScaffoldLayout.packCell(162, -50, 157));
        ScaffoldLayout.Cell step = ScaffoldClimbPolicy.greedyStepToward(
                new ScaffoldLayout.Cell(162, -50, 155),
                new ScaffoldLayout.Cell(163, -49, 159),
                passable);
        assertNotNull(step);
        assertEquals(new ScaffoldLayout.Cell(162, -49, 155), step);
    }

    @Test
    void climbsSameColumnBeforeCrossColumnHorizontalDuringVerticalOnlyPhase() {
        Set<Long> passable = new HashSet<>();
        for (int y = -60; y <= -50; y++) {
            passable.add(ScaffoldLayout.packCell(162, y, 155));
            passable.add(ScaffoldLayout.packCell(162, y, 160));
        }
        for (int z = 155; z <= 160; z++) {
            passable.add(ScaffoldLayout.packCell(162, -60, z));
        }
        ScaffoldLayout.Cell step = ScaffoldClimbPolicy.greedyStepToward(
                new ScaffoldLayout.Cell(162, -60, 155),
                new ScaffoldLayout.Cell(163, -50, 160),
                passable);
        assertNotNull(step);
        assertEquals(new ScaffoldLayout.Cell(162, -59, 155), step);
    }
}
