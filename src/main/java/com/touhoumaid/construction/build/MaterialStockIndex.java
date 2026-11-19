package com.touhoumaid.construction.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Incremental vault stock by item id for claim scans.
 * <p>
 * Mutations bump {@link #revision()} so claim-starve windows can clear without a full rescan.
 */
public final class MaterialStockIndex {
    private final Map<String, Integer> counts = new HashMap<>();
    private final Set<String> unavailableBlockIds = new HashSet<>();
    private int revision;
    private boolean ready;

    public boolean isReady() {
        return ready;
    }

    public int revision() {
        return revision;
    }

    public void rebuildFromCounts(Map<String, Integer> itemCounts) {
        counts.clear();
        unavailableBlockIds.clear();
        if (itemCounts != null) {
            for (Map.Entry<String, Integer> e : itemCounts.entrySet()) {
                if (e.getKey() == null || e.getKey().isEmpty()) {
                    continue;
                }
                int n = e.getValue() == null ? 0 : e.getValue();
                if (n > 0) {
                    counts.put(e.getKey(), n);
                }
            }
        }
        ready = true;
        revision++;
    }

    public void applyDeposit(String itemId, int amount) {
        if (itemId == null || itemId.isEmpty() || amount <= 0) {
            return;
        }
        counts.merge(itemId, amount, Integer::sum);
        unavailableBlockIds.clear();
        revision++;
    }

    public void applyConsume(String itemId, int amount) {
        if (itemId == null || itemId.isEmpty() || amount <= 0) {
            return;
        }
        int have = counts.getOrDefault(itemId, 0);
        int next = have - amount;
        if (next > 0) {
            counts.put(itemId, next);
        } else {
            counts.remove(itemId);
        }
        revision++;
    }

    public void markBlockUnavailable(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        if (unavailableBlockIds.add(blockId)) {
            revision++;
        }
    }

    public boolean isBlockUnavailable(String blockId) {
        return blockId != null && !blockId.isEmpty() && unavailableBlockIds.contains(blockId);
    }

    public int countOf(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        return counts.getOrDefault(itemId, 0);
    }

    public boolean hasForBlockId(String blockId, List<String> matchItemIds) {
        if (blockId != null && !blockId.isEmpty() && unavailableBlockIds.contains(blockId)) {
            return false;
        }
        if (matchItemIds == null || matchItemIds.isEmpty()) {
            return false;
        }
        for (String itemId : matchItemIds) {
            if (countOf(itemId) > 0) {
                return true;
            }
        }
        return false;
    }
}
