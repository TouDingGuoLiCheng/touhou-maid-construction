package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Claim weighting: easy cells first. A cell with solid footing nearby, support below and
 * attached neighbours beats a floating edge cell that only a scaffold could reach.
 */
public final class BuildClaimEasePolicy {
    /** A standable solid cell near the target outweighs any amount of attachment. */
    public static final int SOLID_STAND_WEIGHT = 8;
    public static final int SUPPORT_BELOW_WEIGHT = 2;
    public static final int SIDE_NEIGHBOR_WEIGHT = 1;
    /** Four horizontal sides plus the cell above. */
    public static final int MAX_SIDE_NEIGHBORS = 5;
    /** World probes per claim stay bounded; the rest keep nearest order after the scored cells. */
    public static final int MAX_SCORED_PER_CLAIM = 32;

    private BuildClaimEasePolicy() {
    }

    public static int score(boolean solidStandNear, boolean supportBelow, int sideNeighbors) {
        int sides = Math.max(0, Math.min(sideNeighbors, MAX_SIDE_NEIGHBORS));
        return (solidStandNear ? SOLID_STAND_WEIGHT : 0)
                + (supportBelow ? SUPPORT_BELOW_WEIGHT : 0)
                + sides * SIDE_NEIGHBOR_WEIGHT;
    }

    /** Stable: equal scores keep the incoming (nearest / spread) order. */
    public static <T> List<T> orderByEase(List<T> ordered, ToIntFunction<T> score, int maxScored) {
        if (ordered == null || ordered.isEmpty()) {
            return List.of();
        }
        int scoredCount = Math.max(0, Math.min(maxScored, ordered.size()));
        List<Scored<T>> scored = new ArrayList<>(scoredCount);
        for (int i = 0; i < scoredCount; i++) {
            T item = ordered.get(i);
            scored.add(new Scored<>(item, score.applyAsInt(item)));
        }
        scored.sort(Comparator.comparingInt((Scored<T> s) -> s.score).reversed());
        List<T> result = new ArrayList<>(ordered.size());
        for (Scored<T> s : scored) {
            result.add(s.item);
        }
        result.addAll(ordered.subList(scoredCount, ordered.size()));
        return result;
    }

    private record Scored<T>(T item, int score) {
    }
}
