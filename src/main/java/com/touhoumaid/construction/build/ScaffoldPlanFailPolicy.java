package com.touhoumaid.construction.build;

/** After a scaffold plan fails: retry the same stand once, then direct-place. Do not idle-claim a new cell. */
public final class ScaffoldPlanFailPolicy {
    public enum Next {
        RETRY_MOVE,
        DIRECT_PLACE
    }

    private ScaffoldPlanFailPolicy() {
    }

    public static Next afterPlanFailed(int attemptsIncludingThis) {
        return afterPlanFailed(attemptsIncludingThis, false);
    }

    public static Next afterPlanFailed(int attemptsIncludingThis, boolean climbingScaffold) {
        if (climbingScaffold) {
            return Next.RETRY_MOVE;
        }
        return attemptsIncludingThis >= 2 ? Next.DIRECT_PLACE : Next.RETRY_MOVE;
    }

    /** Same stand returned as NOT_NEEDED while the maid cannot enter it. */
    public static Next afterNotNeededWhileStuck(int notNeededWhileStuck) {
        return afterNotNeededWhileStuck(notNeededWhileStuck, false);
    }

    public static Next afterNotNeededWhileStuck(int notNeededWhileStuck, boolean climbingScaffold) {
        if (climbingScaffold) {
            return Next.RETRY_MOVE;
        }
        return ConstructionWalkPolicy.shouldEscalateNotNeededWalk(notNeededWhileStuck)
                ? Next.DIRECT_PLACE
                : Next.RETRY_MOVE;
    }
}
