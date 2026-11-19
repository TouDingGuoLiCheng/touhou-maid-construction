package com.touhoumaid.construction.build;

/**
 * Cover-terrain obstruction rules with no Minecraft types so JVM tests can cover them.
 * Blueprint AABB uses exclusive max (same as {@code origin + size}).
 */
public final class CoverTerrainClearPolicy {
    public static final int MAX_CLEARS_PER_TICK = 4;

    private CoverTerrainClearPolicy() {
    }

    public static int occupancyHeadY(int feetY) {
        return feetY + 1;
    }

    public static int corridorHeadroomY(int hitY) {
        return hitY + 1;
    }

    public static boolean inExclusiveAabb(
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            int x, int y, int z) {
        return x >= minX && x < maxX
                && y >= minY && y < maxY
                && z >= minZ && z < maxZ;
    }

    /**
     * @param coverTerrain     project cover-terrain mode
     * @param inBounds         cell inside the blueprint AABB
     * @param scaffold         walkable scaffold (vanilla or maid)
     * @param matchesPlanned   already the schematic block / completed placement
     * @param terrainCoverable {@code PlacementRules.isTerrainCoverable}
     */
    public static boolean mayClear(
            boolean coverTerrain,
            boolean inBounds,
            boolean scaffold,
            boolean matchesPlanned,
            boolean terrainCoverable) {
        return mayClear(coverTerrain, false, inBounds, scaffold, matchesPlanned, terrainCoverable, false);
    }

    /**
     * @param ignoreOverlaps   project ignore-overlaps mode (any destroyable solid)
     * @param destroyable      not bedrock / other unbreakable ({@code destroyTime >= 0})
     */
    public static boolean mayClear(
            boolean coverTerrain,
            boolean ignoreOverlaps,
            boolean inBounds,
            boolean scaffold,
            boolean matchesPlanned,
            boolean terrainCoverable,
            boolean destroyable) {
        if (!inBounds || scaffold || matchesPlanned) {
            return false;
        }
        if (coverTerrain && terrainCoverable) {
            return true;
        }
        return ignoreOverlaps && destroyable;
    }

    public static int remainingClears(int alreadyThisTick) {
        return Math.max(0, MAX_CLEARS_PER_TICK - alreadyThisTick);
    }
}
