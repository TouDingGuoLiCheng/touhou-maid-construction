package com.touhoumaid.construction.build;

/**
 * When navigation finishes (or the maid is near the work cell) but she still cannot interact,
 * count ticks and give up so the job can direct-place instead of re-pathing forever.
 */
public final class BuildArrivedPolicy {
    /** 2 seconds at 20 TPS — aligned with {@link BuildMoveStuckPolicy}. */
    public static final int GIVE_UP_TICKS = 40;
    /** Within ~1.5 blocks of the planned standing cell counts as "arrived". */
    public static final double NEAR_WORK_DIST_SQR = 2.25D;

    private BuildArrivedPolicy() {
    }

    public record State(int blockedTicks) {
        public static final State EMPTY = new State(0);
    }

    public static State onTick(State prev, double distWorkSqr, boolean navDone, boolean canInteract) {
        if (canInteract) {
            return State.EMPTY;
        }
        boolean arrived = distWorkSqr <= NEAR_WORK_DIST_SQR;
        if (!arrived) {
            return State.EMPTY;
        }
        return new State(prev.blockedTicks() + 1);
    }

    public static boolean shouldGiveUp(State state) {
        return state.blockedTicks() >= GIVE_UP_TICKS;
    }
}
