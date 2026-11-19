package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldBoundsPolicyTest {
    private static final ScaffoldBoundsPolicy.Box BOX =
            new ScaffoldBoundsPolicy.Box(0, 60, 0, 10, 70, 10);

    @Test
    void insideTheFootprintIsAlwaysAllowed() {
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, ScaffoldBoundsPolicy.MARGIN, 5, 65, 5));
    }

    @Test
    void marginOfTwoIsInclusiveOnEveryAxis() {
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, -2, 65, 5));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, 12, 65, 5));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 58, 5));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 72, 5));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 65, -2));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 65, 12));
    }

    @Test
    void threeBlocksOutIsRejectedOnEveryAxis() {
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, -3, 65, 5));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, 13, 65, 5));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 57, 5));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 73, 5));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 65, -3));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, 2, 5, 65, 13));
    }

    @Test
    void relaxedMarginAcceptsWhatTheDefaultRejects() {
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, ScaffoldBoundsPolicy.MARGIN, 14, 65, 5));
        assertTrue(ScaffoldBoundsPolicy.contains(BOX, ScaffoldBoundsPolicy.RELAXED_MARGIN, 14, 65, 5));
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, ScaffoldBoundsPolicy.RELAXED_MARGIN, 15, 65, 5));
    }

    @Test
    void allInsideRejectsWhenAnySingleCellEscapes() {
        List<ScaffoldLayout.Cell> ok = List.of(
                new ScaffoldLayout.Cell(11, 61, 5),
                new ScaffoldLayout.Cell(11, 62, 5));
        List<ScaffoldLayout.Cell> escaping = List.of(
                new ScaffoldLayout.Cell(11, 61, 5),
                new ScaffoldLayout.Cell(13, 62, 5));
        assertTrue(ScaffoldBoundsPolicy.allInside(BOX, 2, ok));
        assertFalse(ScaffoldBoundsPolicy.allInside(BOX, 2, escaping));
    }

    @Test
    void distanceOutsideReportsHowFarTheMaidStillHasToWalk() {
        assertEquals(0, ScaffoldBoundsPolicy.distanceOutside(BOX, 2, 5, 65, 5));
        assertEquals(0, ScaffoldBoundsPolicy.distanceOutside(BOX, 2, 12, 65, 5));
        assertEquals(8, ScaffoldBoundsPolicy.distanceOutside(BOX, 2, 20, 65, 5));
        assertEquals(8, ScaffoldBoundsPolicy.distanceOutside(BOX, 2, 20, 65, -6));
    }

    @Test
    void aNullBoxNeverBlocksAnything() {
        assertTrue(ScaffoldBoundsPolicy.contains(null, 2, 9999, 9999, 9999));
        assertEquals(0, ScaffoldBoundsPolicy.distanceOutside(null, 2, 9999, 9999, 9999));
    }
}
