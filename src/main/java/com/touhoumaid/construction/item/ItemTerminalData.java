package com.touhoumaid.construction.item;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.VaultCounts;
import com.touhoumaid.construction.registry.ModBlockEntities;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read/write construction terminal data: projects stay on the item; vault uses world SavedData. */
public final class ItemTerminalData {
    private ItemTerminalData() {
    }

    public static CompoundTag ensureTag(ItemStack stack) {
        CompoundTag beTag = BlockItem.getBlockEntityData(stack);
        if (beTag == null) {
            beTag = new CompoundTag();
            BlockItem.setBlockEntityData(stack, ModBlockEntities.CONSTRUCTION_TERMINAL.get(), beTag);
            beTag = BlockItem.getBlockEntityData(stack);
        }
        return beTag == null ? new CompoundTag() : beTag;
    }

    public static List<BuildProject> getProjects(ItemStack stack) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        if (tag == null || !tag.contains("Projects", Tag.TAG_LIST)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(BuildProject.loadList(tag.getList("Projects", Tag.TAG_COMPOUND)));
    }

    public static String getSelectedId(ItemStack stack) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        return tag == null ? "" : tag.getString("SelectedProject");
    }

    public static void saveProjects(ItemStack stack, List<BuildProject> projects, String selectedId) {
        CompoundTag tag = ensureTag(stack);
        tag.put("Projects", BuildProject.saveList(projects));
        tag.putString("SelectedProject", selectedId == null ? "" : selectedId);
        BlockItem.setBlockEntityData(stack, ModBlockEntities.CONSTRUCTION_TERMINAL.get(), tag);
    }

    /** Writes vault slots without clearing Projects / SelectedProject on the held terminal item. */
    public static void saveVaultItems(ItemStack stack, NonNullList<ItemStack> items) {
        saveVaultItems(stack, items, null);
    }

    public static void saveVaultItems(
            ItemStack stack, NonNullList<ItemStack> items, @Nullable MinecraftServer server) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        NonNullList<ItemStack> copies = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            ItemStack slot = items.get(i);
            copies.set(i, slot.isEmpty() ? ItemStack.EMPTY : slot.copy());
        }
        CompoundTag blob = new CompoundTag();
        VaultItemStorage.saveAllItems(blob, copies);
        if (server == null) {
            return;
        }
        CompoundTag tag = ensureTag(stack);
        migrateLegacyItems(tag, server);
        String id = PortableVaultPolicy.ensureVaultId(tag.getString(PortableVaultPolicy.VAULT_ID_KEY));
        tag.putString(PortableVaultPolicy.VAULT_ID_KEY, id);
        PortableVaultSavedData.get(server).saveBlob(id, blob);
        if (PortableVaultPolicy.shouldStripItemItems()) {
            tag.remove(VaultItemStorage.ITEMS_KEY);
        }
        BlockItem.setBlockEntityData(stack, ModBlockEntities.CONSTRUCTION_TERMINAL.get(), tag);
        // #region agent log
        {
            com.google.gson.JsonObject dbg = com.touhoumaid.construction.build.VaultFillDebugLog.data();
            dbg.addProperty("stackHash", System.identityHashCode(stack));
            dbg.addProperty("usedWorldStore", server != null);
            com.touhoumaid.construction.build.VaultFillDebugLog.vaultSnapshot(dbg, "vaultWritten", copies);
            com.touhoumaid.construction.build.VaultFillDebugLog.log(
                    "H1", "ItemTerminalData.saveVaultItems", "save_vault", dbg);
        }
        // #endregion
    }

    public static NonNullList<ItemStack> loadVaultItems(ItemStack stack) {
        return loadVaultItems(stack, null);
    }

    public static NonNullList<ItemStack> loadVaultItems(ItemStack stack, @Nullable MinecraftServer server) {
        NonNullList<ItemStack> items = NonNullList.withSize(
                ConstructionTerminalBE.SLOT_COUNT, ItemStack.EMPTY);
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        if (server != null) {
            if (tag == null) {
                tag = ensureTag(stack);
            }
            migrateLegacyItems(tag, server);
            String id = tag.getString(PortableVaultPolicy.VAULT_ID_KEY);
            if (!id.isEmpty()) {
                VaultItemStorage.loadAllItems(PortableVaultSavedData.get(server).loadBlob(id), items);
            }
            BlockItem.setBlockEntityData(stack, ModBlockEntities.CONSTRUCTION_TERMINAL.get(), tag);
            return items;
        }
        if (tag != null) {
            VaultItemStorage.loadAllItems(tag, items);
        }
        return items;
    }

    public static Map<String, Integer> vaultCounts(ItemStack stack) {
        return vaultCounts(stack, null);
    }

    public static Map<String, Integer> vaultCounts(ItemStack stack, @Nullable MinecraftServer server) {
        return VaultCounts.fromList(loadVaultItems(stack, server));
    }

    public static void saveBlockVaultToWorldStore(ConstructionTerminalBE be, MinecraftServer server) {
        if (be == null || server == null) {
            return;
        }
        String id = PortableVaultPolicy.ensureVaultId(be.vaultId());
        be.setVaultId(id);
        CompoundTag blob = new CompoundTag();
        VaultItemStorage.saveAllItems(blob, be.getItems());
        PortableVaultSavedData.get(server).saveBlob(id, blob);
    }

    /** Move a block-entity vault blob into world store and strip {@code Items} for the dropped item tag. */
    public static void persistBlockTagToWorldStore(CompoundTag beTag, MinecraftServer server) {
        if (beTag == null || server == null) {
            return;
        }
        String id = PortableVaultPolicy.ensureVaultId(beTag.getString(PortableVaultPolicy.VAULT_ID_KEY));
        beTag.putString(PortableVaultPolicy.VAULT_ID_KEY, id);
        if (beTag.contains(VaultItemStorage.ITEMS_KEY, Tag.TAG_LIST)) {
            CompoundTag blob = new CompoundTag();
            blob.put(VaultItemStorage.ITEMS_KEY, beTag.getList(VaultItemStorage.ITEMS_KEY, Tag.TAG_COMPOUND).copy());
            PortableVaultSavedData.get(server).saveBlob(id, blob);
        }
        beTag.remove(VaultItemStorage.ITEMS_KEY);
        beTag.putString(PortableVaultPolicy.VAULT_ID_KEY, id);
    }

    @Nullable
    public static CompoundTag stripItemsFromShareTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        CompoundTag copy = tag.copy();
        if (copy.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            copy.getCompound("BlockEntityTag").remove(VaultItemStorage.ITEMS_KEY);
        }
        return copy;
    }

    private static void migrateLegacyItems(CompoundTag beTag, MinecraftServer server) {
        String id = PortableVaultPolicy.ensureVaultId(beTag.getString(PortableVaultPolicy.VAULT_ID_KEY));
        beTag.putString(PortableVaultPolicy.VAULT_ID_KEY, id);
        boolean itemHasItems = beTag.contains(VaultItemStorage.ITEMS_KEY, Tag.TAG_LIST);
        PortableVaultSavedData store = PortableVaultSavedData.get(server);
        if (PortableVaultPolicy.shouldWriteItemItemsToStore(store.hasEntry(id), itemHasItems)) {
            CompoundTag blob = new CompoundTag();
            blob.put(VaultItemStorage.ITEMS_KEY, beTag.getList(VaultItemStorage.ITEMS_KEY, Tag.TAG_COMPOUND).copy());
            store.saveBlob(id, blob);
        }
        if (PortableVaultPolicy.shouldStripItemItems()) {
            beTag.remove(VaultItemStorage.ITEMS_KEY);
        }
    }

    /**
     * Locate the terminal item for a build job: preferred hand, other hand, then inventory.
     * Item-mode material checks must not require the terminal to stay in the bound hand.
     */
    public static Optional<ItemStack> findTerminalStack(
            ServerPlayer player, InteractionHand preferredHand, String projectId) {
        if (player == null) {
            return Optional.empty();
        }
        InteractionHand hand = preferredHand == null ? InteractionHand.MAIN_HAND : preferredHand;
        ItemStack preferred = player.getItemInHand(hand);
        if (ownsProject(preferred, projectId)) {
            return Optional.of(preferred);
        }
        InteractionHand other =
                hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack off = player.getItemInHand(other);
        if (ownsProject(off, projectId)) {
            return Optional.of(off);
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ownsProject(stack, projectId)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private static boolean ownsProject(ItemStack stack, String projectId) {
        if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
            return false;
        }
        if (projectId == null || projectId.isEmpty()) {
            return true;
        }
        return BuildProject.find(getProjects(stack), projectId).isPresent();
    }

}
