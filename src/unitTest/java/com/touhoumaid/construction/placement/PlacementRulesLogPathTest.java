package com.touhoumaid.construction.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementRulesLogPathTest {

    @Test
    void acceptsUnstrippedLogAndWoodPaths() {
        assertTrue(PlacementRules.isUnstrippedLogPath("oak_log"));
        assertTrue(PlacementRules.isUnstrippedLogPath("oak_wood"));
        assertTrue(PlacementRules.isUnstrippedLogPath("mangrove_log"));
    }

    @Test
    void rejectsStrippedLogPaths() {
        assertFalse(PlacementRules.isUnstrippedLogPath("stripped_oak_log"));
        assertFalse(PlacementRules.isUnstrippedLogPath("stripped_oak_wood"));
        assertFalse(PlacementRules.isUnstrippedLogPath("stripped_mangrove_log"));
    }

    @Test
    void rejectsNullOrEmptyPath() {
        assertFalse(PlacementRules.isUnstrippedLogPath(null));
        assertFalse(PlacementRules.isUnstrippedLogPath(""));
    }
}
