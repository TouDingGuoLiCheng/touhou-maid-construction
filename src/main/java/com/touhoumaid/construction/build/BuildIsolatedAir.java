package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World probe for {@link BuildIsolatedAirPlacePolicy}: four sides + above air / replaceable;
 * below is not checked.
 */
public final class BuildIsolatedAir {
    private BuildIsolatedAir() {
    }

    public static boolean sidesAndAboveAirOrReplaceable(ServerLevel level, BlockPos target) {
        if (level == null || target == null) {
            return false;
        }
        boolean[] faces = new boolean[BuildIsolatedAirPlacePolicy.FACE_COUNT];
        int i = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) {
                continue;
            }
            faces[i++] = isAirOrReplaceable(level.getBlockState(target.relative(dir)));
        }
        return BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(faces);
    }

    private static boolean isAirOrReplaceable(BlockState state) {
        return state == null || state.isAir() || state.canBeReplaced();
    }
}
