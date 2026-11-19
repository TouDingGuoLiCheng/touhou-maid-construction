package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.schedule.Activity;
import org.slf4j.Logger;

/** Keeps construction maids awake and on WORK brain activity so task behaviors actually tick. */
public final class ConstructionMaidBrain {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConstructionMaidBrain() {
    }

    /**
     * Called when a maid is assigned to an active job. Sitting maids never receive
     * {@code canBrainMoving}, and non-WORK schedule activities skip work-registered behaviors.
     */
    public static void prepareForActiveWork(EntityMaid maid) {
        boolean wasSitting = maid.isMaidInSittingPose();
        if (wasSitting) {
            maid.setOrderedToSit(false);
        }
        Activity before = maid.getScheduleDetail();
        boolean wasWork = maid.getBrain().isActive(Activity.WORK);
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        Activity after = maid.getScheduleDetail();
        boolean nowWork = maid.getBrain().isActive(Activity.WORK);
        if (wasSitting || before != Activity.WORK || after != Activity.WORK) {
            LOGGER.info(
                    "[TMC-Build] wake maid={} unsit={} activityBefore={} activityAfter={} switchedToWork={} feet={}",
                    ConstructionDebugIds.maid(maid),
                    wasSitting,
                    activityName(before),
                    activityName(after),
                    !wasWork && nowWork,
                    maid.blockPosition().toShortString());
        }
    }

    /** Re-assert WORK while construction is active (schedule may flip to idle/rest at night). */
    public static void ensureWorkActivity(EntityMaid maid) {
        if (maid.getBrain().isActive(Activity.WORK)) {
            return;
        }
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        if (maid.getBrain().isActive(Activity.WORK) && ConstructionDebug.enabled()) {
            LOGGER.info(
                    "[TMC-Build] force_work maid={} activityAfter={} feet={}",
                    ConstructionDebugIds.maid(maid),
                    activityName(maid.getScheduleDetail()),
                    maid.blockPosition().toShortString());
        }
    }

    private static String activityName(Activity activity) {
        return activity == null ? "-" : activity.getName();
    }
}
