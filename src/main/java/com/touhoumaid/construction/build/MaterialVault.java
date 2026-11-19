package com.touhoumaid.construction.build;

import com.touhoumaid.construction.project.MaterialBlockPolicy;
import com.touhoumaid.construction.project.MaterialDisplay;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.project.BuildProject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deduct one schematic block's worth of material from a terminal vault. */
public final class MaterialVault {
    private MaterialVault() {
    }

    public static boolean hasMaterial(NonNullList<ItemStack> items, String blockId) {
        return hasMaterial(asContainer(items), blockId);
    }

    public static boolean tryConsume(NonNullList<ItemStack> items, String blockId) {
        SimpleContainer temp = asContainer(items);
        boolean ok = tryConsume(temp, blockId);
        if (ok) {
            for (int i = 0; i < items.size(); i++) {
                items.set(i, temp.getItem(i));
            }
        }
        return ok;
    }

    private static SimpleContainer asContainer(NonNullList<ItemStack> items) {
        SimpleContainer container = new SimpleContainer(items.size());
        for (int i = 0; i < items.size(); i++) {
            container.setItem(i, items.get(i));
        }
        return container;
    }

    public static boolean hasMaterial(Container vault, String blockId) {
        if (MaterialBlockPolicy.isAutoFree(blockId)) {
            return true;
        }
        for (String itemId : matchItemIds(blockId)) {
            if (countInVault(vault, itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean tryConsume(Container vault, String blockId) {
        if (MaterialBlockPolicy.isAutoFree(blockId)) {
            return true;
        }
        for (String itemId : matchItemIds(blockId)) {
            if (tryConsumeItemId(vault, itemId)) {
                return true;
            }
        }
        return false;
    }

    /** Deposit stacks into a vault, merging where possible. Returns items that did not fit. */
    public static int deposit(Container vault, ItemStack toAdd) {
        if (toAdd.isEmpty()) {
            return 0;
        }
        if (!VaultItemAcceptPolicy.mayAcceptIntoVault(toAdd)) {
            return toAdd.getCount();
        }
        int remaining = toAdd.getCount();
        for (int slot = 0; slot < vault.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameTags(stack, toAdd)) {
                continue;
            }
            int move = Math.min(remaining, stack.getMaxStackSize() - stack.getCount());
            if (move <= 0) {
                continue;
            }
            stack.grow(move);
            remaining -= move;
        }
        for (int slot = 0; slot < vault.getContainerSize() && remaining > 0; slot++) {
            if (!vault.getItem(slot).isEmpty()) {
                continue;
            }
            int move = Math.min(remaining, toAdd.getMaxStackSize());
            ItemStack placed = toAdd.copy();
            placed.setCount(move);
            vault.setItem(slot, placed);
            remaining -= move;
        }
        return remaining;
    }

    public static int countForBlock(Container vault, String blockId) {
        int total = 0;
        for (String itemId : matchItemIds(blockId)) {
            total += countInVault(vault, itemId);
        }
        return total;
    }

    public static int fillFromLines(
            NonNullList<ItemStack> items,
            List<SchematicFiles.MaterialLine> lines,
            BuildProject project) {
        SimpleContainer temp = asContainer(items);
        int filled = fillFromLines(temp, lines, project);
        for (int i = 0; i < items.size(); i++) {
            items.set(i, temp.getItem(i));
        }
        return filled;
    }

    /** Fill missing quantities for a schematic material list (creative helper). */
    public static int fillFromLines(
            Container vault,
            List<SchematicFiles.MaterialLine> lines,
            BuildProject project) {
        int filledTypes = 0;
        if (lines == null || project == null) {
            return 0;
        }
        for (SchematicFiles.MaterialLine line : lines) {
            if (project.isIgnoredBlock(line.blockId())
                    || MaterialBlockPolicy.isAutoFree(line.blockId())) {
                continue;
            }
            Item item = resolveItem(line.blockId());
            if (item == null || item == Items.AIR) {
                continue;
            }
            int target = project.requiredMaterialCount(line.blockId(), line.count());
            int have = countForBlock(vault, line.blockId());
            int need = Math.max(0, target - have);
            if (need <= 0) {
                continue;
            }
            int left = deposit(vault, new ItemStack(item, need));
            int deposited = need - left;
            // #region agent log
            if (need > 0) {
                com.google.gson.JsonObject lineLog = VaultFillDebugLog.data();
                lineLog.addProperty("blockId", line.blockId());
                lineLog.addProperty("resolvedItem", BuiltInRegistries.ITEM.getKey(item).toString());
                lineLog.addProperty("target", target);
                lineLog.addProperty("haveBefore", have);
                lineLog.addProperty("need", need);
                lineLog.addProperty("deposited", deposited);
                lineLog.addProperty("leftOver", left);
                lineLog.addProperty("haveAfter", countForBlock(vault, line.blockId()));
                lineLog.addProperty("matchIds", String.join("|", matchItemIds(line.blockId())));
                VaultFillDebugLog.log("H3-H4", "MaterialVault.fillFromLines", "line_deposit", lineLog);
            }
            // #endregion
            if (left < need) {
                filledTypes++;
            }
        }
        return filledTypes;
    }

    private static Item resolveItem(String blockId) {
        ItemStack stack = MaterialDisplay.iconForBlockId(blockId);
        return stack.isEmpty() ? Items.AIR : stack.getItem();
    }

    public static List<String> matchItemIds(String blockId) {
        Set<String> ids = new LinkedHashSet<>();
        if (blockId == null || blockId.isEmpty()) {
            return List.of();
        }
        String mapped = MaterialDisplay.vaultItemIdForBlockId(blockId);
        if (!mapped.isEmpty()) {
            ids.add(mapped);
        }
        ids.add(blockId);
        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc != null && BuiltInRegistries.BLOCK.containsKey(loc)) {
            Block block = BuiltInRegistries.BLOCK.get(loc);
            Item item = block.asItem();
            if (item != Items.AIR) {
                ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            ids.add(loc.toString());
        }
        ids.addAll(MaterialBlockPolicy.equivalentVaultMatchIds(blockId));
        return new ArrayList<>(ids);
    }

    private static int countInVault(Container vault, String itemId) {
        int total = 0;
        for (int slot = 0; slot < vault.getContainerSize(); slot++) {
            ItemStack stack = vault.getItem(slot);
            if (!stack.isEmpty()
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean tryConsumeItemId(Container vault, String itemId) {
        for (int slot = 0; slot < vault.getContainerSize(); slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    vault.setItem(slot, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }
}
