package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCraftBudgetPolicyTest {
    @Test
    void allowsTwoEnsuresPerTick() {
        assertEquals(2, AutoCraftBudgetPolicy.MAX_ENSURES_PER_TICK);
        assertTrue(AutoCraftBudgetPolicy.canEnsure(0));
        assertTrue(AutoCraftBudgetPolicy.canEnsure(1));
        assertFalse(AutoCraftBudgetPolicy.canEnsure(2));
    }

    @Test
    void resetsCountWhenServerTickChanges() {
        assertTrue(AutoCraftBudgetPolicy.shouldReset(10, 11));
        assertFalse(AutoCraftBudgetPolicy.shouldReset(10, 10));
    }
}
