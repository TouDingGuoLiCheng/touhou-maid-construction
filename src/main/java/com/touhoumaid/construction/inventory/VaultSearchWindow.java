package com.touhoumaid.construction.inventory;

import java.util.function.IntPredicate;

/**
 * Maps a filtered list of real vault indices onto the 27-slot GUI window.
 * Padding cells have no real index (-1) and must not be written back.
 */
public final class VaultSearchWindow {
    private VaultSearchWindow() {
    }

    public static int[] collectMatches(int[] vault, IntPredicate itemMatches) {
        int count = 0;
        for (int i = 0; i < vault.length; i++) {
            if (vault[i] != 0 && itemMatches.test(vault[i])) {
                count++;
            }
        }
        int[] matches = new int[count];
        int n = 0;
        for (int i = 0; i < vault.length; i++) {
            if (vault[i] != 0 && itemMatches.test(vault[i])) {
                matches[n++] = i;
            }
        }
        return matches;
    }

    public static int mappedVaultIndex(int[] matchIndices, int page, int local, int visibleCount) {
        if (matchIndices == null || visibleCount <= 0) {
            return -1;
        }
        int i = page * visibleCount + local;
        if (i < 0 || i >= matchIndices.length) {
            return -1;
        }
        return matchIndices[i];
    }

    public static int filterPageCount(int matchCount, int visibleCount) {
        if (visibleCount <= 0 || matchCount <= 0) {
            return 1;
        }
        return (matchCount + visibleCount - 1) / visibleCount;
    }

    public static int maxPage(int matchCount, int visibleCount) {
        return Math.max(0, filterPageCount(matchCount, visibleCount) - 1);
    }

    public static void pullPage(int[] vault, int[] display, int[] matchIndices, int page) {
        for (int local = 0; local < display.length; local++) {
            int vaultIndex = mappedVaultIndex(matchIndices, page, local, display.length);
            display[local] = vaultIndex < 0 ? 0 : vault[vaultIndex];
        }
    }

    public static void pushPage(int[] vault, int[] display, int[] matchIndices, int page) {
        for (int local = 0; local < display.length; local++) {
            int vaultIndex = mappedVaultIndex(matchIndices, page, local, display.length);
            if (vaultIndex >= 0) {
                vault[vaultIndex] = display[local];
            }
        }
    }
}
