package com.touhoumaid.construction.build;

/** When repeated pathfinding to the same task cell fails, escalate to vault direct-place. */
public final class BuildPathNavFailPolicy {
    /** Recorded nav failures required (> this value triggers). Planning: 同格寻路失败 ≥1. */
    public static final int VAULT_DIRECT_PLACE_AFTER_FAILURES = 0;
    /** Same task or neighbourhood must be stalled at least this long (3 s at 20 TPS). */
    public static final int MIN_STALL_TICKS = 60;
    /** Per-maid spacing between vault direct-placements (5 s). */
    public static final int MAID_COOLDOWN_TICKS = 100;

    private BuildPathNavFailPolicy() {
    }

    /**
     * @param hasWorkStand when true, vault is suppressed so stuck recovery teleports to the stand
     *                     first; vault is only for cells with no stand (or after stand is cleared)
     */
    public static boolean shouldVaultDirectPlace(
            int recordedFailures,
            int taskClaimTicks,
            int neighborhoodStallTicks,
            boolean hasWorkStand) {
        if (hasWorkStand) {
            return false;
        }
        if (recordedFailures <= VAULT_DIRECT_PLACE_AFTER_FAILURES) {
            return false;
        }
        return taskClaimTicks >= MIN_STALL_TICKS || neighborhoodStallTicks >= MIN_STALL_TICKS;
    }
}
