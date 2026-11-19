package com.touhoumaid.construction.build;

/**
 * Auto-craft miss results must survive across ticks while the vault contents are unchanged.
 * Clearing every tick re-solves dirt/etc. and stalls the server for seconds.
 */
public final class AutoCraftMemoPolicy {
    private AutoCraftMemoPolicy() {
    }

    public static boolean shouldClearFailures(
            int previousMemoTick, int nowTick, long previousSignature, long currentSignature) {
        if (previousMemoTick == Integer.MIN_VALUE) {
            return true;
        }
        return previousSignature != currentSignature;
    }
}
