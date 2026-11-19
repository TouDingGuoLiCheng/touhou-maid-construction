package com.touhoumaid.construction.build;

/**
 * Stuck-while-moving rule: no meaningful displacement within a small neighbourhood for
 * {@link #STUCK_TICKS} cancels the current path.
 */
public final class BuildMoveStuckPolicy {
    public static final int STUCK_TICKS = BuildProgressStallPolicy.STALL_TICKS;

    private BuildMoveStuckPolicy() {
    }

    public record State(boolean initialized, int x, int y, int z, int stuckTicks) {
        public static final State EMPTY = new State(false, 0, 0, 0, 0);
    }

    public static State onTick(State prev, int x, int y, int z) {
        return fromProgress(BuildProgressStallPolicy.onTick(toProgress(prev), x, y, z));
    }

    public static boolean isStuck(State state) {
        return BuildProgressStallPolicy.isStalled(toProgress(state));
    }

    private static BuildProgressStallPolicy.State toProgress(State state) {
        if (!state.initialized()) {
            return BuildProgressStallPolicy.State.EMPTY;
        }
        return new BuildProgressStallPolicy.State(true, state.x(), state.y(), state.z(), state.stuckTicks());
    }

    private static State fromProgress(BuildProgressStallPolicy.State state) {
        return new State(state.initialized(), state.x(), state.y(), state.z(), state.ticks());
    }
}
