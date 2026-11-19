package com.touhoumaid.construction.inventory;

/**
 * Material-vault GUI scroll page (0-based). Kept as a plain field so {@code ContainerData}
 * can read/write it without calling back into wrapper methods (which would recurse
 * and crash when opening the vault).
 */
final class VaultScrollState {
    private int page;

    int page() {
        return page;
    }

    void setPage(int page, int maxPage) {
        int max = Math.max(0, maxPage);
        this.page = Math.max(0, Math.min(page, max));
    }

    void scrollBy(int delta, int maxPage) {
        setPage(page + delta, maxPage);
    }
}
