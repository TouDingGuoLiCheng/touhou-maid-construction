package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoStandOccupiedPlacePolicyTest {
    @Test
    void clearsOccupiedTargetWhenNoWorkStand() {
        assertTrue(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, false, false, true));
    }

    @Test
    void doesNotClearWhenWorkStandExists() {
        assertFalse(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                true, false, false, true));
    }

    @Test
    void doesNotClearAirOrAlreadyCorrectBlock() {
        assertFalse(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, true, false, true));
        assertFalse(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, false, true, true));
    }

    @Test
    void doesNotClearUnbreakable() {
        assertFalse(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, false, false, false));
    }

    @Test
    void ignoresCoverAndOverlapToggles() {
        // Policy itself has no cover/ignore inputs — always eligible when predicates match.
        assertTrue(NoStandOccupiedPlacePolicy.shouldClearOccupiedTargetThenPlace(
                false, false, false, true));
    }
}
