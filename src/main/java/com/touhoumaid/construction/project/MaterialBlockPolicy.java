package com.touhoumaid.construction.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schematic blocks that need special handling: vault item substitutes (A),
 * automatic ignore (B/C), or free place without vault stock (fluids).
 */
public final class MaterialBlockPolicy {
    /**
     * Dirt and grass block are interchangeable for world-match and vault stock checks:
     * already-present either side counts as satisfied; either item covers the other need.
     */
    private static final Set<String> DIRT_GRASS_SURFACE = Set.of(
            "minecraft:dirt",
            "minecraft:grass_block");

    /** No survival item — accept the substitute in the material vault. */
    private static final Map<String, String> VAULT_ITEM_ALIASES = Map.ofEntries(
            Map.entry("minecraft:cave_vines", "minecraft:glow_berries"),
            Map.entry("minecraft:cave_vines_plant", "minecraft:glow_berries"),
            Map.entry("minecraft:kelp_plant", "minecraft:kelp"),
            Map.entry("minecraft:weeping_vines_plant", "minecraft:weeping_vines"),
            Map.entry("minecraft:twisting_vines_plant", "minecraft:twisting_vines"),
            Map.entry("minecraft:tall_grass", "minecraft:wheat_seeds"),
            Map.entry("minecraft:large_fern", "minecraft:wheat_seeds"),
            Map.entry("minecraft:tall_seagrass", "minecraft:seagrass"));

    /**
     * Fluids: placed during construction without vault stock or material-list demand.
     * Not the same as {@link #AUTO_IGNORED} (those are skipped entirely).
     */
    private static final Set<String> AUTO_FREE = Set.of(
            "minecraft:water",
            "minecraft:flowing_water",
            "minecraft:lava",
            "minecraft:flowing_lava");

    /** No obtainable material — always skipped during construction. */
    private static final Set<String> AUTO_IGNORED = Set.of(
            "minecraft:glow_lichen",
            "minecraft:hanging_roots",
            "minecraft:big_dripleaf_stem",
            "minecraft:fire",
            "minecraft:soul_fire",
            "minecraft:bubble_column",
            "minecraft:piston_head",
            "minecraft:moving_piston");

    private MaterialBlockPolicy() {
    }

    public static String vaultItemIdForBlockId(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return "";
        }
        return VAULT_ITEM_ALIASES.getOrDefault(blockId, "");
    }

    public static boolean isAutoIgnored(String blockId) {
        return blockId != null && AUTO_IGNORED.contains(blockId);
    }

    /** True when the block is placed free (no vault consume / not listed as required material). */
    public static boolean isAutoFree(String blockId) {
        return blockId != null && AUTO_FREE.contains(blockId);
    }

    /** Player must stock the vault (or ignore manually) for this schematic block. */
    public static boolean needsVaultMaterial(String blockId) {
        return blockId != null
                && !blockId.isEmpty()
                && !isAutoIgnored(blockId)
                && !isAutoFree(blockId);
    }

    /** True when both ids are dirt/grass-block (including same-to-same). */
    public static boolean areBuildSurfaceEquivalent(String a, String b) {
        return a != null && b != null
                && DIRT_GRASS_SURFACE.contains(a)
                && DIRT_GRASS_SURFACE.contains(b);
    }

    /** Other vault/block ids accepted when checking stock for this schematic block. */
    public static List<String> equivalentVaultMatchIds(String blockId) {
        if (blockId == null || !DIRT_GRASS_SURFACE.contains(blockId)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(DIRT_GRASS_SURFACE.size() - 1);
        for (String id : DIRT_GRASS_SURFACE) {
            if (!id.equals(blockId)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
