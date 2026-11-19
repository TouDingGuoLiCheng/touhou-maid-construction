package com.touhoumaid.construction.build;

/**
 * Caps how many {@code canPathReach} probes {@link BuildWorkPosition#find} may run in one call.
 * Candidate gather can return dozens of cells; each probe is a full land pathfind.
 */
public final class BuildWorkPositionFindPolicy {
    /** Prefer nearest sorted stands; stop before unbounded A* fan-out stalls the tick. */
    public static final int MAX_PATH_REACH_CHECKS = 8;

    private BuildWorkPositionFindPolicy() {
    }

    public static boolean mayCheckPathReach(int alreadyChecked) {
        return alreadyChecked >= 0 && alreadyChecked < MAX_PATH_REACH_CHECKS;
    }

    public static int pathReachBudget(int candidateCount) {
        if (candidateCount <= 0) {
            return 0;
        }
        return Math.min(candidateCount, MAX_PATH_REACH_CHECKS);
    }
}
