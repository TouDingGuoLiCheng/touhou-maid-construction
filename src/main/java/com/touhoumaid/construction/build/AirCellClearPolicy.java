package com.touhoumaid.construction.build;

import java.util.Set;

/**
 * Schematic air is not a build task and is not bulk-harvested.
 * Surface comes out only when it blocks a maid ({@link CoverTerrainClear}).
 */
public final class AirCellClearPolicy {
    private static final Set<String> SCHEMATIC_AIR = Set.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air");

    private AirCellClearPolicy() {
    }

    public static boolean plannedIsAir(String plannedBlockId) {
        return plannedBlockId != null && SCHEMATIC_AIR.contains(plannedBlockId);
    }

    /**
     * Footprint scans do not harvest grass, stone, or anything else on air cells.
     * Path, stand, and body obstructions are decided by {@link CoverTerrainClearPolicy}.
     */
    public static boolean shouldClear(String plannedBlockId, String worldBlockId) {
        if (plannedBlockId == null || worldBlockId == null) {
            return false;
        }
        return false;
    }
}
