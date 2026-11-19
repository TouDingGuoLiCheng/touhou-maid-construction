package com.touhoumaid.construction.build;

import com.touhoumaid.construction.item.ConstructionTerminalItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Rules for what may enter the construction terminal vault.
 * Nested inventories (shulker BlockEntityTag.Items, bundle Items, …) are rejected so
 * deposit paths that strip NBT cannot silently empty containers.
 */
public final class VaultItemAcceptPolicy {
    private VaultItemAcceptPolicy() {
    }

    /**
     * @param rootItemsCount size of root {@code Items} list, or {@code -1} if absent
     * @param blockEntityItemsCount size of {@code BlockEntityTag.Items}, or {@code -1} if absent
     */
    public static boolean mayAcceptIntoVault(
            boolean isTerminalItem, int rootItemsCount, int blockEntityItemsCount) {
        if (isTerminalItem) {
            return false;
        }
        return !hasNestedItemContents(rootItemsCount, blockEntityItemsCount);
    }

    public static boolean hasNestedItemContents(int rootItemsCount, int blockEntityItemsCount) {
        return rootItemsCount > 0 || blockEntityItemsCount > 0;
    }

    public static boolean mayAcceptIntoVault(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean terminal = stack.getItem() instanceof ConstructionTerminalItem;
        CompoundTag tag = stack.getTag();
        return mayAcceptIntoVault(terminal, listSize(tag, "Items"), blockEntityItemsSize(tag));
    }

    private static int blockEntityItemsSize(@Nullable CompoundTag itemTag) {
        if (itemTag == null || !itemTag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return -1;
        }
        return listSize(itemTag.getCompound("BlockEntityTag"), "Items");
    }

    private static int listSize(@Nullable CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
            return -1;
        }
        return tag.getList(key, Tag.TAG_COMPOUND).size();
    }
}
