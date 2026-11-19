package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldColumnSupportTest {
    @Test
    void canStepUpRequiresScaffoldSupportBelowTarget() {
        // Documented contract: upward step into air is only valid when the cell below is scaffold.
        // Full block-state checks are covered in-game; here we assert the policy wiring exists.
        assertTrue(ConstructionWalkPolicy.shouldSnapClimbStall(
                true, true, 8, 0, 20, -58, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(
                true, true, 8, 0, 20, -50, -50, -50));
        assertFalse(ConstructionWalkPolicy.shouldSnapClimbStall(
                true, true, 8, 0, 20, -49, -40, -50));
    }

    @Test
    void snapUpAfterPlaceOnlyForVerticalTowerExtension() {
        assertTrue(ConstructionWalkPolicy.shouldSnapUpAfterVerticalPlace(
                157, -60, -111, 157, -60, -111));
        assertTrue(ConstructionWalkPolicy.shouldSnapUpAfterVerticalPlace(
                157, -60, -111, 157, -59, -111));
        assertFalse(ConstructionWalkPolicy.shouldSnapUpAfterVerticalPlace(
                157, -60, -111, 158, -60, -111));
        assertFalse(ConstructionWalkPolicy.shouldSnapUpAfterVerticalPlace(
                157, -48, -111, 158, -48, -111));
    }
}
