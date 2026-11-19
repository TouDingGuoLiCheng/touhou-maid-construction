package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathComputationType;

import java.util.Comparator;

/**
 * Rescue teleport only: absolutely far from the site AABB, or stuck on the same block too long.
 * Never teleport on job start, and never pull the maid back to a stay-put center.
 */
public final class ConstructionSiteArrive {
    private static final TicketType<ChunkPos> ARRIVE_TICKET =
            TicketType.create("touhou_maid_construction_arrive", Comparator.comparingLong(ChunkPos::toLong), 40);

    private ConstructionSiteArrive() {
    }

    public static void teleportIfAbsolutelyFar(EntityMaid maid, ConstructionMaidFollow.StayArea area) {
        if (area == null || !(maid.level() instanceof ServerLevel)) {
            return;
        }
        double dist = ConstructionSiteArrivePolicy.shortestXzDistanceToAabb(
                maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ());
        if (!ConstructionSiteArrivePolicy.shouldRescueTeleport(dist)) {
            return;
        }
        teleportToStand(maid, area, null, "rescue", dist, 0);
    }

    public static void teleportIfStuck(
            EntityMaid maid, ConstructionMaidFollow.StayArea area, BlockPos preferStand, int sameBlockTicks) {
        teleportIfStuck(maid, area, preferStand, sameBlockTicks, ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS);
    }

    public static boolean teleportIfStuck(
            EntityMaid maid,
            ConstructionMaidFollow.StayArea area,
            BlockPos preferStand,
            int sameBlockTicks,
            int ticksSinceLastTeleport) {
        if (area == null || !(maid.level() instanceof ServerLevel)) {
            return false;
        }
        if (!ConstructionSiteArrivePolicy.shouldStuckTeleport(sameBlockTicks, ticksSinceLastTeleport)) {
            return false;
        }
        double dist = ConstructionSiteArrivePolicy.shortestXzDistanceToAabb(
                maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ());
        return teleportToStand(maid, area, preferStand, "stuck", dist, sameBlockTicks);
    }

    /**
     * She cannot scaffold outside the build range, so terrain that blocks the walk-in would
     * otherwise leave her standing there forever. Drop her just inside the range instead.
     */
    public static boolean teleportIntoBuildRange(
            EntityMaid maid,
            ConstructionMaidFollow.StayArea area,
            ScaffoldBoundsPolicy.Box box,
            BlockPos preferStand,
            int sameBlockTicks,
            int ticksSinceLastTeleport) {
        if (area == null || box == null || !(maid.level() instanceof ServerLevel)) {
            return false;
        }
        BlockPos feet = maid.blockPosition();
        boolean outside = !ScaffoldBoundsPolicy.contains(
                box, ScaffoldBoundsPolicy.MARGIN, feet.getX(), feet.getY(), feet.getZ());
        if (!ConstructionSiteArrivePolicy.shouldRescueOutOfRange(
                outside, sameBlockTicks, ticksSinceLastTeleport)) {
            return false;
        }
        boolean standInRange = preferStand != null && ScaffoldBoundsPolicy.contains(
                box, ScaffoldBoundsPolicy.MARGIN,
                preferStand.getX(), preferStand.getY(), preferStand.getZ());
        BlockPos hint = standInRange ? preferStand : clampIntoBox(box, feet);
        double dist = ConstructionSiteArrivePolicy.shortestXzDistanceToAabb(
                maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ());
        return teleportToStand(maid, area, hint, "out-of-range", dist, sameBlockTicks);
    }

