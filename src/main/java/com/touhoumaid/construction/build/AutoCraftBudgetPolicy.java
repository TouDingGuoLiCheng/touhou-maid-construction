package com.touhoumaid.construction.build;

/** Caps expensive auto-craft solves so one uncraftable id cannot stall a whole server tick. */
public final class AutoCraftBudgetPolicy {
    public static final int MAX_ENSURES_PER_TICK = 2;

    private AutoCraftBudgetPolicy() {
    }

    public static boolean canEnsure(int ensuresThisTick) {
        return ensuresThisTick < MAX_ENSURES_PER_TICK;
    }

    public static boolean shouldReset(int budgetTick, int serverTick) {
        return budgetTick != serverTick;
    }
}
