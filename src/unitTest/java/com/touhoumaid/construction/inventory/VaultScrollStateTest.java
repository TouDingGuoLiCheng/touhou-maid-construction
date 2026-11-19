package com.touhoumaid.construction.inventory;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class VaultScrollStateTest {
    @Test
    void startsAtZero() {
        assertEquals(0, new VaultScrollState().page());
    }

    @Test
    void clampsToMaxPage() {
        VaultScrollState state = new VaultScrollState();
        int maxPage = ConstructionTerminalBE.PAGES - 1;
        state.setPage(100, maxPage);
        assertEquals(maxPage, state.page());
        state.setPage(-3, maxPage);
        assertEquals(0, state.page());
    }

    @Test
    void scrollByStaysWithinBoundsWithoutRecursing() {
        VaultScrollState state = new VaultScrollState();
        int maxPage = ConstructionTerminalBE.PAGES - 1;
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100_000; i++) {
                state.scrollBy(1, maxPage);
            }
        });
        assertEquals(maxPage, state.page());
    }

    @Test
    void supportsHundredPages() {
        VaultScrollState state = new VaultScrollState();
        int maxPage = ConstructionTerminalBE.PAGES - 1;
        state.setPage(50, maxPage);
        assertEquals(50, state.page());
        state.setPage(200, maxPage);
        assertEquals(maxPage, state.page());
    }
}
