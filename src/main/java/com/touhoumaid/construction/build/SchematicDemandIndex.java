package com.touhoumaid.construction.build;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Blueprint demand per block id, counted once and reused.
 * Remaining need is this total minus {@code placedByBlockId}.
 */
public final class SchematicDemandIndex {
    private final Map<String, Integer> demandByBlockId = new HashMap<>();

    public void clear() {
        demandByBlockId.clear();
    }

    public void count(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        demandByBlockId.merge(blockId, 1, Integer::sum);
    }

    public void rebuild(Iterable<String> blockIds, Predicate<String> ignored) {
        clear();
        if (blockIds == null) {
            return;
        }
        for (String blockId : blockIds) {
            if (blockId == null || blockId.isEmpty() || ignored.test(blockId)) {
                continue;
            }
            count(blockId);
        }
    }

    public int demandOf(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return 0;
        }
        return demandByBlockId.getOrDefault(blockId, 0);
    }
}
