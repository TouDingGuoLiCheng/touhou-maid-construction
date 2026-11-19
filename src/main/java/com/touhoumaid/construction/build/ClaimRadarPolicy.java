package com.touhoumaid.construction.build;

/**
 * Outward claim scan: budgeted material checks with a cross-tick cursor.
 * Expensive survival checks stay under {@link ClaimScanBudgetPolicy}.
 *
 * <p>Callers must keep radar state keyed by {@code (maidId, layer)} so upper-layer
 * fallback in {@code claimNearest} cannot wipe another layer's cursor.
 */
public final class ClaimRadarPolicy {
    /** Max {@code hasMaterial} checks per {@code claimNearestOnLayer} call. */
    public static final int MAX_MATERIAL_CHECKS_PER_CLAIM = 48;

    private ClaimRadarPolicy() {
    }

    public record Window(int fromInclusive, int toExclusive, int nextCursor, boolean passExhausted) {
    }

    /**
     * Slice {@code [cursor, cursor+budget)} of a distance-ordered candidate list.
     * When the slice reaches the end, {@code nextCursor} resets to 0 and {@code passExhausted} is true.
     */
    public static Window nextWindow(int cursor, int listSize, int budget) {
        if (listSize <= 0 || budget <= 0) {
            return new Window(0, 0, 0, true);
        }
        int from = Math.min(Math.max(0, cursor), listSize);
        if (from >= listSize) {
            return new Window(listSize, listSize, 0, true);
        }
        int to = Math.min(from + budget, listSize);
        boolean exhausted = to >= listSize;
        int next = exhausted ? 0 : to;
        return new Window(from, to, next, exhausted);
    }

    /**
     * Material starve only after a full outward pass found rejects and zero stocked cells.
     * Mid-pass empty windows must not starve — farther rings may still have stock.
     */
    public static boolean shouldStarveAfterPass(boolean passExhausted, int materialRejected, int materialAccepted) {
        return passExhausted && materialRejected > 0 && materialAccepted <= 0;
    }

    /**
     * Rebuild the distance-ordered cheap list when layer / stock / queue shape change,
     * or when starting a new pass ({@code cursorAtStart}) with a moved search center.
     * Mid-pass center drift is ignored so the radar cursor can resume.
     */
    public static boolean shouldRebuildOrdered(
            boolean cursorAtStart,
            int layer,
            int storedLayer,
            int pendingSize,
            int storedPendingSize,
            int stockRevision,
            int storedStockRevision,
            long centerKey,
            long storedCenterKey) {
        if (layer != storedLayer
                || pendingSize != storedPendingSize
                || stockRevision != storedStockRevision) {
            return true;
        }
        if (cursorAtStart && centerKey != storedCenterKey) {
            return true;
        }
        return false;
    }
}
