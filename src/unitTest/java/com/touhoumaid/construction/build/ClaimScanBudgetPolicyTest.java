package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimScanBudgetPolicyTest {
    @Test
    void surviveBudgetIsSmallConstant() {
        assertTrue(ClaimScanBudgetPolicy.MAX_SURVIVE_CHECKS_PER_CLAIM > 0);
        assertTrue(ClaimScanBudgetPolicy.MAX_SURVIVE_CHECKS_PER_CLAIM <= 64);
    }

    @Test
    void shouldStopSurviveChecksAtBudget() {
        assertFalse(ClaimScanBudgetPolicy.surviveBudgetExhausted(0, 48));
        assertFalse(ClaimScanBudgetPolicy.surviveBudgetExhausted(47, 48));
        assertTrue(ClaimScanBudgetPolicy.surviveBudgetExhausted(48, 48));
    }

    @Test
    void capForClaimOrderKeepsPrefix() {
        List<Integer> all = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(1, 2, 3), ClaimScanBudgetPolicy.capForClaimOrder(all, 3));
        assertEquals(all, ClaimScanBudgetPolicy.capForClaimOrder(all, 10));
    }
}
