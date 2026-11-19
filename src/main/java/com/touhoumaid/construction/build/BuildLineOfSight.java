package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BuildLineOfSight {
    private BuildLineOfSight() {
    }

    public static boolean hasClearView(ServerLevel level, EntityMaid maid, BlockPos target) {
        return hasClearView(level, maid, maid.getEyePosition(), target);
    }

    public static boolean hasClearView(ServerLevel level, EntityMaid maid, Vec3 eye, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                maid));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    /** Line-of-sight without an entity (scaffold planning). */
    public static boolean hasClearView(ServerLevel level, Vec3 eye, BlockPos target) {
        return hasClearView(level, null, eye, target);
    }
}
