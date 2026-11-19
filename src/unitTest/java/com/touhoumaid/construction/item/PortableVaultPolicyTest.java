package com.touhoumaid.construction.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableVaultPolicyTest {
    @Test
    void emptyVaultIdGetsANewOne() {
        String id = PortableVaultPolicy.ensureVaultId("");
        assertFalse(id.isEmpty());
        assertEquals(id, PortableVaultPolicy.ensureVaultId(id));
        assertNotEquals(id, PortableVaultPolicy.ensureVaultId(""));
    }

    @Test
    void leftoverItemItemsFillEmptyStoreThenAlwaysStrip() {
        assertTrue(PortableVaultPolicy.shouldWriteItemItemsToStore(false, true));
        assertFalse(PortableVaultPolicy.shouldWriteItemItemsToStore(true, true));
        assertFalse(PortableVaultPolicy.shouldWriteItemItemsToStore(false, false));
        assertTrue(PortableVaultPolicy.shouldStripItemItems());
    }

    @Test
    void emptyStoreEntryIsStillAnEntryAndMustNotBeRefilledFromItem() {
        assertFalse(PortableVaultPolicy.shouldWriteItemItemsToStore(true, true));
    }

    @Test
    void clientCloneKeepsLegacyItemsOnlyWhenThereIsNoVaultId() {
        assertTrue(PortableVaultPolicy.clientCloneShouldKeepLegacyItems(""));
        assertFalse(PortableVaultPolicy.clientCloneShouldKeepLegacyItems("already-bound"));
    }
}
