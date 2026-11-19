package com.touhoumaid.construction.build;

/**
 * Login / restore rules without Minecraft types so JVM tests can cover them.
 */
public final class BuildJobResumePolicy {
    private BuildJobResumePolicy() {
    }

    /**
     * Whether a remembered session should be force-restored.
     *
     * @param projectBuilding project status is BUILDING
     * @param jobPresent      in-memory job already exists
     * @param maidMissingBind an assigned maid is loaded but has no job binding
     */
    public static boolean needsResume(boolean projectBuilding, boolean jobPresent, boolean maidMissingBind) {
        if (!projectBuilding) {
            return false;
        }
        if (!jobPresent) {
            return true;
        }
        return maidMissingBind;
    }

    /**
     * After recreating a job from a BUILDING project, skip maid kickoff when the world already
     * matches the schematic (treat as finished).
     */
    public static boolean shouldKickoffRestoredJob(boolean projectBuilding, boolean jobDone) {
        return projectBuilding && !jobDone;
    }

    /** Status to persist after a restore recreate when the job is already complete in-world. */
    public static String statusAfterRestoreRecreate(boolean jobDone) {
        return jobDone ? "DONE" : "BUILDING";
    }
}
