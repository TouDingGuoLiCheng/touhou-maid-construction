package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildIsolatedAirPlacePolicyTest {
    @Test
    void fiveFacesAirAllowsImmediateVaultPlace() {
        assertTrue(BuildIsolatedAirPlacePolicy.allowImmediateVaultPlace(true));
    }

    @Test
    void anyCheckedSolidNeighborBlocksImmediateVaultPlace() {
        assertFalse(BuildIsolatedAirPlacePolicy.allowImmediateVaultPlace(false));
    }

    @Test
    void fiveAirOrReplaceableNeighborsMeansIsolated() {
        boolean[] air = {true, true, true, true, true};
        assertTrue(BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(air));
    }

    @Test
    void oneSolidSideOrAboveIsNotIsolated() {
        boolean[] faces = {true, true, false, true, true};
        assertFalse(BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(faces));
    }

    @Test
    void nullOrWrongLengthIsNotIsolated() {
        assertFalse(BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(null));
        assertFalse(BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(new boolean[4]));
        assertFalse(BuildIsolatedAirPlacePolicy.allNeighborsAirOrReplaceable(new boolean[6]));
    }
}
