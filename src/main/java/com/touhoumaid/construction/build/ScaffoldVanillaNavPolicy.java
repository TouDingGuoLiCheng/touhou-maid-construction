package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Vanilla {@code Navigation} driver for scaffold key-node routes. */
public final class ScaffoldVanillaNavPolicy {
    /** Pathfinding stuck (waypoint route or direct walk) before scaffold escalation. */
    public static final int PATH_STUCK_TELEPORT_TICKS = ConstructionSiteArrivePolicy.STUCK_TELEPORT_TICKS;

    private ScaffoldVanillaNavPolicy() {
    }

    public static boolean shouldTeleportToWork(int stallTicks) {
        return stallTicks >= PATH_STUCK_TELEPORT_TICKS;
    }

    /** Either neighbourhood or work-goal stall may unlock the work-stand snap. */
    public static boolean shouldTeleportToWork(int neighborhoodStallTicks, int workGoalStallTicks) {
        return shouldTeleportToWork(neighborhoodStallTicks) || shouldTeleportToWork(workGoalStallTicks);
    }

    /**
     * Holding a claim with a work stand (including IDLE after out-of-reach) may snap once stall
     * is met — vault must not run ahead of this teleport.
     */
    public static boolean maySnapWhileHoldingWorkStand(boolean hasWorkStand, boolean stallThresholdMet) {
        return hasWorkStand && stallThresholdMet;
    }

    /**
     * When the stall threshold is already met, snap even if vanilla nav finished / failed —
     * that is exactly when the maid is stuck short of the work stand.
     */
    public static boolean maySnapToWorkDespiteNav(boolean pathfindingTowardWork, boolean stallThresholdMet) {
        return pathfindingTowardWork || stallThresholdMet;
    }

    /** Destination must be a walkable stand (solid floor or scaffold) with headroom. */
    public static boolean isTeleportStandEligible(boolean walkableStand, boolean headroom) {
        return walkableStand && headroom;
    }

    public static boolean canTeleportToWork(ServerLevel level, BlockPos workFeet) {
        if (level == null || workFeet == null) {
            return false;
        }
        return isTeleportStandEligible(
                ScaffoldColumnSupport.isWalkableStand(level, workFeet),
                ScaffoldHeadroomPolicy.hasStandHeadroom(level, workFeet));
    }
}
