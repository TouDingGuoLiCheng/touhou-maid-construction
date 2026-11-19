package com.touhoumaid.construction.build;

import com.touhoumaid.construction.placement.BlockStateResolve;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Pre-place checks: entity occupancy and block survival rules. */
public final class BuildPlacementCheck {
    private BuildPlacementCheck() {
    }

    public static boolean wouldOccupyEntity(Entity entity, BlockPos target) {
        AABB box = entity.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (pos.equals(target)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canSurvive(ServerLevel level, BuildTask task, int yaw) {
        BlockState state = BlockStateResolve.resolveRotated(task.raw(), yaw);
        if (state.isAir()) {
            return false;
        }
        return state.canSurvive(level, task.worldPos());
    }

    /** Heuristic: torches, lanterns, buttons, etc. should run after solids on the same layer. */
    public static int attachPriority(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return 0;
        }
        String lower = blockId.toLowerCase();
        if (lower.contains("lantern")
                || lower.contains("torch")
                || lower.contains("button")
                || lower.contains("lever")
                || lower.contains("pressure_plate")
                || lower.contains("sign")
                || lower.contains("banner")
                || lower.contains("carpet")
                || lower.contains("snow_layer")
                || lower.contains("rail")
                || lower.contains("tripwire")
                || lower.contains("chain")) {
            return 1;
        }
        return 0;
    }
}
