package com.touhoumaid.construction.build;

import com.touhoumaid.construction.blueprint.RawBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Compares schematic cells to live world blocks for progress resync. */
public final class BuildWorldMatch {
    private BuildWorldMatch() {
    }

    /**
     * True when the world cell already satisfies the schematic entry.
     * Same block type is enough (facing/shape/waterlogged ignored).
     * Dirt and grass block count as already satisfied for each other.
     */
    public static boolean matchesPlanned(RawBlockState raw, BlockState expected, BlockState actual) {
        if (raw == null || expected.isAir()) {
            return false;
        }
        if (actual.equals(expected)) {
            return true;
        }
        String expectedId = BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString();
        String actualId = BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString();
        return BuildCellSatisfiedPolicy.alreadySatisfied(expectedId, actualId);
    }
}
