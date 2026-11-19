package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scaffold column height and valid stand/step checks (world blocks, not passable-graph air). */
public final class ScaffoldColumnSupport {
    private static final int SCAN_UP = 32;
    private static final int SCAN_DOWN = 64;

    private ScaffoldColumnSupport() {
    }

    /** Highest scaffolding block at {@code x,z} near {@code hintY}. */
    public static int topScaffoldBlockY(ServerLevel level, int x, int z, int hintY) {
        int top = Integer.MIN_VALUE;
        int y0 = hintY;
        for (int y = y0 - SCAN_DOWN; y <= y0 + SCAN_UP; y++) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.SCAFFOLDING)) {
                top = Math.max(top, y);
            }
        }
        return top;
    }

    /** Highest Y the maid may stand on this column: inside/on top of the highest scaffold block. */
    public static int maxStandY(ServerLevel level, int x, int z, int hintY) {
        int top = topScaffoldBlockY(level, x, z, hintY);
        if (top == Integer.MIN_VALUE) {
            return hintY;
        }
        return top + 1;
    }

    /**
     * Merge job column metadata with a live world scan — registry wins when it knows a higher tower.
     */
    public static int maxStandY(
            ServerLevel level, ScaffoldColumnRegistry registry, int x, int z, int hintY) {
        int world = maxStandY(level, x, z, hintY);
        if (registry == null) {
            return world;
        }
        int recorded = registry.maxStandY(x, z);
        if (recorded == Integer.MIN_VALUE) {
            return world;
        }
        return Math.max(world, recorded);
    }

    public static boolean canOccupyStand(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
            return true;
        }
        return level.getBlockState(pos.below()).is(Blocks.SCAFFOLDING);
    }

    /** Step target must be a scaffold cell or stand directly on scaffold — not a chain of air. */
    public static boolean canStepUpTo(ServerLevel level, BlockPos feet, BlockPos up) {
        if (!canOccupyStand(level, up)) {
            return false;
        }
        if (level.getBlockState(up).is(Blocks.SCAFFOLDING)) {
            return true;
        }
        return level.getBlockState(up.below()).is(Blocks.SCAFFOLDING);
    }

    /** Clamp a proposed stand to the highest valid cell on its scaffold column. */
    public static BlockPos clampStandToColumn(ServerLevel level, BlockPos stand) {
        return clampStandToColumn(level, null, stand);
    }

    public static BlockPos clampStandToColumn(
            ServerLevel level, ScaffoldColumnRegistry registry, BlockPos stand) {
        // Work stands must stay on solid footing — do not snap them onto scaffold columns.
        if (!ScaffoldWorkStandPolicy.allowsScaffoldColumnWorkStand()) {
            return stand;
        }
        int maxY = maxStandY(level, registry, stand.getX(), stand.getZ(), stand.getY());
        if (stand.getY() <= maxY) {
            return stand;
        }
        BlockPos onTop = new BlockPos(stand.getX(), maxY, stand.getZ());
        if (canOccupyStand(level, onTop)) {
            return onTop;
        }
        int top = topScaffoldBlockY(level, stand.getX(), stand.getZ(), stand.getY());
        if (top != Integer.MIN_VALUE) {
            BlockPos inside = new BlockPos(stand.getX(), top, stand.getZ());
            if (canOccupyStand(level, inside)) {
                return inside;
            }
        }
        return stand;
    }

    /** Nearest scaffold column within {@code xzRadius} of {@code feet}. */
    public static int topScaffoldBlockYNear(ServerLevel level, BlockPos feet, int xzRadius) {
        int top = Integer.MIN_VALUE;
        for (int dx = -xzRadius; dx <= xzRadius; dx++) {
            for (int dz = -xzRadius; dz <= xzRadius; dz++) {
                top = Math.max(top, topScaffoldBlockY(level, feet.getX() + dx, feet.getZ() + dz, feet.getY()));
            }
        }
        return top;
    }

    public static int maxStandYNear(ServerLevel level, BlockPos feet, int xzRadius) {
        return maxStandYNear(level, null, feet, xzRadius);
    }

    public static int maxStandYNear(
            ServerLevel level, ScaffoldColumnRegistry registry, BlockPos feet, int xzRadius) {
        int best = Integer.MIN_VALUE;
        for (int dx = -xzRadius; dx <= xzRadius; dx++) {
            for (int dz = -xzRadius; dz <= xzRadius; dz++) {
                best = Math.max(
                        best,
                        maxStandY(
                                level,
                                registry,
                                feet.getX() + dx,
                                feet.getZ() + dz,
                                feet.getY()));
            }
        }
        if (best == Integer.MIN_VALUE) {
            return feet.getY();
        }
        return best;
    }

    public static boolean isFloatingAboveColumn(ServerLevel level, BlockPos feet) {
        int top = topScaffoldBlockYNear(level, feet, 1);
        return ScaffoldFloatingPolicy.isFloating(
                canOccupyStand(level, feet),
                hasSolidFloorBelow(level, feet),
                top != Integer.MIN_VALUE,
                feet.getY(),
                top);
    }

    /** Ground, wall tops and any other sturdy floor count as legal footing off the scaffolding. */
    public static boolean hasSolidFloorBelow(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Scaffold cell or sturdy floor — valid for stepping off a column onto a wall/platform. */
    public static boolean isWalkableStand(ServerLevel level, BlockPos stand) {
        return canOccupyStand(level, stand) || hasSolidFloorBelow(level, stand);
    }

    /**
     * One horizontal cell toward {@code work} that is not in the scaffold passable graph (e.g. solid
     * platform beside the column).
     */
    public static BlockPos horizontalLandStepToward(ServerLevel level, BlockPos feet, BlockPos work) {
        BlockPos best = null;
        int bestDxz = Integer.MAX_VALUE;
        int feetDxz = Math.abs(feet.getX() - work.getX()) + Math.abs(feet.getZ() - work.getZ());
        int[][] horiz = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : horiz) {
            BlockPos next = feet.offset(d[0], 0, d[1]);
            if (!isWalkableStand(level, next)) {
                continue;
            }
            int dxz = Math.abs(next.getX() - work.getX()) + Math.abs(next.getZ() - work.getZ());
            if (dxz < feetDxz && dxz < bestDxz) {
                bestDxz = dxz;
                best = next;
            }
        }
        return best;
    }

    /** Stand on top of the nearest column under/near the maid. */
    public static BlockPos snapStandOnColumn(ServerLevel level, BlockPos feet) {
        int top = topScaffoldBlockYNear(level, feet, 1);
        if (top == Integer.MIN_VALUE) {
            return feet;
        }
        int bestDist = Integer.MAX_VALUE;
        BlockPos best = feet;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (topScaffoldBlockY(level, feet.getX() + dx, feet.getZ() + dz, feet.getY()) != top) {
                    continue;
                }
                BlockPos onTop = new BlockPos(feet.getX() + dx, top + 1, feet.getZ() + dz);
                if (canOccupyStand(level, onTop)) {
                    int dist = Math.abs(dx) + Math.abs(dz);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = onTop;
                    }
                    continue;
                }
                BlockPos inside = new BlockPos(feet.getX() + dx, top, feet.getZ() + dz);
                if (canOccupyStand(level, inside)) {
                    int dist = Math.abs(dx) + Math.abs(dz);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = inside;
                    }
                }
            }
        }
        return best;
    }

    /** Teleport back to the column top when the maid was stacked into open air above scaffolding. */
    public static void correctFloatingAboveColumn(ServerLevel level, EntityMaid maid) {
        BlockPos feet = maid.blockPosition();
        if (!isFloatingAboveColumn(level, feet)) {
            return;
        }
        BlockPos dest = snapStandOnColumn(level, feet);
        if (dest.equals(feet)) {
            return;
        }
        maid.getNavigation().stop();
        float fallBefore = maid.fallDistance;
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.setNoGravity(false);
        maid.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        ScaffoldFallDebugLog.teleportClear(maid, "snap_column_down", fallBefore, feet, dest);
    }

    /**
     * Vanilla pathing cannot step from grass onto a one-block scaffold column; snap onto the column
     * top when the maid is still below it on the same XZ.
     */
    public static boolean snapOntoColumnIfBelowTop(ServerLevel level, EntityMaid maid) {
        BlockPos feet = maid.blockPosition();
        BlockPos dest = snapStandOnColumn(level, feet);
        if (dest.equals(feet) || dest.getY() <= feet.getY()) {
            return false;
        }
        if (isCellOccupiedByOthers(level, maid, dest)) {
            return false;
        }
        teleportMaidToStand(maid, feet, dest, "snap_column_up");
        // #region agent log
        var dbg = AgentDebugLog.data();
        dbg.addProperty("maid", ConstructionDebugIds.maid(maid));
        dbg.addProperty("from", feet.toShortString());
        dbg.addProperty("to", dest.toShortString());
        dbg.addProperty("dy", dest.getY() - feet.getY());
        AgentDebugLog.log("H13", "ScaffoldColumnSupport.snapOntoColumnIfBelowTop", "snap_column_up", dbg);
        // #endregion
        return true;
    }

    private static void teleportMaidToStand(EntityMaid maid, BlockPos from, BlockPos dest, String reason) {
        maid.getNavigation().stop();
        float fallBefore = maid.fallDistance;
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.setNoGravity(false);
        maid.teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        ScaffoldFallDebugLog.teleportClear(maid, reason, fallBefore, from, dest);
    }

    /** Pull the maid off scaffolding posts into the cell center so she can climb. */
    public static void centerInScaffoldCell(EntityMaid maid) {
        BlockPos feet = maid.blockPosition();
        double fracX = maid.getX() - feet.getX();
        double fracZ = maid.getZ() - feet.getZ();
        if (!ScaffoldFillUnderFeetPolicy.shouldCenterInScaffoldCell(fracX, fracZ)) {
            return;
        }
        maid.getNavigation().stop();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.teleportTo(feet.getX() + 0.5D, maid.getY(), feet.getZ() + 0.5D);
    }

    /**
     * Place scaffolding at the maid's XZ instead of walking into a neighbor tower or snap-down.
     *
     * @return true if a block was placed this tick
     */
    public static boolean tryFillUnderFeet(
            ServerLevel level, BuildJob job, EntityMaid maid, BlockPos workFeet, int sameBlockTicks) {
        BlockPos feet = maid.blockPosition();
        int dxz = Math.abs(workFeet.getX() - feet.getX()) + Math.abs(workFeet.getZ() - feet.getZ());
        int dy = workFeet.getY() - feet.getY();
        boolean floating = isFloatingAboveColumn(level, feet);
        boolean stuckBeside = ScaffoldFillUnderFeetPolicy.shouldFillWhenStuckBeside(dxz, dy, sameBlockTicks);
        boolean inScaffold = level.getBlockState(feet).is(Blocks.SCAFFOLDING)
                || level.getBlockState(feet.below()).is(Blocks.SCAFFOLDING);
        if (!ScaffoldFillUnderFeetPolicy.shouldFillUnderFeet(
                dxz, dy, floating, stuckBeside, inScaffold, sameBlockTicks)) {
            return false;
        }
        int top = topScaffoldBlockY(level, feet.getX(), feet.getZ(), feet.getY());
        // Already standing legally on column top — no fill needed, let normal nav climb further.
        if (top != Integer.MIN_VALUE && feet.getY() == top + 1
                && level.getBlockState(feet.below()).is(Blocks.SCAFFOLDING)) {
            return false;
        }
        int y = ScaffoldFillUnderFeetPolicy.fillY(feet.getY(), workFeet.getY(), top, inScaffold);
        if (y == Integer.MIN_VALUE && floating) {
            y = ScaffoldFillUnderFeetPolicy.fillY(feet.getY(), feet.getY(), top, false);
        }
        if (y == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos cell = new BlockPos(feet.getX(), y, feet.getZ());
        return ScaffoldService.tryPlaceOne(level, job, maid, cell);
    }

    private static boolean isCellOccupiedByOthers(ServerLevel level, EntityMaid maid, BlockPos dest) {
        AABB box = new AABB(dest).move(0.0D, 0.01D, 0.0D);
        return !level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class, box, other -> other != maid && other.isAlive())
                .isEmpty();
    }
}
