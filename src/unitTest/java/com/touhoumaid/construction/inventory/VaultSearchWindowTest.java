package com.touhoumaid.construction.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultSearchWindowTest {
    private static final int VISIBLE = 27;

    @Test
    void collectMatchesSkipsEmptyAndNonMatchingSlots() {
        int[] vault = new int[8];
        vault[0] = 1;
        vault[1] = 0;
        vault[2] = 2;
        vault[3] = 1;
        vault[5] = 3;
        int[] matches = VaultSearchWindow.collectMatches(vault, id -> id == 1);
        assertArrayEquals(new int[] {0, 3}, matches);
    }

    @Test
    void mappedVaultIndexReturnsPaddingAsMinusOne() {
        int[] matches = {10, 20, 30};
        assertEquals(10, VaultSearchWindow.mappedVaultIndex(matches, 0, 0, VISIBLE));
        assertEquals(30, VaultSearchWindow.mappedVaultIndex(matches, 0, 2, VISIBLE));
        assertEquals(-1, VaultSearchWindow.mappedVaultIndex(matches, 0, 3, VISIBLE));
        assertEquals(-1, VaultSearchWindow.mappedVaultIndex(matches, 1, 0, VISIBLE));
    }

    @Test
    void emptyMatchesYieldOneEmptyPage() {
        assertEquals(1, VaultSearchWindow.filterPageCount(0, VISIBLE));
        assertEquals(0, VaultSearchWindow.maxPage(0, VISIBLE));
        assertEquals(1, VaultSearchWindow.filterPageCount(1, VISIBLE));
        assertEquals(2, VaultSearchWindow.filterPageCount(28, VISIBLE));
        assertEquals(1, VaultSearchWindow.maxPage(28, VISIBLE));
    }

    @Test
    void pullPageCopiesOnlyMappedSlots() {
        int[] vault = new int[50];
        for (int i = 0; i < vault.length; i++) {
            vault[i] = i + 1;
        }
        int[] matches = {5, 7, 9};
        int[] display = new int[VISIBLE];
        VaultSearchWindow.pullPage(vault, display, matches, 0);
        assertEquals(6, display[0]);
        assertEquals(8, display[1]);
        assertEquals(10, display[2]);
        assertEquals(0, display[3]);
        assertEquals(0, display[26]);
    }

    @Test
    void filterThenRestoreLeavesUnmatchedVaultCellsUntouched() {
        int[] vault = new int[50];
        for (int i = 0; i < vault.length; i++) {
            vault[i] = 100 + i;
        }
        int[] original = vault.clone();
        int[] matches = VaultSearchWindow.collectMatches(vault, id -> id == 102 || id == 108);
        int[] display = new int[VISIBLE];
        VaultSearchWindow.pullPage(vault, display, matches, 0);
        VaultSearchWindow.pushPage(vault, display, matches, 0);
        assertArrayEquals(original, vault);
    }

    @Test
    void pushPageDoesNotOverwriteUnmatchedOrPaddingSlots() {
        int[] vault = new int[50];
        for (int i = 0; i < vault.length; i++) {
            vault[i] = 100 + i;
        }
        int[] matches = {2, 8};
        int[] display = new int[VISIBLE];
        display[0] = 7;
        display[1] = 9;
        display[2] = 999;
        VaultSearchWindow.pushPage(vault, display, matches, 0);

        assertEquals(7, vault[2]);
        assertEquals(9, vault[8]);
        assertEquals(103, vault[3]);
        assertEquals(100, vault[0]);
        for (int i = 0; i < vault.length; i++) {
            if (i != 2 && i != 8) {
                assertEquals(100 + i, vault[i]);
            }
        }
    }
}
