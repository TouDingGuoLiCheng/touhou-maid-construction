package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldColumnClaimsTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void ownerKeepsHerColumnAgainstOtherMaids() {
        ScaffoldColumnClaims claims = new ScaffoldColumnClaims();
        assertTrue(claims.occupy(129, 195, ALICE, 100L));
        assertFalse(claims.occupy(129, 195, BOB, 100L));
        assertTrue(claims.occupy(129, 195, ALICE, 105L));
    }

    @Test
    void othersColumnsAreReportedAsBlocked() {
        ScaffoldColumnClaims claims = new ScaffoldColumnClaims();
        claims.occupy(129, 195, ALICE, 100L);
        claims.occupy(131, 206, BOB, 100L);
        Set<Long> blockedForBob = claims.occupiedByOthers(BOB, 100L);
        assertEquals(Set.of(ScaffoldLayout.packXz(129, 195)), blockedForBob);
        assertEquals(Set.of(ScaffoldLayout.packXz(131, 206)), claims.occupiedByOthers(ALICE, 100L));
    }

    @Test
    void adjacentColumnsAreAlsoBlockedWhenSpacingIsRequired() {
        ScaffoldColumnClaims claims = new ScaffoldColumnClaims();
        claims.occupy(10, 10, ALICE, 100L);
        Set<Long> blockedForBob = claims.occupiedByOthersWithSpacing(BOB, 100L, 1);
        assertTrue(blockedForBob.contains(ScaffoldLayout.packXz(10, 10)));
        assertTrue(blockedForBob.contains(ScaffoldLayout.packXz(9, 10)));
        assertTrue(blockedForBob.contains(ScaffoldLayout.packXz(11, 10)));
        assertTrue(blockedForBob.contains(ScaffoldLayout.packXz(10, 9)));
        assertTrue(blockedForBob.contains(ScaffoldLayout.packXz(10, 11)));
        assertFalse(blockedForBob.contains(ScaffoldLayout.packXz(12, 10)));
    }

    @Test
    void abandonedColumnsExpire() {
        ScaffoldColumnClaims claims = new ScaffoldColumnClaims();
        claims.occupy(129, 195, ALICE, 100L);
        long later = 100L + ScaffoldColumnClaims.CLAIM_TIMEOUT_TICKS;
        assertTrue(claims.occupiedByOthers(BOB, later).isEmpty());
        assertTrue(claims.occupy(129, 195, BOB, later));
    }

    @Test
    void releasingAMaidFreesAllHerColumns() {
        ScaffoldColumnClaims claims = new ScaffoldColumnClaims();
        claims.occupy(129, 195, ALICE, 100L);
        claims.occupy(130, 195, ALICE, 100L);
        claims.release(ALICE);
        assertTrue(claims.occupiedByOthers(BOB, 100L).isEmpty());
    }
}
