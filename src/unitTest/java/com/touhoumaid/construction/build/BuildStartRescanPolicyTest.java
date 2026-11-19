package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildStartRescanPolicyTest {
    @Test
    void repairRescanDoesNotSkipPendingFromSavedCounts() {
        assertFalse(BuildStartRescanPolicy.allowSkipPendingFromSavedCounts(true));
    }

    @Test
    void softUiRestoreMayStillSkipFromSavedCounts() {
        assertTrue(BuildStartRescanPolicy.allowSkipPendingFromSavedCounts(false));
    }

    @Test
    void projectProgressUsesWorldScanNotMaxWithSaved() {
        Map<String, Integer> saved = Map.of("minecraft:stone", 150);
        Map<String, Integer> scanned = Map.of("minecraft:stone", 40);
        Map<String, Integer> forProject =
                BuildStartRescanPolicy.projectPlacedByBlockIdAfterRescan(saved, scanned);
        assertEquals(40, forProject.get("minecraft:stone"));
    }

    @Test
    void consumedMaterialsShrinksWhenMatchedPlacedDrops() {
        assertEquals(45, BuildStartRescanPolicy.adjustConsumedAfterRescan(50, 50, 45));
        assertEquals(50, BuildStartRescanPolicy.adjustConsumedAfterRescan(50, 40, 45));
        assertEquals(0, BuildStartRescanPolicy.adjustConsumedAfterRescan(3, 10, 5));
    }
}
