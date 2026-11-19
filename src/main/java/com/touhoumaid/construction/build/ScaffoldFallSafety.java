package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.task.TaskConstruction;

/**
 * noGravity hold can leave a huge fallDistance even on flat ground. Clear it while holding
 * and when restoring gravity so the maid does not smash into the floor.
 */
public final class ScaffoldFallSafety {
    private ScaffoldFallSafety() {
    }

    public static boolean shouldClearFallDistance(boolean holdingOrReleasing) {
        return true;
    }

    public static float clearedFallDistance() {
        return 0.0F;
    }

    /** Construction maids on an active (non-paused) build job are immune to fall damage. */
    public static boolean shouldImmunizeFallDamage(boolean constructionTask, boolean boundToActiveJob) {
        return constructionTask && boundToActiveJob;
    }

    public static boolean shouldImmunizeFallDamage(EntityMaid maid) {
        if (!maid.getTask().getUid().equals(TaskConstruction.UID)) {
            return false;
        }
        return ConstructionMaidBinding.jobOf(maid).map(job -> !job.isPaused()).orElse(false);
    }
}
