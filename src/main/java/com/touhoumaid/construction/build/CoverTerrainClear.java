package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import com.touhoumaid.construction.placement.PlacementRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Instantly clears coverable terrain that blocks a construction maid's current task. */
public final class CoverTerrainClear {
    private CoverTerrainClear() {
    }

    public static boolean inJobBox(BuildJob job, BlockPos pos) {
        if (job == null || pos == null) {
            return false;
        }
        BlueprintPlacement.Bounds box = job.placedBounds();
        return CoverTerrainClearPolicy.inExclusiveAabb(
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean mayClear(ServerLevel level, BuildJob job, BlockPos pos) {
        if (level == null || job == null || pos == null
                || (!job.coverTerrain() && !job.ignoreOverlaps())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        boolean matchesPlanned = job.alreadyBuilt(pos);
        if (!matchesPlanned) {
            Optional<BuildTask> task = job.taskAt(pos);
            if (task.isPresent() && job.worldMatchesTask(level, task.get())) {
                matchesPlanned = true;
            }
        }
        boolean destroyable = !PlacementRules.isUnbreakable(state);
        return CoverTerrainClearPolicy.mayClear(
                job.coverTerrain(),
                job.ignoreOverlaps(),
                inJobBox(job, pos),
                ConstructionScaffoldBlocks.isWalkableScaffold(state),
                matchesPlanned,
                PlacementRules.isTerrainCoverable(state),
                destroyable);
    }

    /** @return true when a coverable terrain cell was removed */
    public static boolean tryHarvest(ServerLevel level, BuildJob job, BlockPos pos) {
        if (!mayClear(level, job, pos)) {
            return false;
        }
        return ClearToVault.harvest(level, job.materials(), pos);
    }

    /**
     * No work stand + wrong destroyable block on the claimed cell: dig that one cell into the vault
     * regardless of coverTerrain / ignoreOverlaps. Does not clear corridors.
     *
     * @return true when the cell was harvested (or already air)
     */
    public static boolean tryHarvestOccupiedTargetNoStand(
            ServerLevel level, BuildJob job, BlockPos target) {
        if (level == null || job == null || target == null || !inJobBox(job, target)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        boolean air = state.isAir();
        boolean matchesPlanned = job.alreadyBuilt(target);
        if (!matchesPlanned) {
            Optional<BuildTask> task = job.taskAt(target);
            if (task.isPresent() && job.worldMatchesTask(level, task.get())) {
                matchesPlanned = true;
            }
        }
        boolean destroyable = !PlacementRules.isUnbreakable(state);
        if (!NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, air, matchesPlanned, destroyable)) {
            return false;
        }
        return ClearToVault.harvest(level, job.materials(), target);
    }

    /**
     * Unstick the maid: vanilla pathing needs a 2-high body (feet + head).
     * Eye-clip never hits the block containing the eye or anything above it.
     */
    public static int tryClearBody(ServerLevel level, BuildJob job, EntityMaid maid) {
        if (level == null || job == null || maid == null
                || (!job.coverTerrain() && !job.ignoreOverlaps())) {
            return 0;
        }
        BlockPos feet = maid.blockPosition();
        int cleared = 0;
        if (tryHarvest(level, job, feet.above())) {
            cleared++;
        }
        if (CoverTerrainClearPolicy.remainingClears(cleared) > 0 && tryHarvest(level, job, feet)) {
            cleared++;
        }
        return cleared;
    }

    /**
     * Body first, then a 2-high corridor along eye→target / eye→work stand.
     *
     * @return number of cells harvested
     */
    public static int tryClearObstructions(
            ServerLevel level,
            BuildJob job,
            EntityMaid maid,
            BlockPos target,
            BlockPos workStand) {
        if (level == null || job == null || maid == null
                || (!job.coverTerrain() && !job.ignoreOverlaps())) {
            return 0;
        }
        int cleared = tryClearBody(level, job, maid);
        if (workStand != null && CoverTerrainClearPolicy.remainingClears(cleared) > 0) {
            cleared += clearColumn(level, job, workStand, target, cleared);
        }
        if (target != null && CoverTerrainClearPolicy.remainingClears(cleared) > 0) {
            cleared += clearAlongClip(
                    level, job, maid, maid.getEyePosition(), Vec3.atCenterOf(target), target, cleared);
        }
        if (workStand != null && CoverTerrainClearPolicy.remainingClears(cleared) > 0) {
            cleared += clearAlongClip(
                    level, job, maid, maid.getEyePosition(), Vec3.atCenterOf(workStand), target, cleared);
        }
        return cleared;
    }

    private static int clearColumn(
            ServerLevel level, BuildJob job, BlockPos feet, BlockPos skipHarvest, int alreadyCleared) {
        int cleared = 0;
        int budget = CoverTerrainClearPolicy.remainingClears(alreadyCleared);
        if (budget <= 0) {
            return 0;
        }
        BlockPos head = feet.above();
        if (!feet.equals(skipHarvest) && tryHarvest(level, job, feet)) {
            cleared++;
        }
        if (CoverTerrainClearPolicy.remainingClears(alreadyCleared + cleared) > 0
                && !head.equals(skipHarvest)
                && tryHarvest(level, job, head)) {
            cleared++;
        }
        return cleared;
    }

    private static int clearAlongClip(
            ServerLevel level,
            BuildJob job,
            EntityMaid maid,
            Vec3 from,
            Vec3 to,
            BlockPos skipHarvest,
            int alreadyCleared) {
        int budget = CoverTerrainClearPolicy.remainingClears(alreadyCleared);
        int cleared = 0;
        while (cleared < budget) {
            BlockHitResult hit = level.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    maid));
            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }
            BlockPos hitPos = hit.getBlockPos();
            if (hitPos.equals(skipHarvest)) {
                break;
            }
            if (!tryHarvest(level, job, hitPos)) {
                break;
            }
            cleared++;
            BlockPos headroom = hitPos.above();
            if (CoverTerrainClearPolicy.remainingClears(alreadyCleared + cleared) > 0
                    && !headroom.equals(skipHarvest)
                    && tryHarvest(level, job, headroom)) {
                cleared++;
            }
        }
        return cleared;
    }
}
