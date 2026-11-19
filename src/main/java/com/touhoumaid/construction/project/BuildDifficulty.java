package com.touhoumaid.construction.project;

import net.minecraft.network.chat.Component;

/** Building difficulty tiers from total non-air block count. */
public enum BuildDifficulty {
    EASY(0, 1000, "gui.touhou_maid_construction.difficulty.easy"),
    MEDIUM(1001, 2500, "gui.touhou_maid_construction.difficulty.medium"),
    HARD(2501, Integer.MAX_VALUE, "gui.touhou_maid_construction.difficulty.hard");

    private final int minBlocks;
    private final int maxBlocks;
    private final String translationKey;

    BuildDifficulty(int minBlocks, int maxBlocks, String translationKey) {
        this.minBlocks = minBlocks;
        this.maxBlocks = maxBlocks;
        this.translationKey = translationKey;
    }

    public static BuildDifficulty fromBlockCount(int totalBlocks) {
        if (totalBlocks <= EASY.maxBlocks) {
            return EASY;
        }
        if (totalBlocks <= MEDIUM.maxBlocks) {
            return MEDIUM;
        }
        return HARD;
    }

    public Component label() {
        return Component.translatable(translationKey);
    }
}
