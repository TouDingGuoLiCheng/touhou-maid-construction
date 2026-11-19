package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** O(1) lookup: world positions of blocks placed by active construction jobs. */
public final class BuildPlacementIndex {
    private static final Map<Long, BuildJobKey> BY_POS = new ConcurrentHashMap<>();

    private BuildPlacementIndex() {
    }

    public static void track(BuildJobKey key, BlockPos pos) {
        if (key == null || pos == null) {
            return;
        }
        BY_POS.put(pack(pos), key);
    }

    public static void untrack(BlockPos pos) {
        if (pos == null) {
            return;
        }
        BY_POS.remove(pack(pos));
    }

    public static void untrackJob(BuildJobKey key) {
        if (key == null) {
            return;
        }
        BY_POS.entrySet().removeIf(entry -> key.equals(entry.getValue()));
    }

    public static Optional<BuildJobKey> keyAt(BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_POS.get(pack(pos)));
    }

    private static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }
}
