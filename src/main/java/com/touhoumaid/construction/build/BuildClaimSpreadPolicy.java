package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Prefer cells whose work stands are not already taken. Crowded cells stay at the end so a maid
 * still takes them when they are the only place left to build from.
 */
public final class BuildClaimSpreadPolicy {
    private BuildClaimSpreadPolicy() {
    }

    /**
     * After a maid has already placed, keep nearest-first so she finishes the local wall
     * instead of hopping to a far free stand (her current stand looks "crowded" because she is on it).
     */
    public static <T> List<T> orderForClaim(
            List<T> nearestFirst, Predicate<T> hasFreeStand, boolean stayNearLastBlock) {
        if (stayNearLastBlock) {
            return List.copyOf(nearestFirst);
        }
        return freeStandsFirst(nearestFirst, hasFreeStand);
    }

    public static <T> List<T> freeStandsFirst(List<T> nearestFirst, Predicate<T> hasFreeStand) {
        List<T> free = new ArrayList<>();
        List<T> crowded = new ArrayList<>();
        for (T item : nearestFirst) {
            if (hasFreeStand.test(item)) {
                free.add(item);
            } else {
                crowded.add(item);
            }
        }
        List<T> ordered = new ArrayList<>(free.size() + crowded.size());
        ordered.addAll(free);
        ordered.addAll(crowded);
        return ordered;
    }
}
