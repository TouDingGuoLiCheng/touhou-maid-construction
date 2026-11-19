package com.touhoumaid.construction.build;

/**
 * Detects when a maid is moving but not getting closer to the assigned work stand.
 *
 * <p>Unlike {@link BuildProgressStallPolicy}, wandering across the site does not reset this
 * counter unless Manhattan distance to the work stand actually decreases.
 */
public final class BuildWorkGoalStallPolicy {
    public static final int STALL_TICKS = BuildProgressStallPolicy.STALL_TICKS;

    private BuildWorkGoalStallPolicy() {
    }

    public record State(boolean initialized, int lastDistance, int ticks) {
        public static final State EMPTY = new State(false, 0, 0);
    }

    public static State onTick(State prev, int distanceManhattan) {
        if (!prev.initialized()) {
            return new State(true, distanceManhattan, 1);
        }
        if (distanceManhattan < prev.lastDistance()) {
            return new State(true, distanceManhattan, 1);
        }
        return new State(true, prev.lastDistance(), prev.ticks() + 1);
    }

    public static boolean isStalled(State state) {
        return state.initialized() && state.ticks() >= STALL_TICKS;
    }

    public static int stallTicks(State state) {
        return state.ticks();
    }
}
