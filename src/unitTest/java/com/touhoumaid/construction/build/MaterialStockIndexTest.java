package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialStockIndexTest {
    @Test
    void depositThenHasForMatchingBlock() {
        MaterialStockIndex index = new MaterialStockIndex();
        index.rebuildFromCounts(Map.of());
        int rev0 = index.revision();
        index.applyDeposit("minecraft:oak_planks", 16);
        assertTrue(index.revision() > rev0);
        assertTrue(index.hasForBlockId("minecraft:oak_planks", List.of("minecraft:oak_planks")));
        assertFalse(index.hasForBlockId("minecraft:stone", List.of("minecraft:stone")));
    }

    @Test
    void consumeToZeroClearsAvailability() {
        MaterialStockIndex index = new MaterialStockIndex();
        index.rebuildFromCounts(Map.of("minecraft:oak_planks", 2));
        assertTrue(index.hasForBlockId("minecraft:oak_planks", List.of("minecraft:oak_planks")));
        index.applyConsume("minecraft:oak_planks", 1);
        assertTrue(index.hasForBlockId("minecraft:oak_planks", List.of("minecraft:oak_planks")));
        index.applyConsume("minecraft:oak_planks", 1);
        assertFalse(index.hasForBlockId("minecraft:oak_planks", List.of("minecraft:oak_planks")));
    }

    @Test
    void markUnavailableOverridesPositiveStockUntilClearedByDeposit() {
        MaterialStockIndex index = new MaterialStockIndex();
        index.rebuildFromCounts(Map.of("minecraft:oak_planks", 8));
        index.markBlockUnavailable("minecraft:oak_stairs");
        assertFalse(index.hasForBlockId("minecraft:oak_stairs", List.of("minecraft:oak_planks")));
        index.applyDeposit("minecraft:oak_planks", 1);
        assertTrue(index.hasForBlockId("minecraft:oak_stairs", List.of("minecraft:oak_planks")));
    }

    @Test
    void aliasMatchIdsShareStock() {
        MaterialStockIndex index = new MaterialStockIndex();
        index.rebuildFromCounts(Map.of("minecraft:glow_berries", 1));
        assertTrue(index.hasForBlockId(
                "minecraft:cave_vines_plant",
                List.of("minecraft:glow_berries", "minecraft:cave_vines_plant")));
    }
}
