package com.touhoumaid.construction.build;

/**
 * A target with no solid stand must not rescan its air neighbourhood every tick.
 * One empty result holds for about a second so scaffold / vault can use the tick.
 */
public final class WorkStandMissRescanPolicy {
    /** 1 second at 20 TPS. */
    public static final int RESCAN_INTERVAL_TICKS = 20;

    private WorkStandMissRescanPolicy() {
    }

    public static boolean shouldScan(boolean sameTarget, int ticksSinceMiss) {
        return !sameTarget || ticksSinceMiss >= RESCAN_INTERVAL_TICKS;
    }
}
