package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.ConstructionMod;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/** Join/login recovery for stale hover state and Neruina tick suspension. */
public final class ConstructionMaidRecovery {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConstructionMaidRecovery() {
    }

    public static void recoverOnJoin(ServerLevel level, EntityMaid maid, String reason) {
        boolean clearedNeruina = NeruinaCompat.clearEntitySuspension(maid);
        forceReleaseHover(level, maid, reason);
        BuildJobService.tryBindMaid(level, maid);
        ConstructionMaidBinding.jobOf(maid).ifPresent(job -> {
            if (!job.isPaused()) {
                ConstructionMaidBrain.prepareForActiveWork(maid);
            }
        });
        maid.getPersistentData().putInt(ConstructionMod.HOVER_RECOVER_TICKS_KEY, ConstructionMod.HOVER_RECOVER_TICKS);
        if (clearedNeruina || maid.isNoGravity()) {
            LOGGER.info(
                    "[TMC-Recover] maid={} reason={} neruinaCleared={} noGravity={}",
                    maid.getUUID(),
                    reason,
                    clearedNeruina,
                    maid.isNoGravity());
        }
    }

    private static void forceReleaseHover(ServerLevel level, EntityMaid maid, String reason) {
        ScaffoldColumnSupport.correctFloatingAboveColumn(level, maid);
        if (maid.isNoGravity()) {
            maid.setNoGravity(false);
            maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
            LOGGER.warn("[TMC-Recover] forced noGravity off maid={} reason={}", maid.getUUID(), reason);
        }
        if (!maid.onGround() && maid.getDeltaMovement().y >= 0.0D) {
            maid.setDeltaMovement(maid.getDeltaMovement().x, -0.08D, maid.getDeltaMovement().z);
        }
    }
}
