package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaffoldDistancePolicyTest {
    @Test
    void sturdyFloorIsDistanceZero() {
        assertEquals(
                0,
                ScaffoldDistancePolicy.compute(
                        ScaffoldDistancePolicy.Below.STURDY,
                        ScaffoldDistancePolicy.NO_HORIZONTAL));
    }

    @Test
    void walkableScaffoldBelowKeepsItsDistance() {
        assertEquals(
                2,
                ScaffoldDistancePolicy.compute(
                        ScaffoldDistancePolicy.Below.scaffold(2),
                        ScaffoldDistancePolicy.NO_HORIZONTAL));
    }

    @Test
    void horizontalWalkableNeighborIncrementsDistance() {
        int[] horizontal = {ScaffoldDistancePolicy.NO_SCAFFOLD, 0, ScaffoldDistancePolicy.NO_SCAFFOLD,
                ScaffoldDistancePolicy.NO_SCAFFOLD};
        assertEquals(
                1,
                ScaffoldDistancePolicy.compute(ScaffoldDistancePolicy.Below.NONE, horizontal));
    }

    @Test
    void unsupportedCellIsDistanceSeven() {
        assertEquals(
                7,
                ScaffoldDistancePolicy.compute(
                        ScaffoldDistancePolicy.Below.NONE,
                        ScaffoldDistancePolicy.NO_HORIZONTAL));
    }

    @Test
    void maidScaffoldStackStaysUnderSevenUnlikeVanillaOnlyCheck() {
        // Vertical stack on a maid staging tower: below is walkable scaffold at distance 0.
        assertEquals(
                0,
                ScaffoldDistancePolicy.compute(
                        ScaffoldDistancePolicy.Below.scaffold(0),
                        ScaffoldDistancePolicy.NO_HORIZONTAL));
    }
}