    private static BlockPos clampIntoBox(ScaffoldBoundsPolicy.Box box, BlockPos from) {
        int margin = ScaffoldBoundsPolicy.MARGIN;
        return new BlockPos(
                clampInt(from.getX(), box.minX() - margin, box.maxX() + margin),
                clampInt(from.getY(), box.minY() - margin, box.maxY() + margin),
                clampInt(from.getZ(), box.minZ() - margin, box.maxZ() + margin));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Escape in-wall suffocation: nearest standable cell around the maid (including below).
     *
     * @return true when a stand was found and the maid was teleported
     */
    public static boolean teleportFromSuffocation(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos from = maid.blockPosition();
        BlockPos dest = findNearestSolidStand(level, from, maid);
        if (dest == null || ConstructionSiteArrivePolicy.isNoOpTeleport(
                from.getX(), from.getY(), from.getZ(), dest.getX(), dest.getY(), dest.getZ())) {
            return false;
        }
        applyRescueTeleport(maid, from, dest, "suffocation");
        ScaffoldDebugLog.teleport(maid, "suffocation", from, dest, 0, 0);
        return true;
    }

    private static void applyRescueTeleport(EntityMaid maid, BlockPos from, BlockPos dest, String reason) {
        ServerLevel level = (ServerLevel) maid.level();
        ChunkPos chunk = new ChunkPos(dest);
        level.getChunkSource().addRegionTicket(ARRIVE_TICKET, chunk, 2, chunk);
        level.getChunk(chunk.x, chunk.z);
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        float fallBefore = maid.fallDistance;
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
        maid.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        ScaffoldFallDebugLog.teleportClear(maid, reason, fallBefore, from, dest);
    }

    private static BlockPos findNearestSolidStand(ServerLevel level, BlockPos center, EntityMaid maid) {
        BlockPos avoid = maid.blockPosition();
        int salt = maid.getUUID().hashCode();
        int ox = (salt % 5) - 2;
        int oz = ((salt / 5) % 5) - 2;
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radius = 6;
        int vertical = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -vertical; dy <= vertical; dy++) {
                    cursor.set(center.getX() + ox + dx, center.getY() + dy, center.getZ() + oz + dz);
                    if (!canStand(level, cursor)) {
                        continue;
                    }
                    BlockPos stand = cursor.immutable();
                    if (stand.equals(avoid)) {
                        continue;
                    }
                    int dist = stand.distManhattan(avoid);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = stand;
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        BlockPos surface = new BlockPos(center.getX(), surfaceY, center.getZ());
        return canStand(level, surface) && !surface.equals(avoid) ? surface : null;
    }

    private static boolean teleportToStand(
            EntityMaid maid,
            ConstructionMaidFollow.StayArea area,
            BlockPos preferStand,
            String reason,
            double siteDist,
            int sameBlockTicks) {
        ServerLevel level = (ServerLevel) maid.level();
        boolean distanceRescue = "rescue".equals(reason);
        int hintY;
        BlockPos hint;
        if (distanceRescue) {
            hintY = ConstructionSiteArrivePolicy.distanceRescueHintY(
                    maid.getBlockY(),
                    preferStand != null ? preferStand.getY() : area.center().getY(),
                    area.center().getY());
            double[] xz = ConstructionSiteArrivePolicy.nearestXzOnAabb(
                    maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ());
            hint = new BlockPos((int) Math.floor(xz[0]), hintY, (int) Math.floor(xz[1]));
        } else {
            hintY = preferStand != null
                    ? preferStand.getY()
                    : maid.getBlockY();
            hint = preferStand != null
                    ? preferStand
                    : new BlockPos(
                            (int) Math.floor(ConstructionSiteArrivePolicy.nearestXzOnAabb(
                                    maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ())[0]),
                            hintY,
                            (int) Math.floor(ConstructionSiteArrivePolicy.nearestXzOnAabb(
                                    maid.getX(), maid.getZ(), area.minX(), area.maxX(), area.minZ(), area.maxZ())[1]));
        }
        BlockPos from = maid.blockPosition();
        BlockPos dest = findStandNear(level, hint, maid, from);
        if ("stuck".equals(reason)
                && ConstructionSiteArrivePolicy.shouldRejectAdjacentStuckHop(
                        siteDist,
                        preferStand != null,
                        Math.abs(from.getX() - dest.getX()) + Math.abs(from.getZ() - dest.getZ()))) {
            return false;
        }
        if (ConstructionSiteArrivePolicy.needsVerticalSafety(reason)
                && ConstructionSiteArrivePolicy.isUnsafeShaftTeleport(
                        from.getX(), from.getY(), from.getZ(), dest.getX(), dest.getY(), dest.getZ())) {
            return false;
        }
        if (ConstructionSiteArrivePolicy.isNoOpTeleport(
                from.getX(), from.getY(), from.getZ(), dest.getX(), dest.getY(), dest.getZ())) {
            return false;
        }
        applyRescueTeleport(maid, from, dest, reason);
        ScaffoldDebugLog.teleport(maid, reason, from, dest, siteDist, sameBlockTicks);
        return true;
    }

    private static BlockPos findStandNear(ServerLevel level, BlockPos center, EntityMaid maid, BlockPos avoid) {
        int salt = maid.getUUID().hashCode();
        int ox = (salt % 5) - 2;
        int oz = ((salt / 5) % 5) - 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos fallback = null;
        for (int dy = 0; dy <= 3; dy++) {
            for (int r = 0; r <= 4; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r && r != 0) {
                            continue;
                        }
                        cursor.set(center.getX() + ox + dx, center.getY() + dy, center.getZ() + oz + dz);
                        if (!canStand(level, cursor)) {
                            continue;
                        }
                        BlockPos stand = cursor.immutable();
                        if (avoid != null && stand.equals(avoid)) {
                            if (fallback == null) {
                                fallback = stand;
                            }
                            continue;
                        }
                        return stand;
                    }
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        int fromY = avoid != null ? avoid.getY() : center.getY();
        int y = ConstructionSiteArrivePolicy.fallbackStandY(
                center.getY(),
                surfaceY,
                fromY,
                ConstructionSiteArrivePolicy.TELEPORT_MAX_VERTICAL_DELTA);
        return new BlockPos(center.getX(), y, center.getZ());
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isPathfindable(level, feet, PathComputationType.LAND)
                && level.getBlockState(feet.above()).isPathfindable(
                        level, feet.above(), PathComputationType.LAND)
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }
}
