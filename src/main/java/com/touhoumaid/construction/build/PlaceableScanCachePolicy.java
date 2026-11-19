package com.touhoumaid.construction.build;

/**
 * Avoid re-walking thousands of pending cells for {@code hasPlaceableTaskOnActiveLayer}
 * many times per tick while stock and queue shape are unchanged.
 */
public final class PlaceableScanCachePolicy {
    private PlaceableScanCachePolicy() {
    }

    public static boolean isHit(int cachedKey, int currentKey, boolean cacheInitialized) {
        return cacheInitialized && cachedKey == currentKey;
    }

    public static int cacheKey(
            int stockRevision, int activeLayer, int pendingSize, int unreachableSize, int deferredSize) {
        int key = stockRevision;
        key = 31 * key + activeLayer;
        key = 31 * key + pendingSize;
        key = 31 * key + unreachableSize;
        key = 31 * key + deferredSize;
        return key;
    }
}
