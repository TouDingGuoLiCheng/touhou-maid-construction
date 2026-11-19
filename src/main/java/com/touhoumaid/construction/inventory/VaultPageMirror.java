package com.touhoumaid.construction.inventory;

/**
 * Maps a material-vault scroll page onto a fixed-size visible window.
 * The GUI only ever binds slots to that window; the full vault stays server-side.
 */
public final class VaultPageMirror {
    private VaultPageMirror() {
    }

    public static int clampPage(int page, int maxPage) {
        int max = Math.max(0, maxPage);
        return Math.max(0, Math.min(page, max));
    }

    public static int vaultIndex(int page, int localIndex, int cols, int visibleRows) {
        int col = localIndex % cols;
        int row = localIndex / cols;
        return (page * visibleRows + row) * cols + col;
    }

    /** Copy one page from {@code vault} into {@code display} (int stand-ins for unit tests). */
    public static void pullPage(int[] vault, int[] display, int page, int cols, int visibleRows) {
        for (int local = 0; local < display.length; local++) {
            display[local] = vault[vaultIndex(page, local, cols, visibleRows)];
        }
    }

    /** Write {@code display} back into one page of {@code vault}. */
    public static void pushPage(int[] vault, int[] display, int page, int cols, int visibleRows) {
        for (int local = 0; local < display.length; local++) {
            vault[vaultIndex(page, local, cols, visibleRows)] = display[local];
        }
    }
}
