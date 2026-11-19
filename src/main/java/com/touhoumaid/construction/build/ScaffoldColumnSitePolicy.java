package com.touhoumaid.construction.build;

import java.util.Optional;

/**
 * Prefer planting a scaffold tower against the building edge. When the vertical shaft at that XZ
 * is blocked (overhang / solid above), nudge one block outward away from the target and retry.
 */
public final class ScaffoldColumnSitePolicy {
    @FunctionalInterface
    public interface ShaftBlocked {
        boolean test(int x, int y, int z);
    }

    public static final int DEFAULT_MAX_OUTWARD = 8;

    private ScaffoldColumnSitePolicy() {
    }

    /**
     * @param preferredX/Z first column site (typically the solid work-stand XZ beside the target)
     * @param targetX/Z    blueprint cell — outward steps move away from this XZ
     * @param baseY        inclusive bottom of the climb shaft (usually near ground / maid)
     * @param topY         inclusive top (work-stand height)
     */
    public static Optional<ScaffoldLayout.Cell> pick(
            int preferredX,
            int preferredY,
            int preferredZ,
            int targetX,
            int targetZ,
            int baseY,
            int topY,
            ShaftBlocked blocked,
            int maxOutward) {
        if (blocked == null || maxOutward < 0) {
            return Optional.empty();
        }
        int x = preferredX;
        int z = preferredZ;
        int limit = Math.max(0, maxOutward);
        for (int step = 0; step <= limit; step++) {
            if (shaftClear(x, z, baseY, topY, blocked)) {
                return Optional.of(new ScaffoldLayout.Cell(x, preferredY, z));
            }
            int[] next = stepOutward(x, z, targetX, targetZ);
            if (next[0] == x && next[1] == z) {
                return Optional.empty();
            }
            x = next[0];
            z = next[1];
        }
        return Optional.empty();
    }

    /** True when every cell from {@code baseY} through {@code topY} is free of blockers. */
    public static boolean shaftClear(int x, int z, int baseY, int topY, ShaftBlocked blocked) {
        if (blocked == null) {
            return false;
        }
        int lo = Math.min(baseY, topY);
        int hi = Math.max(baseY, topY);
        for (int y = lo; y <= hi; y++) {
            if (blocked.test(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * One axis-aligned step away from {@code target}. When already on the target XZ, default to +X.
     */
    static int[] stepOutward(int x, int z, int targetX, int targetZ) {
        int dx = Integer.signum(x - targetX);
        int dz = Integer.signum(z - targetZ);
        if (dx == 0 && dz == 0) {
            return new int[]{x + 1, z};
        }
        if (Math.abs(x - targetX) >= Math.abs(z - targetZ)) {
            return new int[]{x + (dx == 0 ? 1 : dx), z};
        }
        return new int[]{x, z + (dz == 0 ? 1 : dz)};
    }
}
