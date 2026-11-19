package com.touhoumaid.construction.build;

import com.touhoumaid.construction.project.MaterialBlockPolicy;

/**
 * Schematic water and lava are not maid tasks. After maids finish every claimable cell,
 * the job vault-places unreachable leftovers, then water/lava, using the blueprint state.
 * Vanilla flow is left alone.
 */
public final class FluidEndPlacePolicy {
    /** Neighbor updates on fluids are expensive; one burst still finishes a pool in a few ticks. */
    public static final int CELLS_PER_TICK = 512;

    private FluidEndPlacePolicy() {
    }

    public static boolean isEndPlacedFluid(String blockId) {
        return MaterialBlockPolicy.isAutoFree(blockId);
    }

    /** Stairs, slabs and similar keep a solid shape for maids; the water goes in at the end. */
    public static boolean isWaterlogged(java.util.Map<String, String> properties) {
        return properties != null && "true".equals(properties.get("waterlogged"));
    }

    public static boolean maidMayPlace(String blockId) {
        return blockId != null && !isEndPlacedFluid(blockId);
    }

    /**
     * Vanilla destroy puts the same water or lava back, so harvest never clears the cell.
     * Placement should overwrite these with {@code setBlock} instead.
     */
    public static boolean replaceOccupiedFluid(String blockId) {
        return isEndPlacedFluid(blockId);
    }

    /**
     * A fluid cell is not support the force-placer must build before the block above it.
     * Blocks that truly need the fluid stay deferred and are placed after the end dump.
     */
    public static boolean isForcePlaceSupport(String blockId) {
        return blockId != null && !isEndPlacedFluid(blockId);
    }

    /**
     * Pending cells already marked unreachable are leftovers for the end dump,
     * not work maids can still claim.
     */
    public static int maidClaimablePending(int pendingCount, int unreachableStillPending) {
        if (pendingCount <= 0) {
            return 0;
        }
        return Math.max(0, pendingCount - Math.max(0, unreachableStillPending));
    }

    /**
     * Start the end dump when maids have nothing left to claim. Unreachable leftovers
     * and fluids are placed in the same burst; deferred leftovers dump with them.
     */
    public static boolean readyToDump(int maidClaimablePending, int inProgressClaims) {
        return maidClaimablePending <= 0 && inProgressClaims <= 0;
    }
}
