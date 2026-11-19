package com.touhoumaid.construction.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Overworld map of portable terminal vaults keyed by {@link PortableVaultPolicy#VAULT_ID_KEY}.
 */
public final class PortableVaultSavedData extends SavedData {
    public static final String FILE_ID = "touhou_maid_construction_portable_vaults";

    private final Map<String, CompoundTag> vaults = new HashMap<>();

    public PortableVaultSavedData() {
    }

    public static PortableVaultSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PortableVaultSavedData::load, PortableVaultSavedData::new, FILE_ID);
    }

    public static PortableVaultSavedData load(CompoundTag tag) {
        PortableVaultSavedData data = new PortableVaultSavedData();
        ListTag list = tag.getList("Vaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            String id = row.getString("Id");
            if (id.isEmpty()) {
                continue;
            }
            data.vaults.put(id, row.getCompound("Data").copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, CompoundTag> entry : vaults.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Id", entry.getKey());
            row.put("Data", entry.getValue().copy());
            list.add(row);
        }
        tag.put("Vaults", list);
        return tag;
    }

    public CompoundTag loadBlob(String vaultId) {
        CompoundTag blob = vaults.get(vaultId);
        return blob == null ? new CompoundTag() : blob.copy();
    }

    public void saveBlob(String vaultId, CompoundTag blob) {
        if (vaultId == null || vaultId.isEmpty()) {
            return;
        }
        vaults.put(vaultId, blob == null ? new CompoundTag() : blob.copy());
        setDirty();
    }

    public boolean hasItems(String vaultId) {
        CompoundTag blob = vaults.get(vaultId);
        if (blob == null || !blob.contains(VaultItemStorage.ITEMS_KEY, Tag.TAG_LIST)) {
            return false;
        }
        return !blob.getList(VaultItemStorage.ITEMS_KEY, Tag.TAG_COMPOUND).isEmpty();
    }

    public boolean hasEntry(String vaultId) {
        return vaultId != null && !vaultId.isEmpty() && vaults.containsKey(vaultId);
    }
}
