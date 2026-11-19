package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildHeavyNavBudgetPolicyTest {
    @Test
    void heavyNavHasNoPerMaidCooldown() {
        assertTrue(BuildHeavyNavBudgetPolicy.maidCooldownReady(0));
        assertTrue(BuildHeavyNavBudgetPolicy.maidCooldownReady(19));
        assertTrue(BuildHeavyNavBudgetPolicy.maidCooldownReady(20));
    }

    @Test
    void heavyNavHasNoGlobalPerTickCap() {
        assertTrue(BuildHeavyNavBudgetPolicy.canTakeGlobalSlot(0));
        assertTrue(BuildHeavyNavBudgetPolicy.canTakeGlobalSlot(1));
        assertTrue(BuildHeavyNavBudgetPolicy.canTakeGlobalSlot(99));
    }

    @Test
    void mayRunHeavyAlwaysAllows() {
        assertTrue(BuildHeavyNavBudgetPolicy.mayRunHeavy(0, 0));
        assertTrue(BuildHeavyNavBudgetPolicy.mayRunHeavy(0, 5));
        assertTrue(BuildHeavyNavBudgetPolicy.mayRunHeavy(19, 1));
    }

    @Test
    void emptyNeighbourhoodStillSkipsPathProbe() {
        assertFalse(BuildHeavyNavBudgetPolicy.standSearchNeedsPathProbe(false));
        assertTrue(BuildHeavyNavBudgetPolicy.standSearchNeedsPathProbe(true));
    }
}
