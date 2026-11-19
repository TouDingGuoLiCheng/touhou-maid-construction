package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Task pipeline diagnostics. Grep {@code [TMC-Build]}; enable with {@code /tmc debug on}
 * or {@code -Dtouhou_maid_construction.debug=true}.
 */
public final class BuildDebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildDebugLog() {
    }

    private static void info(String format, Object... args) {
        if (ConstructionDebug.enabled()) {
            LOGGER.info(format, args);
        }
    }

    public static void phaseChange(EntityMaid maid, String from, String to, String reason) {
        info(
                "[TMC-Build] PHASE maid={} {} -> {} reason={} feet={}",
                ConstructionDebugIds.maid(maid),
                from,
                to,
                reason,
                shortPos(maid.blockPosition()));
    }

    public static void behaviorStarted(EntityMaid maid) {
        info(
                "[TMC-Build] START maid={} sitting={} activity={} jobPaused={} feet={}",
                ConstructionDebugIds.maid(maid),
                maid.isMaidInSittingPose(),
                maid.getScheduleDetail() != null ? maid.getScheduleDetail().getName() : "-",
                ConstructionMaidBinding.jobOf(maid).map(BuildJob::isPaused).orElse(true),
                shortPos(maid.blockPosition()));
    }

    public static void pickNextStuck(EntityMaid maid, String reason) {
        info(
                "[TMC-Build] PICK_STUCK maid={} reason={} feet={}",
                ConstructionDebugIds.maid(maid),
                reason,
                shortPos(maid.blockPosition()));
    }

    public static void heartbeat(
            EntityMaid maid,
            String phase,
            BlockPos target,
            BlockPos workPos,
            boolean canInteract,
            int placeCooldown,
            int faceHold,
            int taskClaimTicks,
            int abandonCooldown,
            int batchSize,
            boolean usingScaffoldNav,
            int neighborhoodStallTicks,
            int arrivedBlockedTicks) {
        info(
                "[TMC-Build] TICK maid={} phase={} target={} work={} canInteract={} "
                        + "placeCd={} faceHold={} claimTicks={} abandonCd={} batch={} "
                        + "scaffoldNav={} neighborhoodStall={} arrivedBlocked={} feet={}",
                ConstructionDebugIds.maid(maid),
                phase,
                shortPos(target),
                shortPos(workPos),
                canInteract,
                placeCooldown,
                faceHold,
                taskClaimTicks,
                abandonCooldown,
                batchSize,
                usingScaffoldNav,
                neighborhoodStallTicks,
                arrivedBlockedTicks,
                shortPos(maid.blockPosition()));
    }

    public static void claimed(EntityMaid maid, BlockPos taskPos, BlockPos workPos, BlockPos anchor) {
        info(
                "[TMC-Build] CLAIM maid={} task={} work={} anchor={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                shortPos(workPos),
                shortPos(anchor),
                shortPos(maid.blockPosition()));
    }

    public static void noProgress(EntityMaid maid, BuildProgressStallPolicy.State stall, BlockPos work) {
        info(
                "[TMC-Build] NO_PROGRESS maid={} anchor={}, {}, {} ticks={} work={} feet={}",
                ConstructionDebugIds.maid(maid),
                stall.x(),
                stall.y(),
                stall.z(),
                stall.ticks(),
                shortPos(work),
                shortPos(maid.blockPosition()));
    }

    public static void placed(EntityMaid maid, BlockPos pos, boolean fromBatch, int batchRemaining) {
        info(
                "[TMC-Build] PLACED maid={} pos={} fromBatch={} batchLeft={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(pos),
                fromBatch,
                batchRemaining,
                shortPos(maid.blockPosition()));
    }

    public static void placeFailed(EntityMaid maid, BlockPos pos, String reason) {
        info(
                "[TMC-Build] PLACE_FAIL maid={} pos={} reason={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(pos),
                reason,
                shortPos(maid.blockPosition()));
    }

    public static void batchNext(EntityMaid maid, BlockPos taskPos, int queueRemaining) {
        info(
                "[TMC-Build] BATCH maid={} task={} queueLeft={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                queueRemaining,
                shortPos(maid.blockPosition()));
    }

    public static void abandoned(EntityMaid maid, BlockPos taskPos, int heldTicks, int maidsOnJob, int retryCooldown) {
        info(
                "[TMC-Build] ABANDON maid={} task={} heldTicks={} maidsOnJob={} retryCd={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                heldTicks,
                maidsOnJob,
                retryCooldown,
                shortPos(maid.blockPosition()));
    }

    public static void walk(
            EntityMaid maid,
            BlockPos work,
            int routeLen,
            boolean onScaffold,
            boolean deferLandNav,
            boolean nudgeFlat,
            boolean usingScaffoldNav) {
        info(
                "[TMC-Build] WALK maid={} work={} routeLen={} onScaffold={} deferLand={} nudgeFlat={} scaffoldNav={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(work),
                routeLen,
                onScaffold,
                deferLandNav,
                nudgeFlat,
                usingScaffoldNav,
                shortPos(maid.blockPosition()));
    }

    public static void vaultDirectPlace(EntityMaid maid, BlockPos taskPos, int navFailures) {
        info(
                "[TMC-Build] VAULT_PLACE maid={} pos={} navFails={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                navFailures,
                shortPos(maid.blockPosition()));
    }

    public static void isolatedAirPlace(EntityMaid maid, BlockPos taskPos) {
        info(
                "[TMC-Build] ISOLATED_AIR_PLACE maid={} pos={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                shortPos(maid.blockPosition()));
    }

    public static void noStandOccupiedPlace(EntityMaid maid, BlockPos taskPos) {
        info(
                "[TMC-Build] NO_STAND_OCCUPIED_PLACE maid={} pos={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                shortPos(maid.blockPosition()));
    }

    public static void noStandImmediatePlace(EntityMaid maid, BlockPos taskPos) {
        info(
                "[TMC-Build] NO_STAND_IMMEDIATE_PLACE maid={} pos={} feet={}",
                ConstructionDebugIds.maid(maid),
                shortPos(taskPos),
                shortPos(maid.blockPosition()));
    }

    public static void projectStallFallback(BuildJobKey key, UUID maidId, BlockPos taskPos, int placedDelta) {
        info(
                "[TMC-Build] PROJECT_STALL_FALLBACK key={} maid={} task={} placedDelta={}",
                key.projectId(),
                ConstructionDebugIds.maid(null, maidId),
                shortPos(taskPos),
                placedDelta);
    }

    public static void projectStallFallbackFailed(BuildJobKey key, UUID maidId, BlockPos taskPos) {
        info(
                "[TMC-Build] PROJECT_STALL_FALLBACK_FAIL key={} maid={} task={}",
                key.projectId(),
                ConstructionDebugIds.maid(null, maidId),
                shortPos(taskPos));
    }

    public static void projectIdleFallback(BuildJobKey key, UUID maidId, BlockPos taskPos, int placedDelta) {
        info(
                "[TMC-Build] PROJECT_IDLE_FALLBACK key={} maid={} task={} placedDelta={}",
                key.projectId(),
                ConstructionDebugIds.maid(null, maidId),
                shortPos(taskPos),
                placedDelta);
    }

    public static void projectIdleFallbackFailed(BuildJobKey key, UUID maidId, BlockPos taskPos) {
        info(
                "[TMC-Build] PROJECT_IDLE_FALLBACK_FAIL key={} maid={} task={}",
                key.projectId(),
                ConstructionDebugIds.maid(null, maidId),
                shortPos(taskPos));
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.toShortString();
    }
}
