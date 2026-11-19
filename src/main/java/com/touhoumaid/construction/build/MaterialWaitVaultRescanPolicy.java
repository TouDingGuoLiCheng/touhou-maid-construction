package com.touhoumaid.construction.build;

/**
 * While a maid is waiting on missing materials, rebuild the stock index on this
 * cadence so items placed in the terminal GUI are noticed.
 */
public final class MaterialWaitVaultRescanPolicy {
    /** About 10 seconds at 20 ticks per second. */
    public static final int INTERVAL_TICKS = 200;

    private MaterialWaitVaultRescanPolicy() {
    }

    public static int arm() {
        return INTERVAL_TICKS;
    }

    /** @param ticksUntilRescan countdown remaining before the next vault scan */
    public static boolean due(int ticksUntilRescan) {
        return ticksUntilRescan <= 0;
    }
}
