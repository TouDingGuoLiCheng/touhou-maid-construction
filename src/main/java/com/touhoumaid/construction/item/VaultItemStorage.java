package com.touhoumaid.construction.item;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Persist terminal vault slots with {@code int} indices.
 * Vanilla {@link net.minecraft.world.ContainerHelper} stores slot as {@code byte},
 * which wraps for indices &gt;= 256 and corrupts our 2700-slot vault on save/load.
 */
public final class VaultItemStorage {
    public static final String ITEMS_KEY = "Items";

    private VaultItemStorage() {
    }

    public static void saveAllItems(CompoundTag tag, NonNullList<ItemStack> items) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            stack.save(entry);
            list.add(entry);
        }
        tag.put(ITEMS_KEY, list);
    }

    public static void loadAllItems(CompoundTag tag, NonNullList<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (!tag.contains(ITEMS_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = readSlot(entry);
            if (slot < 0 || slot >= items.size()) {
                continue;
            }
            items.set(slot, ItemStack.of(entry));
        }
    }

    private static int readSlot(CompoundTag entry) {
        if (entry.contains("Slot", Tag.TAG_INT)) {
            return entry.getInt("Slot");
        }
        if (entry.contains("Slot", Tag.TAG_BYTE)) {
            return entry.getByte("Slot") & 255;
        }
        return -1;
    }
}
