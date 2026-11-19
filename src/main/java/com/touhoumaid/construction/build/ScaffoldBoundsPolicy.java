package com.touhoumaid.construction.build;

/**
 * Keeps scaffolding inside the blueprint footprint plus a small margin, so a maid who is
 * still walking to the site cannot raise a tower wherever she happens to stand.
 *
 * <p>No Minecraft types so JVM unit tests can cover it.
 */
public final class ScaffoldBoundsPolicy {
    /** Normal build range: blueprint footprint expanded by this many blocks on every axis. */
    public static final int MARGIN = 2;
    /** Widened once when every candidate was rejected by {@link #MARGIN}. */
    public static final int RELAXED_MARGIN = 4;

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private ScaffoldBoundsPolicy() {
    }

    public static boolean contains(Box box, int margin, int x, int y, int z) {
        if (box == null) {
            return true;
        }
        return x >= box.minX() - margin && x <= box.maxX() + margin
                && y >= box.minY() - margin && y <= box.maxY() + margin
                && z >= box.minZ() - margin && z <= box.maxZ() + margin;
    }

    public static boolean allInside(Box box, int margin, Iterable<ScaffoldLayout.Cell> cells) {
        if (box == null) {
            return true;
        }
        for (ScaffoldLayout.Cell cell : cells) {
            if (!contains(box, margin, cell.x(), cell.y(), cell.z())) {
                return false;
            }
        }
        return true;
    }

    /** Chebyshev-style shortest distance from a point to the expanded box, 0 when inside. */
    public static int distanceOutside(Box box, int margin, int x, int y, int z) {
        if (box == null) {
            return 0;
        }
        int dx = axisOverflow(x, box.minX() - margin, box.maxX() + margin);
        int dy = axisOverflow(y, box.minY() - margin, box.maxY() + margin);
        int dz = axisOverflow(z, box.minZ() - margin, box.maxZ() + margin);
        return Math.max(dx, Math.max(dy, dz));
    }

    private static int axisOverflow(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }
}
