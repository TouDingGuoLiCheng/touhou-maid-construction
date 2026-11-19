package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Sparse tower + arm layout. No Minecraft types so JVM unit tests can cover it. */
public final class ScaffoldLayout {
    public record Cell(int x, int y, int z) {
    }

    private ScaffoldLayout() {
    }

    public static long packXz(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static long packCell(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | (z & 0x3FFFFFFL);
    }

    public static int unpackBlockX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackBlockY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    public static int unpackBlockZ(long packed) {
        return (int) (packed << 38 >> 38);
    }

    /** Reuse a column only when this maid is already standing in it (same XZ). Adjacent is a new tower. */
    public static final int MAID_COLUMN_REUSE_RANGE = 0;

    /**
     * Prefer a column this maid is already standing at; do not pull every maid onto the first
     * tower when none exists yet.
     */
    public static int[] pickColumn(int feetX, int feetZ, Set<Long> existingXz, int maxArm) {
        return pickColumn(feetX, feetZ, existingXz, maxArm, feetX, feetZ);
    }

    public static int[] pickColumn(
            int feetX, int feetZ, Set<Long> existingXz, int maxArm, int maidX, int maidZ) {
        return pickColumn(feetX, feetZ, existingXz, maxArm, maidX, maidZ, Set.of());
    }

    /** Rejects tower XZ outside the allowed build range. */
    @FunctionalInterface
    public interface ColumnFilter {
        boolean allows(int x, int z);

        ColumnFilter ANY = (x, z) -> true;
    }

    /**
     * @param blockedXz columns another maid is currently raising — never start a tower there
     */
    public static int[] pickColumn(
            int feetX,
            int feetZ,
            Set<Long> existingXz,
            int maxArm,
            int maidX,
            int maidZ,
            Set<Long> blockedXz) {
        return pickColumn(feetX, feetZ, existingXz, maxArm, maidX, maidZ, blockedXz, ColumnFilter.ANY);
    }

    /**
     * @param blockedXz columns another maid is currently raising — never start a tower there
     * @param allowed   build-range filter; disallowed XZ are treated like blocked columns
     */
    public static int[] pickColumn(
            int feetX,
            int feetZ,
            Set<Long> existingXz,
            int maxArm,
            int maidX,
            int maidZ,
            Set<Long> blockedXz,
            ColumnFilter allowed) {
        int bestX = feetX;
        int bestZ = feetZ;
        int bestD = Integer.MAX_VALUE;
        boolean foundAtMaid = false;
        for (long packed : existingXz) {
            int x = (int) (packed >> 32);
            int z = (int) packed;
            if (isBlocked(x, z, blockedXz, allowed)) {
                continue;
            }
            int dMaid = Math.abs(x - maidX) + Math.abs(z - maidZ);
            int dFeet = Math.abs(x - feetX) + Math.abs(z - feetZ);
            if (dMaid <= MAID_COLUMN_REUSE_RANGE && dFeet <= maxArm && dMaid < bestD) {
                bestD = dMaid;
                bestX = x;
                bestZ = z;
                foundAtMaid = true;
            }
        }
        if (foundAtMaid) {
            return new int[]{bestX, bestZ};
        }
        int dMaidToWork = Math.abs(maidX - feetX) + Math.abs(maidZ - feetZ);
        if (dMaidToWork <= maxArm && !isBlocked(maidX, maidZ, blockedXz, allowed)) {
            return new int[]{maidX, maidZ};
        }
        if (!isBlocked(feetX, feetZ, blockedXz, allowed)) {
            return new int[]{feetX, feetZ};
        }
        int[] aside = nearestFreeColumn(feetX, feetZ, maxArm, blockedXz, allowed);
        if (aside != null) {
            return aside;
        }
        return new int[]{feetX, feetZ};
    }

    private static boolean isBlocked(int x, int z, Set<Long> blockedXz, ColumnFilter allowed) {
        return isColumnBlocked(x, z, blockedXz, allowed);
    }

    /** True when a tower must not start at this XZ (another maid or out of build range). */
    public static boolean isColumnBlocked(int x, int z, Set<Long> blockedXz, ColumnFilter allowed) {
        return blockedXz.contains(packXz(x, z)) || !allowed.allows(x, z);
    }

    private static int[] nearestFreeColumn(
            int feetX, int feetZ, int maxArm, Set<Long> blockedXz, ColumnFilter allowed) {
        for (int r = 1; r <= maxArm; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int rest = r - Math.abs(dx);
                for (int sign = 1; sign >= -1; sign -= 2) {
                    int dz = rest * sign;
                    int x = feetX + dx;
                    int z = feetZ + dz;
                    if (!isBlocked(x, z, blockedXz, allowed)) {
                        return new int[]{x, z};
                    }
                    if (rest == 0) {
                        break;
                    }
                }
            }
        }
        return null;
    }

    /** Vertical cells from supportY+1 through feetY only (no horizontal arm). */
    public static List<Cell> columnOnly(int supportY, int colX, int colZ, int feetY) {
        List<Cell> cells = new ArrayList<>();
        for (int y = supportY + 1; y <= feetY; y++) {
            cells.add(new Cell(colX, y, colZ));
        }
        return cells;
    }

    /** Vertical cells from supportY+1 through feetY, then Manhattan arm at feetY. */
    public static List<Cell> columnThenArm(int supportY, int colX, int colZ, int feetX, int feetY, int feetZ) {
        List<Cell> cells = new ArrayList<>();
        for (int y = supportY + 1; y <= feetY; y++) {
            cells.add(new Cell(colX, y, colZ));
        }
        int x = colX;
        int z = colZ;
        while (x != feetX) {
            x += Integer.signum(feetX - x);
            cells.add(new Cell(x, feetY, z));
        }
        while (z != feetZ) {
            z += Integer.signum(feetZ - z);
            cells.add(new Cell(x, feetY, z));
        }
        return cells;
    }
}
