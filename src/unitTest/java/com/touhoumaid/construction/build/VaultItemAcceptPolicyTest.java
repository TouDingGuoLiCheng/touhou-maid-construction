package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultItemAcceptPolicyTest {
    @Test
    void emptyContainerMayEnterVault() {
        assertTrue(VaultItemAcceptPolicy.mayAcceptIntoVault(false, -1, -1));
        assertTrue(VaultItemAcceptPolicy.mayAcceptIntoVault(false, 0, 0));
        assertTrue(VaultItemAcceptPolicy.mayAcceptIntoVault(false, -1, 0));
    }

    @Test
    void shulkerLikeBlockEntityItemsAreRejected() {
        assertFalse(VaultItemAcceptPolicy.mayAcceptIntoVault(false, -1, 1));
        assertFalse(VaultItemAcceptPolicy.mayAcceptIntoVault(false, 0, 3));
    }

    @Test
    void bundleLikeRootItemsAreRejected() {
        assertFalse(VaultItemAcceptPolicy.mayAcceptIntoVault(false, 1, -1));
    }

    @Test
    void constructionTerminalNeverEntersVault() {
        assertFalse(VaultItemAcceptPolicy.mayAcceptIntoVault(true, -1, -1));
    }
}
