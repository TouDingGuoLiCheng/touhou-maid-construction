package com.touhoumaid.construction.build;

import com.touhoumaid.construction.project.MaterialBlockPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicDemandIndexTest {
    @Test
    void countsEachBlockOnceThenLooksUp() {
        SchematicDemandIndex index = new SchematicDemandIndex();
        index.rebuild(List.of(
                "minecraft:oak_planks",
                "minecraft:oak_planks",
                "minecraft:stone",
                "minecraft:oak_planks"), id -> false);

        assertEquals(3, index.demandOf("minecraft:oak_planks"));
        assertEquals(1, index.demandOf("minecraft:stone"));
        assertEquals(0, index.demandOf("minecraft:glass"));
        assertEquals(3, index.demandOf("minecraft:oak_planks"));
    }

    @Test
    void excludedBlocksDoNotCountTowardDemand() {
        SchematicDemandIndex index = new SchematicDemandIndex();
        index.rebuild(List.of(
                "minecraft:glow_lichen",
                "minecraft:oak_planks",
                "minecraft:oak_planks"), MaterialBlockPolicy::isAutoIgnored);

        assertEquals(0, index.demandOf("minecraft:glow_lichen"));
        assertEquals(2, index.demandOf("minecraft:oak_planks"));
    }

    @Test
    void rebuildReplacesPreviousTotals() {
        SchematicDemandIndex index = new SchematicDemandIndex();
        index.rebuild(List.of("minecraft:oak_planks", "minecraft:oak_planks", "minecraft:stone"), id -> false);
        index.rebuild(List.of("minecraft:oak_planks", "minecraft:stone"), id -> "minecraft:oak_planks".equals(id));

        assertEquals(0, index.demandOf("minecraft:oak_planks"));
        assertEquals(1, index.demandOf("minecraft:stone"));
    }

    @Test
    void finishedWhenPlacedReachesDemand() {
        SchematicDemandIndex index = new SchematicDemandIndex();
        index.rebuild(List.of("minecraft:oak_planks", "minecraft:oak_planks", "minecraft:stone"), id -> false);

        int oakDemand = index.demandOf("minecraft:oak_planks");
        assertTrue(BuildMaterialNeedPolicy.shouldSkipPendingTask(oakDemand, 2));
        assertFalse(BuildMaterialNeedPolicy.shouldSkipPendingTask(index.demandOf("minecraft:stone"), 0));
        assertEquals(0, BuildMaterialNeedPolicy.remainingNeed(oakDemand, 2));
        assertEquals(1, BuildMaterialNeedPolicy.remainingNeed(index.demandOf("minecraft:stone"), 0));
    }
}
