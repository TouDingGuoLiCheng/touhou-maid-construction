package com.touhoumaid.construction.build;

/**
 * Gate for expensive construction nav. Per-maid and per-tick caps are disabled so
 * stuck maids can path immediately; callers still skip path probes when no solid stand exists.
 */
public final class BuildHeavyNavBudgetPolicy {
    /** Kept for call-site reset of {@code ticksSinceHeavyNav}; cooldown is not enforced. */
    public static final int MIN_TICKS_BETWEEN = 0;
    /** Kept for API compatibility; global slot cap is not enforced. */
    public static final int MAX_PER_SERVER_TICK = Integer.MAX_VALUE;

    private BuildHeavyNavBudgetPolicy() {
    }

    public static boolean maidCooldownReady(int ticksSinceLastHeavy) {
        return true;
    }

    public static boolean canTakeGlobalSlot(int usedThisTick) {
        return true;
    }

    public static boolean mayRunHeavy(int ticksSinceLastHeavy, int usedThisTick) {
        return true;
    }

    /**
     * Path probes only matter when a solid stand exists. An empty neighbourhood must not
     * waste a createPath / canPathReach scan when scaffolding should start instead.
     */
    public static boolean standSearchNeedsPathProbe(boolean hasSolidCandidate) {
        return hasSolidCandidate;
    }
}
