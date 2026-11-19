package com.touhoumaid.construction.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-job ownership of scaffold column XZ so two maids never raise a tower in the same cell.
 *
 * <p>Claims are refreshed while a maid keeps working her column and expire on their own, so a maid
 * that dies or wanders off does not lock the column forever. Walking on someone else's finished
 * scaffolding is still allowed — only starting a new tower there is not.
 */
public final class ScaffoldColumnClaims {
    public static final long CLAIM_TIMEOUT_TICKS = 200L;

    private record Entry(UUID owner, long touchedAt) {
    }

    private final Map<Long, Entry> byXz = new HashMap<>();

    /** Take or refresh the column at {@code x,z}; false when another maid holds it. */
    public boolean occupy(int x, int z, UUID maid, long gameTime) {
        if (maid == null) {
            return false;
        }
        long key = ScaffoldLayout.packXz(x, z);
        Entry current = byXz.get(key);
        if (current != null && !current.owner().equals(maid) && !isExpired(current, gameTime)) {
            return false;
        }
        byXz.put(key, new Entry(maid, gameTime));
        return true;
    }

    public Set<Long> occupiedByOthers(UUID maid, long gameTime) {
        Set<Long> blocked = new HashSet<>();
        for (Map.Entry<Long, Entry> entry : byXz.entrySet()) {
            Entry claim = entry.getValue();
            if (isExpired(claim, gameTime) || claim.owner().equals(maid)) {
                continue;
            }
            blocked.add(entry.getKey());
        }
        return blocked;
    }

    /**
     * Same as {@link #occupiedByOthers(UUID, long)} but expands each occupied column by
     * {@code spacing} cells in X/Z (Chebyshev distance), so maids cannot raise two towers
     * directly next to each other.
     */
    public Set<Long> occupiedByOthersWithSpacing(UUID maid, long gameTime, int spacing) {
        Set<Long> blocked = new HashSet<>();
        int pad = Math.max(0, spacing);
        for (Map.Entry<Long, Entry> entry : byXz.entrySet()) {
            Entry claim = entry.getValue();
            if (isExpired(claim, gameTime) || claim.owner().equals(maid)) {
                continue;
            }
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            for (int dx = -pad; dx <= pad; dx++) {
                for (int dz = -pad; dz <= pad; dz++) {
                    blocked.add(ScaffoldLayout.packXz(x + dx, z + dz));
                }
            }
        }
        return blocked;
    }

    public void release(UUID maid) {
        if (maid == null) {
            return;
        }
        byXz.values().removeIf(entry -> entry.owner().equals(maid));
    }

    public void clear() {
        byXz.clear();
    }

    private static boolean isExpired(Entry entry, long gameTime) {
        return gameTime - entry.touchedAt() >= CLAIM_TIMEOUT_TICKS;
    }
}
