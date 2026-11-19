package com.touhoumaid.construction.build;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-cell task claims so multiple maids do not place the same block. */
final class TaskClaimRegistry {
    private final ConcurrentHashMap<Long, UUID> claimedBy = new ConcurrentHashMap<>();

    boolean tryClaim(long packed, UUID maidId) {
        UUID existing = claimedBy.putIfAbsent(packed, maidId);
        return existing == null || existing.equals(maidId);
    }

    boolean isClaimedByOther(long packed, UUID maidId) {
        UUID owner = claimedBy.get(packed);
        return owner != null && !owner.equals(maidId);
    }

    void releaseClaim(long packed, UUID maidId) {
        claimedBy.computeIfPresent(packed, (pos, owner) -> owner.equals(maidId) ? null : owner);
    }

    void releaseAllFor(UUID maidId) {
        if (maidId == null) {
            return;
        }
        claimedBy.entrySet().removeIf(entry -> maidId.equals(entry.getValue()));
    }

    void forceRelease(long packed) {
        claimedBy.remove(packed);
    }

    boolean isClaimed(long packed) {
        return claimedBy.containsKey(packed);
    }

    java.util.List<Long> claimedPositions() {
        return java.util.List.copyOf(claimedBy.keySet());
    }

    UUID owner(long packed) {
        return claimedBy.get(packed);
    }

    int size() {
        return claimedBy.size();
    }

    void clearAll() {
        claimedBy.clear();
    }
}
