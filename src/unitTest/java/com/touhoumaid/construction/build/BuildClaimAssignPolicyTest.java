package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildClaimAssignPolicyTest {
    @Test
    void resetsAttemptsWhenNoPreviousHeldTask() {
        assertTrue(BuildClaimAssignPolicy.shouldResetAttemptCounters(null, "1,2,3"));
    }

    @Test
    void resetsAttemptsWhenClaimingADifferentCell() {
        assertTrue(BuildClaimAssignPolicy.shouldResetAttemptCounters("1,2,3", "4,5,6"));
    }

    @Test
    void keepsAttemptsOnlyWhenReassigningSameHeldCell() {
        assertFalse(BuildClaimAssignPolicy.shouldResetAttemptCounters("1,2,3", "1,2,3"));
    }
}
