package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildClaimSpreadPolicyTest {
    @Test
    void crowdedNearestYieldsToAFreeStandFartherAlong() {
        List<String> nearestFirst = List.of("crowdedNear", "freeFar", "crowdedMid");
        assertEquals(
                List.of("freeFar", "crowdedNear", "crowdedMid"),
                BuildClaimSpreadPolicy.freeStandsFirst(nearestFirst, "freeFar"::equals));
    }

    @Test
    void keepsNearestOrderWhenEveryStandIsTaken() {
        List<String> nearestFirst = List.of("onlySpot", "alsoCrowded");
        assertEquals(
                nearestFirst,
                BuildClaimSpreadPolicy.freeStandsFirst(nearestFirst, cell -> false));
    }

    @Test
    void keepsNearestOrderWhenEveryStandIsFree() {
        List<String> nearestFirst = List.of("a", "b", "c");
        assertEquals(
                nearestFirst,
                BuildClaimSpreadPolicy.freeStandsFirst(nearestFirst, cell -> true));
    }

    @Test
    void continuingFromLastPlacedKeepsNearestEvenIfThatStandIsCrowded() {
        List<String> nearestFirst = List.of("crowdedNear", "freeFar", "crowdedMid");
        assertEquals(
                nearestFirst,
                BuildClaimSpreadPolicy.orderForClaim(nearestFirst, "freeFar"::equals, true));
    }

    @Test
    void firstClaimStillPrefersAFreeStand() {
        List<String> nearestFirst = List.of("crowdedNear", "freeFar", "crowdedMid");
        assertEquals(
                List.of("freeFar", "crowdedNear", "crowdedMid"),
                BuildClaimSpreadPolicy.orderForClaim(nearestFirst, "freeFar"::equals, false));
    }
}
