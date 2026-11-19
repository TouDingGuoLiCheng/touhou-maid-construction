package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/**
 * Fall / noGravity diagnostics for construction maids. Grep {@code [TMC-Fall]}; enable with {@code /tmc debug on}
 * or {@code -Dtouhou_maid_construction.debug=true}.
 */
public final class ScaffoldFallDebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScaffoldFallDebugLog() {
    }

    private static void info(String format, Object... args) {
        if (ConstructionDebug.enabled()) {
            LOGGER.info(format, args);
        }
    }

    public static void moverTick(
            EntityMaid maid,
            BlockPos step,
            boolean noGravity,
            float fallDistance,
            double vy,
            boolean arrived) {
        info(
                "[TMC-Fall] MOVER maid={} step={} noGravity={} fallDist={} vy={} arrived={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(step),
                noGravity,
                String.format("%.2f", fallDistance),
                String.format("%.3f", vy),
                arrived,
                shortPos(maid.blockPosition()));
    }

    public static void moverStop(EntityMaid maid, float fallBefore, boolean hadNoGravity) {
        info(
                "[TMC-Fall] STOP maid={} hadNoGravity={} fallBefore={} fallAfter=0 feet={}",
                ConstructionDebugIds.maid(maid),
                hadNoGravity,
                String.format("%.2f", fallBefore),
                shortPos(maid.blockPosition()));
    }

    public static void fallCleared(EntityMaid maid, String reason, float fallBefore) {
        info(
                "[TMC-Fall] CLEAR maid={} reason={} fallBefore={} feet={} noGravity={}",
                ConstructionDebugIds.maid(maid),
                reason,
                String.format("%.2f", fallBefore),
                shortPos(maid.blockPosition()),
                maid.isNoGravity());
    }

    public static void hoverForcedOff(EntityMaid maid, boolean onScaffold, boolean usingScaffoldNav) {
        info(
                "[TMC-Fall] HOVER_OFF maid={} onScaffold={} scaffoldNav={} fallDist={} feet={}",
                ConstructionDebugIds.maid(maid),
                onScaffold,
                usingScaffoldNav,
                String.format("%.2f", maid.fallDistance),
                shortPos(maid.blockPosition()));
    }

    public static void hoverReleasedOnJoin(EntityMaid maid, float fallBefore) {
        info(
                "[TMC-Fall] JOIN_OFF maid={} fallBefore={} feet={}",
                ConstructionDebugIds.maid(maid),
                String.format("%.2f", fallBefore),
                shortPos(maid.blockPosition()));
    }

    public static void teleportClear(EntityMaid maid, String reason, float fallBefore, BlockPos from, BlockPos to) {
        info(
                "[TMC-Fall] TELEPORT maid={} reason={} fallBefore={} from={} to={}",
                ConstructionDebugIds.maid(maid),
                reason,
                String.format("%.2f", fallBefore),
                shortPos(from),
                shortPos(to));
    }

    public static void fallBlocked(EntityMaid maid, float amount, boolean onJob) {
        info(
                "[TMC-Fall] BLOCKED maid={} amount={} onJob={} feet={}",
                maid.getUUID(),
                String.format("%.2f", amount),
                onJob,
                maid.blockPosition());
    }

    public static void fallDamage(EntityMaid maid, float amount, float fallDistance, boolean noGravity, String phase) {
        info(
                "[TMC-Fall] DAMAGE maid={} amount={} fallDist={} noGravity={} phase={} hp={} feet={}",
                ConstructionDebugIds.maid(maid),
                String.format("%.2f", amount),
                String.format("%.2f", fallDistance),
                noGravity,
                phase == null ? "-" : phase,
                String.format("%.1f", maid.getHealth()),
                shortPos(maid.blockPosition()));
    }

    public static void death(EntityMaid maid, String damageType, float fallDistance, boolean noGravity) {
        info(
                "[TMC-Fall] DEATH maid={} damageType={} fallDist={} noGravity={} feet={}",
                ConstructionDebugIds.maid(maid),
                damageType,
                String.format("%.2f", fallDistance),
                noGravity,
                shortPos(maid.blockPosition()));
    }

    public static void highFallDistance(EntityMaid maid, float fallDistance, String phase) {
        info(
                "[TMC-Fall] HIGH_FALL maid={} fallDist={} noGravity={} phase={} feet={}",
                ConstructionDebugIds.maid(maid),
                String.format("%.2f", fallDistance),
                maid.isNoGravity(),
                phase == null ? "-" : phase,
                shortPos(maid.blockPosition()));
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.toShortString();
    }
}
