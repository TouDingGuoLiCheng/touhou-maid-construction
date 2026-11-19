package com.touhoumaid.construction.inventory;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultPageMirrorTest {
    private static final int COLS = ConstructionTerminalBE.COLS;
    private static final int VISIBLE_ROWS = ConstructionTerminalBE.VISIBLE_ROWS;
    private static final int VISIBLE = ConstructionTerminalBE.VISIBLE_SLOT_COUNT;

    @Test
    void vaultIndexMapsLocalSlotThroughPageOffset() {
        assertEquals(0, VaultPageMirror.vaultIndex(0, 0, COLS, VISIBLE_ROWS));
        assertEquals(26, VaultPageMirror.vaultIndex(0, 26, COLS, VISIBLE_ROWS));
        // UI page 15 → 0-based page 14; last visible cell
        assertEquals(14 * VISIBLE + 26, VaultPageMirror.vaultIndex(14, 26, COLS, VISIBLE_ROWS));
    }

    @Test
    void pullPageCopiesOnlyTheVisibleWindow() {
        int[] vault = new int[ConstructionTerminalBE.SLOT_COUNT];
        for (int i = 0; i < vault.length; i++) {
            vault[i] = i + 1;
        }
        int[] display = new int[VISIBLE];
        VaultPageMirror.pullPage(vault, display, 14, COLS, VISIBLE_ROWS);

        int[] expected = new int[VISIBLE];
        for (int local = 0; local < VISIBLE; local++) {
            expected[local] = 14 * VISIBLE + local + 1;
        }
        assertArrayEquals(expected, display);
    }

    @Test
    void pushPageWritesDisplayBackWithoutTouchingOtherPages() {
        int[] vault = new int[ConstructionTerminalBE.SLOT_COUNT];
        int[] display = new int[VISIBLE];
        for (int local = 0; local < VISIBLE; local++) {
            display[local] = 1000 + local;
        }
        VaultPageMirror.pushPage(vault, display, 2, COLS, VISIBLE_ROWS);

        for (int i = 0; i < vault.length; i++) {
            int pageStart = 2 * VISIBLE;
            if (i >= pageStart && i < pageStart + VISIBLE) {
                assertEquals(1000 + (i - pageStart), vault[i]);
            } else {
                assertEquals(0, vault[i]);
            }
        }
    }

    @Test
    void clampPageRespectsBounds() {
        assertEquals(0, VaultPageMirror.clampPage(-1, 99));
        assertEquals(50, VaultPageMirror.clampPage(50, 99));
        assertEquals(99, VaultPageMirror.clampPage(200, 99));
    }
}
