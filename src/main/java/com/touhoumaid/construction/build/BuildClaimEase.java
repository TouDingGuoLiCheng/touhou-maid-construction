package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cheap world probe for {@link BuildClaimEasePolicy}: block-state reads only, no pathfinding
 * or line-of-sight, so every claim can afford it.
 */
public final class BuildClaimEase {
    /** Horizontal radius searched for a solid standing cell. */
    private static final int STAND_RADIUS_XZ = 2;
    /** Feet may sit this far below the target (placing upward from the floor). */
    private static final int STAND_BELOW = 2;
    /** Feet may sit this far above the target (placing downward from a built ledge). */
    private static final int STAND_ABOVE = 1;

    private BuildClaimEase() {
    }

    public static int score(ServerLevel level, BlockPos target) {
        boolean supportBelow = isAttachSolid(level.getBlockState(target.below()));
        int sides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (isAttachSolid(level.getBlockState(target.relative(dir)))) {
                sides++;
            }
        }
        if (isAttachSolid(level.getBlockState(target.above()))) {
            sides++;
        }
        return BuildClaimEasePolicy.score(hasSolidStandNear(level, target), supportBelow, sides);
    }

    /**
     * Small solid-footing neighbourhood (no reach / LOS / path). Used by claim-ease and by
     * claim-time free-stand occupancy so scans stay O(small) per cell.
     */
    public static boolean hasSolidStandNear(ServerLevel level, BlockPos target) {
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dy = -STAND_BELOW; dy <= STAND_ABOVE; dy++) {
            for (int dx = -STAND_RADIUS_XZ; dx <= STAND_RADIUS_XZ; dx++) {
                for (int dz = -STAND_RADIUS_XZ; dz <= STAND_RADIUS_XZ; dz++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == -1)) {
                        continue;
                    }
                    feet.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                    if (BuildWorkPosition.canStandAt(level, feet)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when a cheap solid stand exists that is not occupied by another maid's feet.
     */
    public static boolean hasUntakenSolidStandNear(
            ServerLevel level, BlockPos target, java.util.Set<BlockPos> takenFeet) {
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dy = -STAND_BELOW; dy <= STAND_ABOVE; dy++) {
            for (int dx = -STAND_RADIUS_XZ; dx <= STAND_RADIUS_XZ; dx++) {
                for (int dz = -STAND_RADIUS_XZ; dz <= STAND_RADIUS_XZ; dz++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == -1)) {
                        continue;
                    }
                    feet.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                    if (!BuildWorkPosition.canStandAt(level, feet)) {
                        continue;
                    }
                    if (takenFeet == null || !takenFeet.contains(feet.immutable())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isAttachSolid(BlockState state) {
        return !state.isAir() && !state.canBeReplaced();
    }
}
