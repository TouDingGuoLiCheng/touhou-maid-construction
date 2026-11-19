package com.touhoumaid.construction.build;

import com.touhoumaid.construction.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Distinguish maid staging towers from scaffolding that belongs in the blueprint. */
public final class ConstructionScaffoldBlocks {
    private ConstructionScaffoldBlocks() {
    }

    public static Block maidBlock() {
        return ModBlocks.CONSTRUCTION_SCAFFOLD.get();
    }

    /** Walk / climb / attach — vanilla blueprint scaffolding or maid staging. */
    public static boolean isWalkableScaffold(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        return state.is(Blocks.SCAFFOLDING) || state.is(maidBlock());
    }

    /** Only maid-placed staging; safe to break on job finish. */
    public static boolean isMaidPlacedScaffold(BlockState state) {
        return state != null && state.is(maidBlock());
    }
}
