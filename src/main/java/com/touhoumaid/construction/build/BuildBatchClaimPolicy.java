package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.List;

/** Caps how many nearby cells one maid may hold so others still get work. */
public final class BuildBatchClaimPolicy {
    /** Max claimed cells in the local placement queue (not the search radius). */
    public static final int MAX_TASKS = 5;

    private BuildBatchClaimPolicy() {
    }

    public static <T> List<T> take(List<T> reachable, int limit) {
        if (reachable == null || reachable.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (reachable.size() <= limit) {
            return List.copyOf(reachable);
        }
        List<T> sliced = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            sliced.add(reachable.get(i));
        }
        return sliced;
    }
}
