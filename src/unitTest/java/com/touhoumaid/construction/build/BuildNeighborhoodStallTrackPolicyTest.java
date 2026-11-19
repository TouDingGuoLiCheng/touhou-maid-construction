package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildNeighborhoodStallTrackPolicyTest {
    @Test
    void tracksMovingPhases() {
        assertTrue(BuildNeighborhoodStallTrackPolicy.shouldTrack(true, false, false));
    }

    @Test
    void idleWithoutClaimDoesNotAccumulateStall() {
        assertFalse(BuildNeighborhoodStallTrackPolicy.shouldTrack(false, true, false));
    }

    @Test
    void idleWithHeldClaimStillTracksWhenLayerHasWork() {
        assertTrue(BuildNeighborhoodStallTrackPolicy.shouldTrack(false, true, true));
    }

    @Test
    void idleWithoutPlaceableWorkDoesNotTrack() {
        assertFalse(BuildNeighborhoodStallTrackPolicy.shouldTrack(false, false, true));
    }
}
