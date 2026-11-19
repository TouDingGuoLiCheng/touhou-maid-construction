package com.touhoumaid.construction.build;

import java.util.HashMap;
import java.util.Map;

/**
 * Start / login repair rescan: world match drives the pending queue; NBT placed counts must not
 * skip missing cells.
 */
public final class BuildStartRescanPolicy {
    private BuildStartRescanPolicy() {
    }

    /**
     * @param repairRescan true for Hub start / login BUILDING restore world rescan
     * @return whether {@link BuildJob#reconcileStalePending} may skip from saved placed counts
     */
    public static boolean allowSkipPendingFromSavedCounts(boolean repairRescan) {
        return !repairRescan;
    }

    /** After a repair rescan, project UI counts follow the world scan (not max with NBT). */
    public static Map<String, Integer> projectPlacedByBlockIdAfterRescan(
            Map<String, Integer> saved, Map<String, Integer> scanned) {
        Map<String, Integer> result = new HashMap<>();
        if (scanned != null) {
            for (Map.Entry<String, Integer> entry : scanned.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isEmpty()) {
                    continue;
                }
                int count = entry.getValue() == null ? 0 : entry.getValue();
                if (count > 0) {
                    result.put(entry.getKey(), count);
                }
            }
        }
        return result;
    }

    /**
     * When matched placed cells shrink after rescan, reduce consumed by the drop so re-placing
     * does not permanently over-count vault withdrawals. Never raise consumed here.
     */
    public static int adjustConsumedAfterRescan(int consumed, int previousMatched, int matchedAfter) {
        int safeConsumed = Math.max(0, consumed);
        int prev = Math.max(0, previousMatched);
        int after = Math.max(0, matchedAfter);
        if (after >= prev) {
            return safeConsumed;
        }
        return Math.max(0, safeConsumed - (prev - after));
    }
}
