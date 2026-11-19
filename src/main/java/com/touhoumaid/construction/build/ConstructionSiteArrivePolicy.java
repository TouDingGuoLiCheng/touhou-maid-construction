package com.touhoumaid.construction.build;

/**
 * Teleport is a rescue only: the maid is absolutely far from the <em>site region</em>
 * (shortest XZ to the AABB), or she has been stuck on the same block for a long time.
 * Never teleport just because she is far from a stay-put center.
 */
public final class ConstructionSiteArrivePolicy {
    /** Same extra blocks as TLM follow-owner teleport. */
    public static final double TLM_FOLLOW_TELEPORT_MARGIN = 2.0D;
    /**
     * Absolute distance from the site AABB that counts as "too far" (not the small follow band).
     */
    public static final double SITE_RESCUE_RANGE = 32.0D;
    /** 3 seconds at 20 TPS on the same block. */
    public static final int STUCK_TELEPORT_TICKS = 60;
    /** Do not immediately teleport again after a rescue (5 seconds). */
    public static final int STUCK_TELEPORT_COOLDOWN_TICKS = 100;
    /** 5 seconds at 20 TPS: she is outside the build range and terrain will not let her walk in. */
    public static final int OUT_OF_RANGE_STUCK_TICKS = 100;
    /**
     * Heightmap fallback only: when no stand exists near the hint, do not yeet the maid onto
     * the building's mid-height surface. A real stand is not rejected for vertical distance.
     */
    public static final int TELEPORT_MAX_VERTICAL_DELTA = 6;

    private ConstructionSiteArrivePolicy() {
    }

    public static boolean shouldRescueTeleport(double shortestDistanceToSite) {
        return shortestDistanceToSite > SITE_RESCUE_RANGE;
    }

    public static boolean shouldStuckTeleport(int sameBlockTicks) {
        return shouldStuckTeleport(sameBlockTicks, STUCK_TELEPORT_COOLDOWN_TICKS);
    }

    public static boolean shouldStuckTeleport(int sameBlockTicks, int ticksSinceLastTeleport) {
        return sameBlockTicks >= STUCK_TELEPORT_TICKS
                && ticksSinceLastTeleport >= STUCK_TELEPORT_COOLDOWN_TICKS;
    }

    /**
     * She cannot scaffold outside the build range, so a maid walled off from the site would
     * otherwise stand there forever. Rescue her in well before the generic stuck timer.
     */
    public static boolean shouldRescueOutOfRange(
            boolean outsideBuildRange, int sameBlockTicks, int ticksSinceLastTeleport) {
        return outsideBuildRange
                && sameBlockTicks >= OUT_OF_RANGE_STUCK_TICKS
                && ticksSinceLastTeleport >= STUCK_TELEPORT_COOLDOWN_TICKS;
    }

    public static boolean isNoOpTeleport(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        return fromX == toX && fromY == toY && fromZ == toZ;
    }

    /** Idle maid waiting on a claimable layer must not be yanked upward through a scaffold shaft. */
    public static boolean shouldSuppressStuckTeleport(boolean idleWithoutClaim, boolean jobHasPlaceableTasks) {
        return idleWithoutClaim && jobHasPlaceableTasks;
    }

    /**
     * Stuck rescue needs a work stand to aim at. A held claim with {@code work=-} while already
     * on-site must not hop to a salt-picked neighbour (log: 91↔90 every ~5s, {@code siteDist=0}).
     */
    public static boolean shouldAllowStuckRescue(
            boolean suppressIdlePickStuck, boolean hasWorkStand, boolean movingOrClaimOrScaffold) {
        if (suppressIdlePickStuck || !movingOrClaimOrScaffold) {
            return false;
        }
        return hasWorkStand;
    }

    /**
     * Inside the site AABB with no preferred stand, a 1–2 block sideways hop is not a rescue —
     * {@code findStandNear} will just bounce between the same two cells on the next stuck tick.
     */
    public static boolean shouldRejectAdjacentStuckHop(
            double siteDist, boolean hasPreferStand, int manhattanXz) {
        if (hasPreferStand || siteDist > 0.01D) {
            return false;
        }
        return manhattanXz > 0 && manhattanXz <= 2;
    }

    /**
     * Distance rescue must keep the maid's current Y. Site-center Y is mid-building and a high
     * work stand is often a scaffold cell — both drop her tens of blocks after teleport.
     */
    public static int distanceRescueHintY(int maidY, int preferStandY, int areaCenterY) {
        return maidY;
    }

    /**
     * Heightmap at the site can be the top of the in-progress building. Never use that as a
     * fallback if it would lift the maid more than {@code maxLift} blocks.
     */
    public static int fallbackStandY(int hintY, int surfaceY, int fromY, int maxLift) {
        int candidate = Math.max(hintY, surfaceY);
        if (candidate - fromY > maxLift) {
            return fromY;
        }
        return candidate;
    }

    public static boolean shouldRejectVerticalTeleport(int fromY, int toY, int maxDelta) {
        return Math.abs(toY - fromY) > maxDelta;
    }

    public static boolean needsVerticalSafety(String reason) {
        return "rescue".equals(reason) || "stuck".equals(reason) || "out-of-range".equals(reason);
    }

    /**
     * Same-column rescue that lifts the maid more than a few blocks (e.g. -51 to -9) causes a fatal
     * drop back down the shaft.
     */
    public static boolean isUnsafeShaftTeleport(
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        int dy = toY - fromY;
        if (dy <= 2) {
            return false;
        }
        if (Math.abs(toX - fromX) > 2 || Math.abs(toZ - fromZ) > 2) {
            return false;
        }
        return dy > 8;
    }

    /** @deprecated use {@link #shouldRescueTeleport(double)} */
    @Deprecated
    public static boolean shouldTeleport(double shortestDistanceToSite, double followRange) {
        return shortestDistanceToSite > followRange + TLM_FOLLOW_TELEPORT_MARGIN;
    }

    public static double shortestXzDistanceToAabb(
            double x, double z, int minX, int maxX, int minZ, int maxZ) {
        double nx = clamp(x, minX, maxX);
        double nz = clamp(z, minZ, maxZ);
        return Math.hypot(x - nx, z - nz);
    }

    public static double[] nearestXzOnAabb(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        return new double[]{clamp(x, minX, maxX), clamp(z, minZ, maxZ)};
    }

    private static double clamp(double v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
