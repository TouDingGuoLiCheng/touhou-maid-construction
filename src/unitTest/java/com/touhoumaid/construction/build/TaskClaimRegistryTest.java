package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskClaimRegistryTest {
    private static final long POS_A = 1L;
    private static final long POS_B = 2L;

    @Test
    void secondMaidCannotClaimSameCellUntilReleased() {
        TaskClaimRegistry claims = new TaskClaimRegistry();
        UUID maidA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID maidB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        assertTrue(claims.tryClaim(POS_A, maidA));
        assertEquals(maidA, claims.owner(POS_A));
        assertFalse(claims.tryClaim(POS_A, maidB));

        claims.releaseAllFor(maidA);
        assertNull(claims.owner(POS_A));
        assertTrue(claims.tryClaim(POS_A, maidB));
    }

    @Test
    void releaseAllForOnlyClearsOneMaid() {
        TaskClaimRegistry claims = new TaskClaimRegistry();
        UUID maidA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID maidB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        assertTrue(claims.tryClaim(POS_A, maidA));
        assertTrue(claims.tryClaim(POS_B, maidB));

        claims.releaseAllFor(maidA);
        assertNull(claims.owner(POS_A));
        assertEquals(maidB, claims.owner(POS_B));
    }
}
