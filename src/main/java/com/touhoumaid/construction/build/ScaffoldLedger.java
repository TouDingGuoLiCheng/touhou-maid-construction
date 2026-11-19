package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Tracks scaffolding blocks placed by this job for end-of-job reclaim. */
public final class ScaffoldLedger {
    public static final String BLOCK_ID = "minecraft:scaffolding";

    private final Set<Long> placed = new LinkedHashSet<>();

    public void record(BlockPos pos) {
        if (pos != null) {
            placed.add(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public boolean contains(BlockPos pos) {
        return pos != null && placed.contains(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
    }

    public void remove(BlockPos pos) {
        if (pos != null) {
            placed.remove(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public Set<Long> packedPositions() {
        return Collections.unmodifiableSet(placed);
    }

    public int size() {
        return placed.size();
    }

    public void clear() {
        placed.clear();
    }

    public static boolean isScaffoldingBlockId(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    public static net.minecraft.world.level.block.Block scaffoldingBlock() {
        return Blocks.SCAFFOLDING;
    }
}
