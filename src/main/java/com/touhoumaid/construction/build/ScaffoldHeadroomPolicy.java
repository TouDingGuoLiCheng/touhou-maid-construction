package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Standing on scaffold at {@code feetCell} needs two passable cells above (head + gap)
 * before a solid ceiling at {@code feetCell.above(3)}.
 */
public final class ScaffoldHeadroomPolicy {
    private ScaffoldHeadroomPolicy() {
    }

    /** Whether a maid may stand on scaffold placed at {@code feetCell}. */
    public static boolean hasStandHeadroom(ServerLevel level, BlockPos feetCell) {
        if (level == null || feetCell == null) {
            return false;
        }
        return isPassableForHead(level, feetCell.above())
                && isPassableForHead(level, feetCell.above(2));
    }

    private static boolean isPassableForHead(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir()
                || state.canBeReplaced()
                || state.isPathfindable(level, pos, PathComputationType.LAND);
    }
}
