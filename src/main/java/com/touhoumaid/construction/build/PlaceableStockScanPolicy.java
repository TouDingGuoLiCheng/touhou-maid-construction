package com.touhoumaid.construction.build;

/**
 * {@code hasPlaceable} is called many times per tick and must not invoke auto-craft.
 * Answer from the stock index and skip duplicate block ids on the layer.
 */
public final class PlaceableStockScanPolicy {
    private PlaceableStockScanPolicy() {
    }

    public static boolean preferStockIndex(boolean indexReady) {
        return indexReady;
    }

    public static boolean shouldSkipDuplicate(String blockId, boolean alreadySeen) {
        return blockId != null && !blockId.isEmpty() && alreadySeen;
    }
}
