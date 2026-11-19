package com.touhoumaid.construction.build;

/**
 * After {@code PLACE_BLOCKED}: climbing maids snap to the work stand instead of forever retrying
 * a dead tower extension; ground maids escalate like plan failure.
 */
public final class ScaffoldPlaceBlockedPolicy {
    public enum Next {
        RETRY_MOVE,
        TELEPORT_WORK,
        DIRECT_PLACE
    }

    /** First blocked place while climbing → teleport to work stand. */
    public static final int CLIMBING_TELEPORT_AFTER_ATTEMPTS = 1;

    private ScaffoldPlaceBlockedPolicy() {
    }

    public static Next afterPlaceBlocked(int attemptsIncludingThis, boolean climbingScaffold) {
        if (climbingScaffold) {
            return attemptsIncludingThis >= CLIMBING_TELEPORT_AFTER_ATTEMPTS
                    ? Next.TELEPORT_WORK
                    : Next.RETRY_MOVE;
        }
        return attemptsIncludingThis >= 2 ? Next.DIRECT_PLACE : Next.RETRY_MOVE;
    }
}
