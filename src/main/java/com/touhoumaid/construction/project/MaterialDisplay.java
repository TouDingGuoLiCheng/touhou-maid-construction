package com.touhoumaid.construction.project;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolve block ids from schematics to player-facing names and item icons.
 * Many blocks (potted plants, fluids, etc.) have no {@link Block#asItem()} — fall back gracefully.
 */
public final class MaterialDisplay {
    private MaterialDisplay() {
    }

    public static Component nameForBlockId(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return Component.literal(blockId);
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName();
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDescription();
        }
        return Component.literal(blockId);
    }

    public static ItemStack iconForBlockId(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            Item item = block.asItem();
            if (item != Items.AIR) {
                return item.getDefaultInstance();
            }
            String path = id.getPath();
            if (path.startsWith("potted_")) {
                ResourceLocation inner = new ResourceLocation(id.getNamespace(), path.substring("potted_".length()));
                if (BuiltInRegistries.BLOCK.containsKey(inner)) {
                    Item innerItem = BuiltInRegistries.BLOCK.get(inner).asItem();
                    if (innerItem != Items.AIR) {
                        return innerItem.getDefaultInstance();
                    }
                }
                return Items.FLOWER_POT.getDefaultInstance();
            }
            if (path.endsWith("_wall_banner") || path.endsWith("_wall_head") || path.endsWith("_wall_sign")) {
                String upright = path.replace("_wall_", "_");
                ResourceLocation uprightId = new ResourceLocation(id.getNamespace(), upright);
                if (BuiltInRegistries.ITEM.containsKey(uprightId)) {
                    return BuiltInRegistries.ITEM.get(uprightId).getDefaultInstance();
                }
            }
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return BuiltInRegistries.ITEM.get(id).getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }

    /** Player-facing sample list for missing registry ids (blocks first, then entities). */
    public static String summarizeMissingSample(List<String> blockIds, List<String> entityIds, int maxNames) {
        List<String> parts = new ArrayList<>();
        if (blockIds != null) {
            for (String id : blockIds) {
                if (parts.size() >= maxNames) {
                    break;
                }
                parts.add(nameForBlockId(id).getString());
            }
        }
        if (entityIds != null) {
            for (String id : entityIds) {
                if (parts.size() >= maxNames) {
                    break;
                }
                parts.add(nameForEntityId(id).getString());
            }
        }
        if (parts.isEmpty()) {
            return "-";
        }
        String joined = String.join("\u3001", parts);
        int total = (blockIds == null ? 0 : blockIds.size()) + (entityIds == null ? 0 : entityIds.size());
        if (total > parts.size()) {
            joined += "\u2026";
        }
        return joined;
    }

    public static Component nameForEntityId(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription();
        }
        return Component.literal(entityId);
    }

    /** Item id used when matching vault stock for a schematic block id. */
    public static String vaultItemIdForBlockId(String blockId) {
        ItemStack icon = iconForBlockId(blockId);
        if (!icon.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(icon.getItem()).toString();
        }
        return blockId;
    }
}
