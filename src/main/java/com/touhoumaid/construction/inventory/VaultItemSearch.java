package com.touhoumaid.construction.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Name + registry-id substring match for the material-vault search box.
 * Empty query is "no filter" at the menu layer and does not match here.
 */
public final class VaultItemSearch {
    private VaultItemSearch() {
    }

    public static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(String displayName, String itemId, String query) {
        String q = normalizeQuery(query);
        if (q.isEmpty()) {
            return false;
        }
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String id = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        return name.contains(q) || id.contains(q);
    }

    public static boolean matches(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key == null ? "" : key.toString();
        return matches(stack.getHoverName().getString(), id, query);
    }
}
