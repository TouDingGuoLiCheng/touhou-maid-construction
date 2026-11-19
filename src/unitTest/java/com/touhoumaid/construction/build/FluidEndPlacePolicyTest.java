package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidEndPlacePolicyTest {
    @Test
    void vanillaFluidsWaitForTheEnd() {
        assertTrue(FluidEndPlacePolicy.isEndPlacedFluid("minecraft:water"));
        assertTrue(FluidEndPlacePolicy.isEndPlacedFluid("minecraft:flowing_water"));
        assertTrue(FluidEndPlacePolicy.isEndPlacedFluid("minecraft:lava"));
        assertTrue(FluidEndPlacePolicy.isEndPlacedFluid("minecraft:flowing_lava"));
        assertFalse(FluidEndPlacePolicy.isEndPlacedFluid("minecraft:stone"));
        assertFalse(FluidEndPlacePolicy.isEndPlacedFluid(null));
    }

    @Test
    void maidsDoNotPlaceFluids() {
        assertFalse(FluidEndPlacePolicy.maidMayPlace("minecraft:water"));
        assertFalse(FluidEndPlacePolicy.maidMayPlace("minecraft:flowing_lava"));
        assertTrue(FluidEndPlacePolicy.maidMayPlace("minecraft:stone"));
    }

    @Test
    void dumpStartsOnlyAfterMaidClaimableWorkIsGone() {
        assertFalse(FluidEndPlacePolicy.readyToDump(1, 0));
        assertFalse(FluidEndPlacePolicy.readyToDump(0, 1));
        assertTrue(FluidEndPlacePolicy.readyToDump(0, 0));
    }

    @Test
    void unreachablePendingDoesNotCountAsMaidWork() {
        assertEquals(0, FluidEndPlacePolicy.maidClaimablePending(2, 2));
        assertEquals(1, FluidEndPlacePolicy.maidClaimablePending(3, 2));
        assertEquals(0, FluidEndPlacePolicy.maidClaimablePending(2, 4));
        assertTrue(FluidEndPlacePolicy.readyToDump(FluidEndPlacePolicy.maidClaimablePending(2, 2), 0));
    }

    @Test
    void occupiedFluidsAreReplacedInPlaceInsteadOfHarvested() {
        assertTrue(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:water"));
        assertTrue(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:flowing_water"));
        assertTrue(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:lava"));
        assertTrue(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:flowing_lava"));
        assertFalse(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:stone"));
        assertFalse(FluidEndPlacePolicy.replaceOccupiedFluid("minecraft:oak_stairs"));
        assertFalse(FluidEndPlacePolicy.replaceOccupiedFluid(null));
    }

    @Test
    void fluidsAreNotForcePlacedAsSupportUnderAnotherBlock() {
        assertFalse(FluidEndPlacePolicy.isForcePlaceSupport("minecraft:water"));
        assertFalse(FluidEndPlacePolicy.isForcePlaceSupport("minecraft:flowing_lava"));
        assertTrue(FluidEndPlacePolicy.isForcePlaceSupport("minecraft:stone"));
        assertTrue(FluidEndPlacePolicy.isForcePlaceSupport("minecraft:lily_pad"));
        assertFalse(FluidEndPlacePolicy.isForcePlaceSupport(null));
    }

    @Test
    void waterloggedBlocksKeepTheirSolidShapeForMaids() {
        assertTrue(FluidEndPlacePolicy.isWaterlogged(java.util.Map.of("waterlogged", "true")));
        assertFalse(FluidEndPlacePolicy.isWaterlogged(java.util.Map.of("waterlogged", "false")));
        assertFalse(FluidEndPlacePolicy.isWaterlogged(java.util.Map.of()));
        assertFalse(FluidEndPlacePolicy.isWaterlogged(null));
        assertTrue(FluidEndPlacePolicy.maidMayPlace("minecraft:oak_stairs"));
        assertFalse(FluidEndPlacePolicy.maidMayPlace("minecraft:water"));
    }
}
