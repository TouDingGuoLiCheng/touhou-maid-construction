package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStarvePolicyTest {
    @Test
    void skipWhileStarvingSameRevision() {
        assertTrue(ClaimStarvePolicy.shouldSkip(100, 110, 5, 5));
        assertFalse(ClaimStarvePolicy.shouldSkip(110, 110, 5, 5));
        assertFalse(ClaimStarvePolicy.shouldSkip(100, 110, 6, 5));
    }

    @Test
    void starveUntilAddsWindow() {
        assertEquals(110, ClaimStarvePolicy.starveUntilTick(100));
    }

    @Test
    void onlyStarveWhenMaterialWasTheBlocker() {
        assertTrue(ClaimStarvePolicy.shouldEnterStarve(12, 0));
        assertFalse(ClaimStarvePolicy.shouldEnterStarve(0, 0));
        assertFalse(ClaimStarvePolicy.shouldEnterStarve(5, 3));
    }

    @Test
    void starveWhenEntireStockedSetFailedSurvive() {
        assertTrue(ClaimStarvePolicy.shouldEnterSurviveStarve(16, 0, 16));
        assertFalse(ClaimStarvePolicy.shouldEnterSurviveStarve(48, 0, 2000));
        assertFalse(ClaimStarvePolicy.shouldEnterSurviveStarve(16, 2, 16));
        assertFalse(ClaimStarvePolicy.shouldEnterSurviveStarve(0, 0, 10));
    }
}
