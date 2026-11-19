package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceableScanCachePolicyTest {
    @Test
    void hitsWhenKeyUnchanged() {
        assertTrue(PlaceableScanCachePolicy.isHit(10, 10, true));
        assertFalse(PlaceableScanCachePolicy.isHit(10, 11, true));
        assertFalse(PlaceableScanCachePolicy.isHit(Integer.MIN_VALUE, 0, false));
    }

    @Test
    void packsStableKeyFromStockAndQueueShape() {
        int a = PlaceableScanCachePolicy.cacheKey(3, 0, 1000, 5, 2);
        int b = PlaceableScanCachePolicy.cacheKey(3, 0, 1000, 5, 2);
        int c = PlaceableScanCachePolicy.cacheKey(4, 0, 1000, 5, 2);
        assertEquals(a, b);
        assertTrue(a != c);
    }
}
