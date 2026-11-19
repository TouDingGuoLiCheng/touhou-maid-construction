package com.touhoumaid.construction.build;

/**
 * Anchor-based "no real progress" detector for a held task.
 *
 * <p>Unlike same-cell counters, this one survives recovery actions: rebuilding scaffolds, filling
 * under the feet or snapping one cell up/down all change the feet cell without getting the maid
 * anywhere, so those counters keep resetting and the maid never escapes. Progress means leaving a
 * small neighbourhood of the anchor.
 */
public final class BuildProgressStallPolicy {
    /** 3 seconds at 20 TPS inside the same 2-block neighbourhood. */
    public static final int STALL_TICKS = ConstructionSiteArrivePolicy.STUCK_TELEPORT_TICKS;
    public static final int PROGRESS_RADIUS = 2;

    private BuildProgressStallPolicy() {
    }

    public record State(boolean initialized, int x, int y, int z, int ticks) {
        public static final State EMPTY = new State(false, 0, 0, 0, 0);
    }

    public static State onTick(State prev, int x, int y, int z) {
        if (!prev.initialized()) {
            return new State(true, x, y, z, 1);
        }
        int drift = Math.abs(prev.x() - x) + Math.abs(prev.y() - y) + Math.abs(prev.z() - z);
        if (drift > PROGRESS_RADIUS) {
            return new State(true, x, y, z, 1);
        }
        return new State(true, prev.x(), prev.y(), prev.z(), prev.ticks() + 1);
    }

    public static boolean isStalled(State state) {
        return state.initialized() && state.ticks() >= STALL_TICKS;
    }

    public static int stallTicks(State state) {
        return state.ticks();
    }
}
