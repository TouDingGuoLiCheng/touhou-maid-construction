package com.touhoumaid.construction.client;

/**
 * Key routing for the vault search {@code EditBox}.
 * A focused search field must not swallow Escape — that closes the GUI.
 */
public final class VaultSearchKeyPolicy {
    private VaultSearchKeyPolicy() {
    }

    /** Escape always goes to the screen (close GUI), even while the search box is focused. */
    public static boolean shouldPassEscapeToScreen(boolean searchFocused, boolean isEscape) {
        return isEscape;
    }

    /** Block inventory-close key (usually E) while typing in search. */
    public static boolean shouldSwallowInventoryKey(boolean searchFocused, boolean isInventoryKey) {
        return searchFocused && isInventoryKey;
    }

    /**
     * Other keys while search is focused stay with the search field
     * (including keys the EditBox itself does not handle).
     */
    public static boolean shouldConsumeFocusedSearchKey(
            boolean searchFocused, boolean isEscape, boolean editBoxHandled) {
        if (!searchFocused || isEscape) {
            return false;
        }
        return true;
    }
}
