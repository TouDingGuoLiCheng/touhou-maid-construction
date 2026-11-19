package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * After a maid fails to place at a cell, deprioritize that cell while the active layer still
 * has other claimable work. Retry the failed cell only when it is the last option left.
 */
public final class BuildPlaceFailRetryPolicy {
    private BuildPlaceFailRetryPolicy() {
    }

    public static <T> List<T> deprioritizeFailed(
            List<T> nearestFirst, Set<Long> failedPacked, ToLongFunction<T> pack) {
        if (nearestFirst == null || nearestFirst.isEmpty()
                || failedPacked == null || failedPacked.isEmpty()) {
            return nearestFirst;
        }
        List<T> fresh = new ArrayList<>();
        List<T> retry = new ArrayList<>();
        for (T item : nearestFirst) {
            if (failedPacked.contains(pack.applyAsLong(item))) {
                retry.add(item);
            } else {
                fresh.add(item);
            }
        }
        if (fresh.isEmpty()) {
            return nearestFirst;
        }
        List<T> ordered = new ArrayList<>(fresh.size() + retry.size());
        ordered.addAll(fresh);
        ordered.addAll(retry);
        return ordered;
    }
}
