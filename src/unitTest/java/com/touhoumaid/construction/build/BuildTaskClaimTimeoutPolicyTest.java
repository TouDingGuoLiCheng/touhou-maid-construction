package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildTaskClaimTimeoutPolicyTest {
    @Test
    void abandonsAfterThirtySeconds() {
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldAbandon(599));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandon(600));
    }

    @Test
    void soloMaidGetsRetryCooldown() {
        assertEquals(100, BuildTaskClaimTimeoutPolicy.soloRetryCooldownTicks(1));
        assertEquals(0, BuildTaskClaimTimeoutPolicy.soloRetryCooldownTicks(2));
    }

    @Test
    void abandonsStalledMoveBeforeFullTimeout() {
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldAbandonStalledMove(59, 60));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandonStalledMove(60, 60));
    }

    @Test
    void abandonsStalledPlaceBeforeFullTimeout() {
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldAbandonStalledPlace(59, 60));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandonStalledPlace(60, 60));
    }

    @Test
    void suppressesDeferAfterNeighborhoodStallThreshold() {
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(14));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(15));
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(14, false));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(15, false));
    }

    @Test
    void idleWithoutClaimNeverSuppressesDefer() {
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(15, true));
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(600, true));
    }

    @Test
    void abandonsClaimWithoutWorkStandAfterStall() {
        // Log: work=- NO_PROGRESS ticks=300+ while beginDirectPlace loop never abandoned.
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldAbandonNoWorkStand(59, 60));
        assertFalse(BuildTaskClaimTimeoutPolicy.shouldAbandonNoWorkStand(60, 59));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandonNoWorkStand(60, 60));
    }

    @Test
    void fullTimeoutAbandonsEvenWithoutWorkStand() {
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandonWithoutWorkStandOnFullTimeout(true));
        assertTrue(BuildTaskClaimTimeoutPolicy.shouldAbandonWithoutWorkStandOnFullTimeout(false));
    }
}
