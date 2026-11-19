package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionPathBrainSyncTest {
    @Test
    void activeNavPathMustBePublishedForDoorOpening() {
        assertTrue(ConstructionPathBrainSync.shouldPublishPath(true, false));
        assertFalse(ConstructionPathBrainSync.shouldPublishPath(false, false));
        assertFalse(ConstructionPathBrainSync.shouldPublishPath(true, true));
        assertFalse(ConstructionPathBrainSync.shouldPublishPath(false, true));
    }

    @Test
    void stopOrDoneClearsBrainPath() {
        assertTrue(ConstructionPathBrainSync.shouldClearPath(true));
        assertFalse(ConstructionPathBrainSync.shouldClearPath(false));
    }
}
