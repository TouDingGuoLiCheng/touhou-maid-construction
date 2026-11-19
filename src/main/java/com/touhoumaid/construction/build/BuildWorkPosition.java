package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Finds a standing position from which a target block can be reached and seen. */
public final class BuildWorkPosition {
    private BuildWorkPosition() {
    }

    public static boolean canInteractNow(ServerLevel level, EntityMaid maid, BlockPos target) {
        if (BuildPlacementCheck.wouldOccupyEntity(maid, target)) {
            return false;
        }
        return BuildInteractionReach.canReach(
                maid.getX(), maid.getEyeY(), maid.getZ(),
                target.getX(), target.getY(), target.getZ(), BuildInteractionReach.REACH_DISTANCE)
                && BuildLineOfSight.hasClearView(level, maid, target);
    }

    public static Optional<BlockPos> find(ServerLevel level, EntityMaid maid, BlockPos target) {
        BlockPos maidFeet = maid.blockPosition();
        if (!BuildPlacementCheck.wouldOccupyEntity(maid, target)
                && canInteractNow(level, maid, target)
                && BuildWorkStandSolidPolicy.acceptsAsWorkStand(canStandAt(level, maidFeet))) {
            return Optional.of(maidFeet);
        }

        List<BlockPos> candidates = gatherCandidates(level, maid, target);
        int checked = 0;
        for (BlockPos candidate : candidates) {
            if (!BuildWorkPositionFindPolicy.mayCheckPathReach(checked)) {
                break;
            }
            checked++;
            if (maid.canPathReach(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Nearest solid placement stand (reach + line-of-sight), without {@code canPathReach}.
     * Empty when only floating geometric cells would interact — caller should scaffold or vault-place.
     */
    public static Optional<BlockPos> findNearestGeometric(ServerLevel level, EntityMaid maid, BlockPos target) {
        if (!BuildPlacementCheck.wouldOccupyEntity(maid, target) && canInteractNow(level, maid, target)) {
            BlockPos feet = maid.blockPosition();
            if (BuildWorkStandSolidPolicy.acceptsAsWorkStand(canStandAt(level, feet))) {
                return Optional.of(feet);
            }
        }
        List<BlockPos> candidates = gatherCandidates(level, maid, target);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    /**
     * Placement stands for a target: solid footing ({@link #canStandAt}) plus reach and LOS.
     * Floating geometric air cells are never work stands.
     */
    public static List<BlockPos> gatherCandidates(ServerLevel level, EntityMaid maid, BlockPos target) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos candidate : BlockPos.withinManhattan(target, 4, 8, 4)) {
            BlockPos feet = candidate.immutable();
            if (feet.equals(target) && BuildPlacementCheck.wouldOccupyEntity(maid, target)) {
                continue;
            }
            if (!BuildWorkStandSolidPolicy.acceptsAsWorkStand(canStandAt(level, feet))) {
                continue;
            }
            if (allowsPlacementStand(level, feet) && canInteractFrom(level, maid, feet, target)) {
                candidates.add(feet);
            }
        }
        candidates.sort(Comparator
                .comparingInt((BlockPos pos) -> pos.distManhattan(target))
                .thenComparingDouble(pos -> pos.distSqr(maid.blockPosition())));
        return candidates;
    }

    /** Same as {@link #gatherCandidates} — work stands are solid-footing only. */
    public static List<BlockPos> gatherSolidStands(ServerLevel level, EntityMaid maid, BlockPos target) {
        return gatherCandidates(level, maid, target);
    }

    /** Solid interact stands closest to the target (floating geometric cells excluded). */
    public static List<BlockPos> gatherGeometricClosestToTarget(ServerLevel level, EntityMaid maid, BlockPos target) {
        return gatherCandidates(level, maid, target);
    }

    /**
     * Placement work cell: headroom and no scaffold footing. Floor must also pass
     * {@link #canStandAt} via {@link #gatherCandidates}.
     */
    static boolean allowsPlacementStand(ServerLevel level, BlockPos feet) {
        var feetState = level.getBlockState(feet);
        var below = level.getBlockState(feet.below());
        if (!ScaffoldWorkStandPolicy.allowsWorkStand(
                ConstructionScaffoldBlocks.isWalkableScaffold(feetState),
                ConstructionScaffoldBlocks.isWalkableScaffold(below))) {
            return false;
        }
        var above = level.getBlockState(feet.above());
        return above.isAir() || above.isPathfindable(level, feet.above(), PathComputationType.LAND);
    }

    static boolean canStandAt(ServerLevel level, BlockPos feet) {
        var feetState = level.getBlockState(feet);
        var above = level.getBlockState(feet.above());
        boolean feetIsScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(feetState);
        boolean belowIsScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet.below()));
        if (!ScaffoldWorkStandPolicy.allowsWorkStand(feetIsScaffold, belowIsScaffold)) {
            return false;
        }
        boolean feetOk = feetState.isPathfindable(level, feet, PathComputationType.LAND);
        boolean headOk = above.isPathfindable(level, feet.above(), PathComputationType.LAND)
                || above.isAir();
        return feetOk && headOk && hasFloorSupport(level, feet.below());
    }

    private static boolean hasFloorSupport(ServerLevel level, BlockPos below) {
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean canInteractFrom(ServerLevel level, EntityMaid maid,
                                           BlockPos feet, BlockPos target) {
        Vec3 eye = new Vec3(
                feet.getX() + 0.5D,
                feet.getY() + maid.getEyeHeight(),
                feet.getZ() + 0.5D);
        return BuildInteractionReach.canReach(
                eye.x, eye.y, eye.z,
                target.getX(), target.getY(), target.getZ(), BuildInteractionReach.REACH_DISTANCE)
                && BuildLineOfSight.hasClearView(level, maid, eye, target);
    }
}
