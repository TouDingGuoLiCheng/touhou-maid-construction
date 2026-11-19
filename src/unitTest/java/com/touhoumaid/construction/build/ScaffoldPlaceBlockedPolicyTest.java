package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaffoldPlaceBlockedPolicyTest {
    @Test
    void firstClimbingPlaceBlockedTeleportsToWork() {
        assertEquals(
                ScaffoldPlaceBlockedPolicy.Next.TELEPORT_WORK,
                ScaffoldPlaceBlockedPolicy.afterPlaceBlocked(1, true));
    }

    @Test
    void nonClimbingPlaceBlockedEscalatesLikePlanFail() {
        assertEquals(
                ScaffoldPlaceBlockedPolicy.Next.RETRY_MOVE,
                ScaffoldPlaceBlockedPolicy.afterPlaceBlocked(1, false));
        assertEquals(
                ScaffoldPlaceBlockedPolicy.Next.DIRECT_PLACE,
                ScaffoldPlaceBlockedPolicy.afterPlaceBlocked(2, false));
    }
}
