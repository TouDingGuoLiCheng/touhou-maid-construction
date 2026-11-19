package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceableStockScanPolicyTest {
    @Test
    void skipsDuplicateBlockIds() {
        assertTrue(PlaceableStockScanPolicy.shouldSkipDuplicate("minecraft:dirt", true));
        assertFalse(PlaceableStockScanPolicy.shouldSkipDuplicate("minecraft:dirt", false));
    }

    @Test
    void stockIndexAnswersPlaceableWithoutCrafting() {
        assertTrue(PlaceableStockScanPolicy.preferStockIndex(true));
        assertFalse(PlaceableStockScanPolicy.preferStockIndex(false));
    }
}
