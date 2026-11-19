package com.touhoumaid.construction.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultSearchKeyPolicyTest {

    @Test
    void escapeWhileFocusedPassesToScreenSoGuiCanClose() {
        assertTrue(VaultSearchKeyPolicy.shouldPassEscapeToScreen(true, true));
    }

    @Test
    void escapeWhenNotFocusedStillPassesToScreen() {
        assertTrue(VaultSearchKeyPolicy.shouldPassEscapeToScreen(false, true));
    }

    @Test
    void nonEscapeWhileFocusedDoesNotForcePassToScreen() {
        assertFalse(VaultSearchKeyPolicy.shouldPassEscapeToScreen(true, false));
    }

    @Test
    void inventoryKeyWhileFocusedIsSwallowed() {
        assertTrue(VaultSearchKeyPolicy.shouldSwallowInventoryKey(true, true));
        assertFalse(VaultSearchKeyPolicy.shouldSwallowInventoryKey(true, false));
        assertFalse(VaultSearchKeyPolicy.shouldSwallowInventoryKey(false, true));
    }

    @Test
    void otherKeysWhileFocusedAreConsumedEvenIfEditBoxDidNotHandle() {
        assertTrue(VaultSearchKeyPolicy.shouldConsumeFocusedSearchKey(true, false, false));
        assertFalse(VaultSearchKeyPolicy.shouldConsumeFocusedSearchKey(true, true, false));
        assertFalse(VaultSearchKeyPolicy.shouldConsumeFocusedSearchKey(false, false, true));
    }
}
