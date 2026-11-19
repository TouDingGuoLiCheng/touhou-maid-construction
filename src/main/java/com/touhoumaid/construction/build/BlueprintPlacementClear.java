package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Clears world blocks that do not match the schematic cell before placement. */
public final class BlueprintPlacementClear {
    private BlueprintPlacementClear() {
    }

    public static boolean matchesPlanned(BlockState existing, BlockState planned) {
        if (existing.equals(planned)) {
            return true;
        }
        String existingId = BuiltInRegistries.BLOCK.getKey(existing.getBlock()).toString();
        String plannedId = BuiltInRegistries.BLOCK.getKey(planned.getBlock()).toString();
        return BuildCellSatisfiedPolicy.alreadySatisfied(plannedId, existingId);
    }

    /**
     * When the cell is not already the planned block, remove the obstructing block
     * into the construction vault (leftovers drop beside the cell when full).
     *
     * @return false only when a non-matching solid block could not be removed
     */
    public static boolean clearMismatch(
            ServerLevel level, MaterialSource materials, BlockPos pos, BlockState planned) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir() || matchesPlanned(existing, planned)) {
            return true;
        }
        String existingId = BuiltInRegistries.BLOCK.getKey(existing.getBlock()).toString();
        if (FluidEndPlacePolicy.replaceOccupiedFluid(existingId)) {
            return true;
        }
        return ClearToVault.harvest(level, materials, pos);
    }
}
