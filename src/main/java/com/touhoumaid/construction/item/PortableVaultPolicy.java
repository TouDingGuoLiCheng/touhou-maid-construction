package com.touhoumaid.construction.item;

import java.util.UUID;

/**
 * Rules for parking a portable terminal vault in world storage.
 * The held item keeps {@code VaultId}; {@code Items} must not stay on the stack.
 */
public final class PortableVaultPolicy {
    public static final String VAULT_ID_KEY = "VaultId";

    private PortableVaultPolicy() {
    }

    public static String ensureVaultId(String current) {
        if (current == null || current.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return current;
    }

    /** Copy leftover item {@code Items} into the store only when that vault id has no store entry yet. */
    public static boolean shouldWriteItemItemsToStore(boolean storeHasEntry, boolean itemHasItems) {
        return itemHasItems && !storeHasEntry;
    }

    /** After a server migrate, the item tag must not keep {@code Items}. */
    public static boolean shouldStripItemItems() {
        return true;
    }

    /** Client middle-click: keep embedded {@code Items} only for unmigrated blocks with no {@code VaultId}. */
    public static boolean clientCloneShouldKeepLegacyItems(String vaultId) {
        return vaultId == null || vaultId.isEmpty();
    }
}
