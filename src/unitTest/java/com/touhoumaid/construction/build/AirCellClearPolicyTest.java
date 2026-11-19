package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AirCellClearPolicyTest {
    @Test
    void doesNotBulkClearGrassOrStoneJustBecauseTheSchematicCellIsAir() {
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:grass_block"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:stone"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:cave_air", "minecraft:stone"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:void_air", "minecraft:grass_block"));
    }

    @Test
    void leavesOtherWorldBlocksOnSchematicAir() {
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:dirt"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:cobblestone"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:deepslate"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:grass"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:air"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", "minecraft:oak_log"));
    }

    @Test
    void doesNotClearWhenSchematicCellIsNotAir() {
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:stone", "minecraft:grass_block"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:grass_block", "minecraft:stone"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:structure_void", "minecraft:stone"));
        assertFalse(AirCellClearPolicy.shouldClear(null, "minecraft:stone"));
        assertFalse(AirCellClearPolicy.shouldClear("minecraft:air", null));
    }
}
