package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRadarPolicyTest {
    @Test
    void materialBudgetIs48() {
        assertEquals(48, ClaimRadarPolicy.MAX_MATERIAL_CHECKS_PER_CLAIM);
    }

    @Test
    void nextWindowSlicesFromCursor() {
        ClaimRadarPolicy.Window w = ClaimRadarPolicy.nextWindow(0, 1300, 48);
        assertEquals(0, w.fromInclusive());
        assertEquals(48, w.toExclusive());
        assertEquals(48, w.nextCursor());
        assertFalse(w.passExhausted());
    }

    @Test
    void nextWindowResumesAndMarksPassExhaustedAtEnd() {
        ClaimRadarPolicy.Window mid = ClaimRadarPolicy.nextWindow(48, 100, 48);
        assertEquals(48, mid.fromInclusive());
        assertEquals(96, mid.toExclusive());
        assertEquals(96, mid.nextCursor());
        assertFalse(mid.passExhausted());

        ClaimRadarPolicy.Window end = ClaimRadarPolicy.nextWindow(96, 100, 48);
        assertEquals(96, end.fromInclusive());
        assertEquals(100, end.toExclusive());
        assertEquals(0, end.nextCursor());
        assertTrue(end.passExhausted());
    }

    @Test
    void nextWindowOnEmptyListIsExhausted() {
        ClaimRadarPolicy.Window w = ClaimRadarPolicy.nextWindow(0, 0, 48);
        assertEquals(0, w.fromInclusive());
        assertEquals(0, w.toExclusive());
        assertEquals(0, w.nextCursor());
        assertTrue(w.passExhausted());
    }

    @Test
    void starveOnlyAfterFullPassWithRejectsAndNoAccepts() {
        assertFalse(ClaimRadarPolicy.shouldStarveAfterPass(false, 40, 0));
        assertFalse(ClaimRadarPolicy.shouldStarveAfterPass(true, 40, 2));
        assertFalse(ClaimRadarPolicy.shouldStarveAfterPass(true, 0, 0));
        assertTrue(ClaimRadarPolicy.shouldStarveAfterPass(true, 40, 0));
    }

    @Test
    void shouldRebuildWhenFingerprintChangesOrCursorAtStartWithNewCenter() {
        assertTrue(ClaimRadarPolicy.shouldRebuildOrdered(
                false, 5, 6, 100, 100, 1, 1, 10L, 10L));
        assertTrue(ClaimRadarPolicy.shouldRebuildOrdered(
                false, 5, 5, 100, 101, 1, 1, 10L, 10L));
        assertTrue(ClaimRadarPolicy.shouldRebuildOrdered(
                false, 5, 5, 100, 100, 1, 2, 10L, 10L));
        // Mid-pass: ignore center drift so cursor can resume.
        assertFalse(ClaimRadarPolicy.shouldRebuildOrdered(
                false, 5, 5, 100, 100, 1, 1, 10L, 99L));
        // New pass (cursor at start): adopt new center.
        assertTrue(ClaimRadarPolicy.shouldRebuildOrdered(
                true, 5, 5, 100, 100, 1, 1, 10L, 99L));
        assertFalse(ClaimRadarPolicy.shouldRebuildOrdered(
                true, 5, 5, 100, 100, 1, 1, 10L, 10L));
    }
}
