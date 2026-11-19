package com.touhoumaid.construction.build;

import java.util.List;

/**
 * When a scaffold approach path has been planned once, keep executing that lock until
 * the next place is blocked or the maid clearly leaves the path — do not replan every tick.
 */
public final class ScaffoldPathLockPolicy {
    /** Manhattan distance beyond nearest locked cell → treat as off-path (option B). */
    public static final int OFF_PATH_MANHATTAN = 4;

    private ScaffoldPathLockPolicy() {
    }

    public static boolean isOffLockedPath(int feetX, int feetY, int feetZ, List<ScaffoldLayout.Cell> lockedPath) {
        if (lockedPath == null || lockedPath.isEmpty()) {
            return true;
        }
        int best = Integer.MAX_VALUE;
        for (ScaffoldLayout.Cell cell : lockedPath) {
            int d = Math.abs(cell.x() - feetX) + Math.abs(cell.y() - feetY) + Math.abs(cell.z() - feetZ);
            if (d < best) {
                best = d;
            }
            if (best <= OFF_PATH_MANHATTAN) {
                return false;
            }
        }
        return true;
    }

    /** True when we should advance the locked path instead of calling the planner. */
    public static boolean shouldExecuteLockedPath(boolean hasLockedPath, boolean placeBlocked, boolean offPath) {
        return hasLockedPath && !placeBlocked && !offPath;
    }

    /** True when the locked path must be discarded and {@code ScaffoldPlanner} run again. */
    public static boolean shouldReplan(boolean hasLockedPath, boolean placeBlocked, boolean offPath) {
        return !shouldExecuteLockedPath(hasLockedPath, placeBlocked, offPath);
    }

    /**
     * @param alreadyScaffold cell already has scaffolding (record + walk — not a block failure)
     * @param worldPlaceFailed {@link ScaffoldPlanner#placeScaffoldBlock} returned false
     */
    public static boolean isPlaceBlocked(boolean alreadyScaffold, boolean worldPlaceFailed) {
        return !alreadyScaffold && worldPlaceFailed;
    }
}
