package com.touhoumaid.construction.build;

import com.touhoumaid.construction.project.MaterialBlockPolicy;

/**
 * World vs schematic: same block type is already built. Neighbor-derived
 * properties and dry-vs-waterlogged do not count as missing.
 */
public final class BuildCellSatisfiedPolicy {
    private BuildCellSatisfiedPolicy() {
    }

    public static boolean alreadySatisfied(String expectedBlockId, String actualBlockId) {
        if (isBlankOrAir(expectedBlockId) || isBlankOrAir(actualBlockId)) {
            return false;
        }
        if (expectedBlockId.equals(actualBlockId)) {
            return true;
        }
        return MaterialBlockPolicy.areBuildSurfaceEquivalent(expectedBlockId, actualBlockId);
    }

    /** Claim only cells the world has not already satisfied. */
    public static boolean shouldClaim(boolean worldAlreadySatisfied) {
        return !worldAlreadySatisfied;
    }

    private static boolean isBlankOrAir(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return true;
        }
        return AirCellClearPolicy.plannedIsAir(blockId);
    }
}
