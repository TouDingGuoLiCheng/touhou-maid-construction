package com.touhoumaid.construction.build;

/**
 * Whether neighbourhood stall should accumulate this tick.
 *
 * <p>IDLE without a held claim must not accumulate stall — otherwise pick→fail→release loops
 * suppress defer and the maid reclaim-loops the same nearest cell forever.
 */
public final class BuildNeighborhoodStallTrackPolicy {
    private BuildNeighborhoodStallTrackPolicy() {
    }

    /**
     * @param phaseTracks             MOVING / FACING / PLACING / BATCH
     * @param idleWithPlaceableWork   IDLE while the active layer still has placeable work
     * @param hasActiveClaim          maid currently holds a claimed task
     */
    public static boolean shouldTrack(
            boolean phaseTracks, boolean idleWithPlaceableWork, boolean hasActiveClaim) {
        return phaseTracks || (idleWithPlaceableWork && hasActiveClaim);
    }
}
