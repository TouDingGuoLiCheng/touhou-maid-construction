package com.touhoumaid.construction.build;

import java.util.Optional;

/**
 * Dynamic work stands gated by interaction reach (not mere column height).
 * Work stands must use solid footing — never scaffolding cells.
 */
public final class ScaffoldWorkStandPolicy {
    /** Matches {@link ScaffoldPlanner} / player eye offset used for stand checks. */
    public static final double DEFAULT_EYE_HEIGHT = 1.62D;

    private ScaffoldWorkStandPolicy() {
    }

    /**
     * A construction work stand cannot be inside scaffolding or rest only on scaffolding.
     * Maids may still path through scaffolds; they must not place blueprint blocks from them.
     */
    public static boolean allowsWorkStand(boolean feetIsScaffolding, boolean belowIsScaffolding) {
        return !feetIsScaffolding && !belowIsScaffolding;
    }

    /**
     * Scaffold-column cells are a path, never a construction work stand.
     * Work stands must be solid footing; scaffolds only extend reach to them.
     */
    public static boolean allowsScaffoldColumnWorkStand() {
        return false;
    }

    public static boolean canInteractFromStand(
            ScaffoldLayout.Cell stand, ScaffoldLayout.Cell target, double eyeHeight) {
        if (stand == null || target == null) {
            return false;
        }
        double eyeX = stand.x() + 0.5D;
        double eyeY = stand.y() + eyeHeight;
        double eyeZ = stand.z() + 0.5D;
        return BuildInteractionReach.canReach(
                eyeX,
                eyeY,
                eyeZ,
                target.x(),
                target.y(),
                target.z(),
                BuildInteractionReach.REACH_DISTANCE);
    }

    /**
     * Highest stand on the column that can still interact with {@code target}. Empty when no Y
     * on this tower is usable for placing that block.
     */
    public static Optional<ScaffoldLayout.Cell> pickInteractStandOnColumn(
            ScaffoldColumnRegistry.Column column, ScaffoldLayout.Cell target, double eyeHeight) {
        if (column == null || target == null) {
            return Optional.empty();
        }
        int minStandY = column.baseY() + 1;
        int maxStandY = column.maxStandY();
        if (maxStandY < minStandY) {
            return Optional.empty();
        }
        for (int y = maxStandY; y >= minStandY; y--) {
            ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(column.x(), y, column.z());
            if (canInteractFromStand(stand, target, eyeHeight)) {
                return Optional.of(stand);
            }
        }
        return Optional.empty();
    }
}
