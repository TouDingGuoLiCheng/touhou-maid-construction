package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildPlaceCadencePolicyTest {
    @Test
    void placeIntervalIsHalfSecondAtTwentyTps() {
        assertEquals(10, BuildPlaceCadencePolicy.PLACE_INTERVAL_TICKS);
    }

    @Test
    void batchFaceHoldIsShorterAndSkipsWhenAlreadyFacing() {
        assertEquals(0, BuildPlaceCadencePolicy.faceHoldTicks(true, true));
        assertEquals(1, BuildPlaceCadencePolicy.faceHoldTicks(true, false));
        assertEquals(4, BuildPlaceCadencePolicy.faceHoldTicks(false, false));
    }
}
