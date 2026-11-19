package com.touhoumaid.construction.build;

/**
 * Whether a maid is really stranded in the air above a scaffold column.
 *
 * <p>Any footing — scaffolding or a solid block such as the wall she just built — means she is
 * standing legally and must never be pulled back onto the tower.
 */
public final class ScaffoldFloatingPolicy {
    private ScaffoldFloatingPolicy() {
    }

    public static boolean isFloating(
            boolean scaffoldSupport,
            boolean solidFloorBelow,
            boolean hasColumnNear,
            int feetY,
            int nearestColumnTopY) {
        if (scaffoldSupport || solidFloorBelow) {
            return false;
        }
        if (!hasColumnNear) {
            return false;
        }
        return feetY > nearestColumnTopY + 1;
    }
}
