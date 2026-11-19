package com.touhoumaid.construction.build;

/**
 * Placement pacing between consecutive block places (20 TPS).
 */
public final class BuildPlaceCadencePolicy {
    /** 0.5 s between placements — always applied, including batch continuation. */
    public static final int PLACE_INTERVAL_TICKS = 10;

    private BuildPlaceCadencePolicy() {
    }

    /**
     * Face-hold before placing can be skipped when the maid is already looking at the target.
     */
    public static int faceHoldTicks(boolean fromBatch, boolean alreadyFacing) {
        if (fromBatch && alreadyFacing) {
            return 0;
        }
        return fromBatch ? 1 : 4;
    }
}
