package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildJobResumePolicyTest {

    @Test
    void neverResumesNonBuildingProjectsEvenIfJobMissing() {
        assertFalse(BuildJobResumePolicy.needsResume(false, false, false));
        assertFalse(BuildJobResumePolicy.needsResume(false, false, true));
        assertFalse(BuildJobResumePolicy.needsResume(false, true, true));
    }

    @Test
    void resumesBuildingWhenJobMissing() {
        assertTrue(BuildJobResumePolicy.needsResume(true, false, false));
    }

    @Test
    void resumesBuildingWhenMaidLostBinding() {
        assertTrue(BuildJobResumePolicy.needsResume(true, true, true));
        assertFalse(BuildJobResumePolicy.needsResume(true, true, false));
    }

    @Test
    void skipsKickoffWhenRestoredJobAlreadyDone() {
        assertFalse(BuildJobResumePolicy.shouldKickoffRestoredJob(true, true));
        assertTrue(BuildJobResumePolicy.shouldKickoffRestoredJob(true, false));
        assertFalse(BuildJobResumePolicy.shouldKickoffRestoredJob(false, false));
    }

    @Test
    void marksDoneWhenRestoreRecreateFindsNothingLeft() {
        assertEquals("DONE", BuildJobResumePolicy.statusAfterRestoreRecreate(true));
        assertEquals("BUILDING", BuildJobResumePolicy.statusAfterRestoreRecreate(false));
    }
}
