package com.touhoumaid.construction.build;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-job scaffold column metadata: landing support Y and highest stand Y at each XZ tower.
 * Used for maid column reuse without rescanning the world every tick.
 */
public final class ScaffoldColumnRegistry {
    /** One vertical tower at {@code x,z}: support/landing Y plus highest scaffold block and stand. */
    public record Column(int x, int z, int baseY, int maxBlockY, int maxStandY) {
    }

    private final Map<Long, Column> byXz = new HashMap<>();

    /** Record one scaffold block placed at {@code x,y,z}. {@code baseY} is the landing/support below the tower. */
    public void recordBlock(int x, int y, int z, int baseY) {
        long key = ScaffoldLayout.packXz(x, z);
        Column current = byXz.get(key);
        int landing = current == null ? baseY : Math.min(current.baseY(), baseY);
        int maxBlock = current == null ? y : Math.max(current.maxBlockY(), y);
        byXz.put(key, new Column(x, z, landing, maxBlock, maxBlock + 1));
    }

    public void recordBlock(int x, int y, int z) {
        recordBlock(x, y, z, y - 1);
    }

    /** Rebuild one column from ledger blocks after removal. */
    public void rescanColumn(int x, int z, Set<Long> ledgerBlockPosPacked) {
        long key = ScaffoldLayout.packXz(x, z);
        int minBlockY = Integer.MAX_VALUE;
        int maxBlockY = Integer.MIN_VALUE;
        if (ledgerBlockPosPacked != null) {
            for (long packed : ledgerBlockPosPacked) {
                if (ScaffoldLayout.unpackBlockX(packed) != x || ScaffoldLayout.unpackBlockZ(packed) != z) {
                    continue;
                }
                int blockY = ScaffoldLayout.unpackBlockY(packed);
                minBlockY = Math.min(minBlockY, blockY);
                maxBlockY = Math.max(maxBlockY, blockY);
            }
        }
        if (maxBlockY == Integer.MIN_VALUE) {
            byXz.remove(key);
            return;
        }
        int baseY = minBlockY - 1;
        byXz.put(key, new Column(x, z, baseY, maxBlockY, maxBlockY + 1));
    }

    public Optional<Column> at(int x, int z) {
        return Optional.ofNullable(byXz.get(ScaffoldLayout.packXz(x, z)));
    }

    public int maxStandY(int x, int z) {
        Column column = byXz.get(ScaffoldLayout.packXz(x, z));
        return column == null ? Integer.MIN_VALUE : column.maxStandY();
    }

    public Set<Long> columnXzKeys() {
        return Collections.unmodifiableSet(byXz.keySet());
    }

    public Collection<Column> columns() {
        return Collections.unmodifiableCollection(byXz.values());
    }

    public void clear() {
        byXz.clear();
    }
}
