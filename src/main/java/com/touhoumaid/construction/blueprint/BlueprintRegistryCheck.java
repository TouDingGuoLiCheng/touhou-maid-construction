package com.touhoumaid.construction.blueprint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Checks blueprint palette / entity ids against current registries (skip missing later).
 */
public final class BlueprintRegistryCheck {
    private BlueprintRegistryCheck() {
    }

    public static BlueprintLoadReport check(Blueprint blueprint) {
        BlueprintLoadReport report = new BlueprintLoadReport();
        for (BlueprintRegion region : blueprint.regions().values()) {
            for (RawBlockState state : region.palette()) {
                ResourceLocation id = ResourceLocation.tryParse(state.name());
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                    report.addMissingBlock(state.name());
                }
            }
            collectEntityIds(region.entities(), report);
            // Tile entity block ids are implied by palette; entity-like ids in TE are rare.
        }
        if (blueprint.countedNonAirBlocks() != blueprint.totalBlocksMeta() && blueprint.totalBlocksMeta() > 0) {
            report.addNote("Counted non-air blocks (" + blueprint.countedNonAirBlocks()
                    + ") differs from Metadata.TotalBlocks (" + blueprint.totalBlocksMeta() + ")");
        }
        return report;
    }

    private static void collectEntityIds(ListTag entities, BlueprintLoadReport report) {
        for (Tag tag : entities) {
            if (!(tag instanceof CompoundTag compound)) {
                continue;
            }
            String id = compound.getString("id");
            if (id.isEmpty()) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                report.addMissingEntityType(id);
            }
        }
    }
}
