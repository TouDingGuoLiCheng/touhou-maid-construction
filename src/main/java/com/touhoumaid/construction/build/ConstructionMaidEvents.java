package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.ai.ConstructionPriority;
import com.touhoumaid.construction.task.TaskConstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Server events for construction-maid fall diagnostics. */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConstructionMaidEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ConstructionMaidEvents() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!ScaffoldFallSafety.shouldImmunizeFallDamage(maid)) {
            return;
        }
        event.setDistance(ScaffoldFallSafety.clearedFallDistance());
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!maid.getTask().getUid().equals(TaskConstruction.UID)) {
            return;
        }
        if (event.getSource().is(DamageTypes.IN_WALL)) {
            if (!maid.level().isClientSide()
                    && ConstructionMaidBinding.jobOf(maid)
                            .map(job -> !job.isPaused() && !job.isDone())
                            .orElse(false)
                    && ConstructionSiteArrive.teleportFromSuffocation(maid)) {
                event.setCanceled(true);
                maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
            }
            return;
        }
        if (!event.getSource().is(DamageTypes.FALL)) {
            return;
        }
        if (ScaffoldFallSafety.shouldImmunizeFallDamage(maid)) {
            event.setCanceled(true);
            maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
            ScaffoldFallDebugLog.fallBlocked(maid, event.getAmount(), ConstructionMaidBinding.jobOf(maid).isPresent());
            return;
        }
        ScaffoldFallDebugLog.fallDamage(
                maid,
                event.getAmount(),
                maid.fallDistance,
                maid.isNoGravity(),
                ConstructionMaidBinding.jobOf(maid).isPresent() ? "construction" : "idle");
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (!maid.getTask().getUid().equals(TaskConstruction.UID)) {
            return;
        }
        String type = event.getSource().getMsgId();
        if (!event.getSource().is(DamageTypes.FALL) && !type.contains("fall")) {
            return;
        }
        ScaffoldFallDebugLog.death(maid, type, maid.fallDistance, maid.isNoGravity());
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
                return;
            }
            int recoverTicks = maid.getPersistentData().getInt(ConstructionMod.HOVER_RECOVER_TICKS_KEY);
            if (recoverTicks <= 0) {
                return;
            }
            maid.getPersistentData().putInt(ConstructionMod.HOVER_RECOVER_TICKS_KEY, recoverTicks - 1);
            BlockPos feet = maid.blockPosition();
            boolean onScaffold = maid.level().getBlockState(feet).is(Blocks.SCAFFOLDING)
                    || maid.level().getBlockState(feet.below()).is(Blocks.SCAFFOLDING);
            if (!onScaffold && maid.isNoGravity()) {
                ScaffoldFallDebugLog.hoverForcedOff(maid, false, false);
                maid.setNoGravity(false);
                maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
            }
            if (maid.getTask().getUid().equals(TaskConstruction.UID)
                    && ConstructionPriority.isActive(maid)
                    && ConstructionMaidBinding.jobOf(maid).map(job -> !job.isPaused()).orElse(false)) {
                ConstructionMaidBrain.ensureWorkActivity(maid);
            }
        } catch (Throwable t) {
            LOGGER.error(
                    "[TMC-Fall] join-recover tick failed maid={}",
                    event.getEntity().getUUID(),
                    t);
        }
    }
}
