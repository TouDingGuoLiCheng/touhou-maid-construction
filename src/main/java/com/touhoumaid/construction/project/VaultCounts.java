package com.touhoumaid.construction.project;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Counts items stored in the construction terminal vault. */
public final class VaultCounts {
    private VaultCounts() {
    }

    public static Map<String, Integer> fromContainer(Container container) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            add(map, container.getItem(i));
        }
        return map;
    }

    public static Map<String, Integer> fromList(NonNullList<ItemStack> items) {
        Map<String, Integer> map = new HashMap<>();
        for (ItemStack stack : items) {
            add(map, stack);
        }
        return map;
    }

    private static void add(Map<String, Integer> map, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        map.merge(id, stack.getCount(), Integer::sum);
    }

    public static int haveForBlockId(Map<String, Integer> vault, String blockId) {
        String itemId = MaterialDisplay.vaultItemIdForBlockId(blockId);
        int total = vault.getOrDefault(itemId, 0);
        for (String alt : MaterialBlockPolicy.equivalentVaultMatchIds(blockId)) {
            String altItem = MaterialDisplay.vaultItemIdForBlockId(alt);
            if (!altItem.isEmpty() && !altItem.equals(itemId)) {
                total += vault.getOrDefault(altItem, 0);
            }
        }
        return total;
    }
}
