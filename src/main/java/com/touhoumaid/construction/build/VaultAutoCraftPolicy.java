package com.touhoumaid.construction.build;

/** Pure rules for vault auto-craft deposit and stock-index unavailable marking. */
public final class VaultAutoCraftPolicy {
    private VaultAutoCraftPolicy() {
    }

    /**
     * Auto-craft allows output overflow: success if at least one crafted item entered the vault.
     */
    public static boolean depositOkAllowingOverflow(int resultCount, int leftOver) {
        int produced = Math.max(0, resultCount);
        int left = Math.max(0, leftOver);
        return produced - left > 0;
    }
}
