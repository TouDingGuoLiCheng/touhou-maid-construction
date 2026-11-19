package com.touhoumaid.construction.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.build.BuildPlacementCheck;
import com.touhoumaid.construction.build.BuildJob;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.build.BuildArrivedPolicy;
import com.touhoumaid.construction.build.ScaffoldLedger;
import com.touhoumaid.construction.build.ScaffoldMaterials;
import com.touhoumaid.construction.build.ScaffoldService;
import com.touhoumaid.construction.build.ScaffoldColumnSupport;
import com.touhoumaid.construction.build.ScaffoldPathLockPolicy;
import com.touhoumaid.construction.build.ScaffoldLayout;
import com.touhoumaid.construction.build.ScaffoldWaypointPolicy;
import com.touhoumaid.construction.build.ScaffoldVanillaNavPolicy;
import com.touhoumaid.construction.build.BuildDebugLog;
import com.touhoumaid.construction.build.BuildPerfLog;
import com.touhoumaid.construction.build.BuildHeavyNavBudget;
import com.touhoumaid.construction.build.BuildHeavyNavBudgetPolicy;
import com.touhoumaid.construction.build.AgentDebugLog;
import com.touhoumaid.construction.build.ConstructionDebugIds;
import com.touhoumaid.construction.build.ScaffoldFallDebugLog;
import com.touhoumaid.construction.build.ScaffoldFallSafety;
import com.touhoumaid.construction.build.ScaffoldDebugLog;
import com.touhoumaid.construction.build.ConstructionScaffoldBlocks;
import com.touhoumaid.construction.build.ConstructionSiteArrive;
import com.touhoumaid.construction.build.ConstructionSiteArrivePolicy;
import com.touhoumaid.construction.build.ConstructionWalkPolicy;
import com.touhoumaid.construction.build.ConstructionPathBrainSync;
import com.touhoumaid.construction.build.ConstructionMaidBrain;
import com.touhoumaid.construction.build.ScaffoldPlanFailPolicy;
import com.touhoumaid.construction.build.ScaffoldPlaceBlockedPolicy;
import com.touhoumaid.construction.build.ScaffoldEscalatePolicy;
import com.touhoumaid.construction.build.BuildPathNavFailPolicy;
import com.touhoumaid.construction.build.MaterialWaitVaultRescanPolicy;
import com.touhoumaid.construction.build.StockIndexedMaterialSource;
import com.touhoumaid.construction.build.BuildVaultHoldClaimPolicy;
import com.touhoumaid.construction.build.BuildIsolatedAir;
import com.touhoumaid.construction.build.BuildIsolatedAirPlacePolicy;
import com.touhoumaid.construction.build.BuildNoWorkStandPlacePolicy;
import com.touhoumaid.construction.build.BuildPlaceCadencePolicy;
import com.touhoumaid.construction.build.BuildNoPlaceTeleportPolicy;
import com.touhoumaid.construction.build.BuildProgressStallPolicy;
import com.touhoumaid.construction.build.ScaffoldNeedPolicy;
import com.touhoumaid.construction.build.BuildWorkGoalStallPolicy;
import com.touhoumaid.construction.build.BuildTaskClaimTimeoutPolicy;
import com.touhoumaid.construction.build.BuildClaimAssignPolicy;
import com.touhoumaid.construction.build.BuildNeighborhoodStallTrackPolicy;
import com.touhoumaid.construction.build.BuildMaterialWaitStayPolicy;
import com.touhoumaid.construction.build.BuildTask;
import com.touhoumaid.construction.build.BuildWorkPosition;
import com.touhoumaid.construction.build.WorkStandMissRescanPolicy;
import com.touhoumaid.construction.build.CoverTerrainClear;
import com.touhoumaid.construction.build.ConstructionMaidBinding;
import com.touhoumaid.construction.build.ConstructionMaidFollow;
import com.touhoumaid.construction.build.MaterialDebugLog;
import com.touhoumaid.construction.chat.ConstructionChatBubbles;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.network.BuildProgressPacket;
import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.task.TaskConstruction;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Drives construction: path to work positions, place blocks, drain local reachable batch (strategy A).
 * On move timeout marks unreachable then direct-places (plan B).
 * Material shortage is per-maid: freeze in place, poll vault, auto-resume when stocked.
 */
public class MaidConstructionBehavior extends Behavior<EntityMaid> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float WALK_SPEED = 0.6F;
    private static final int MATERIAL_CHECK_INTERVAL = 40;
    private static final int OUT_OF_RANGE_SWEEP_INTERVAL = 100;

    private enum Phase {
        IDLE,
        MOVING,
        FACING,
        PLACING,
        BATCH,
        WAITING_MATERIAL
    }

    private Phase phase = Phase.IDLE;
    private BuildTask currentTask;
    private BlockPos workPosition;
    private final Deque<BuildTask> localBatch = new ArrayDeque<>();
    private BuildArrivedPolicy.State arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
    private BuildProgressStallPolicy.State progressStall = BuildProgressStallPolicy.State.EMPTY;
    private BuildWorkGoalStallPolicy.State workGoalStall = BuildWorkGoalStallPolicy.State.EMPTY;
    private int scaffoldAttempts;
    private int notNeededWhileStuck;
    private List<BlockPos> scaffoldPath = List.of();
    private List<BlockPos> scaffoldWaypoints = List.of();
    private BlockPos scaffoldFinalWorkFeet;
    private int scaffoldWaypointIndex;
    private String lastPathLogKey = "";
    private boolean usingScaffoldNav;
    private BlockPos lastVanillaWalkTarget;
    private int ticksSinceVanillaPathIssue = ConstructionWalkPolicy.NEAR_REPATH_COOLDOWN_TICKS;
    private int ticksSinceHeavyNav = BuildHeavyNavBudgetPolicy.MIN_TICKS_BETWEEN;
    private long missedStandTarget = Long.MIN_VALUE;
    private int ticksSinceStandMiss = WorkStandMissRescanPolicy.RESCAN_INTERVAL_TICKS;
    private int ticksSinceStuckTeleport = ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS;
    private int ticksSinceLastPlace;
    private int placeCooldown;
    private int faceHoldRemaining;
    private int materialCheckCooldown;
    private int materialVaultRescanCooldown = MaterialWaitVaultRescanPolicy.arm();
    private boolean placingFromBatch;
    private int taskClaimTicks;
    private int abandonCooldownTicks;
    private Phase lastLoggedPhase = Phase.IDLE;
    private int pickClaimStuckTicks;
    private int buildLogHeartbeat;
    private int outOfRangeSweepCooldown;
    private int agentLogCooldown;
    private boolean handlingUnreachableStand;
    private final Set<Long> layerPlaceFailures = new HashSet<>();
    private int layerPlaceFailTrackedLayer = Integer.MIN_VALUE;
    private final ConstructionChatBubbles chatBubbles = new ConstructionChatBubbles();

    public MaidConstructionBehavior() {
        super(Map.of(), Integer.MAX_VALUE);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!maid.getTask().getUid().equals(TaskConstruction.UID) || maid.isSleeping()) {
            // #region agent log
            agentLog("H2", "MaidConstructionBehavior.checkExtraStartConditions", "start_blocked", d -> {
                d.addProperty("maid", ConstructionDebugIds.maid(maid));
                d.addProperty("onConstructionTask", maid.getTask().getUid().equals(TaskConstruction.UID));
                d.addProperty("sleeping", maid.isSleeping());
            });
            // #endregion
            return false;
        }
        Optional<BuildJob> job = ConstructionMaidBinding.jobOf(maid);
        if (job.isEmpty()) {
            BuildJobService.tryBindMaid(level, maid);
            job = ConstructionMaidBinding.jobOf(maid);
        }
        boolean ok = job.map(j -> !j.isPaused()).orElse(false);
        if (!ok) {
            // #region agent log
            final boolean jobBound = job.isPresent();
            final boolean jobPaused = job.map(BuildJob::isPaused).orElse(true);
            agentLog("H2", "MaidConstructionBehavior.checkExtraStartConditions", "start_blocked_job", d -> {
                d.addProperty("maid", ConstructionDebugIds.maid(maid));
                d.addProperty("jobBound", jobBound);
                d.addProperty("jobPaused", jobPaused);
            });
            // #endregion
        }
        return ok;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        ConstructionMaidBrain.prepareForActiveWork(maid);
        BuildDebugLog.behaviorStarted(maid);
        resetWorkState();
        // #region agent log
        AgentDebugLog.log("H3", "MaidConstructionBehavior.start", "behavior_started", agentMaidState(maid, null));
        // #endregion
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // #region agent log
        AgentDebugLog.log("H2", "MaidConstructionBehavior.stop", "behavior_stopped", agentMaidState(maid, null));
        // #endregion
        if (currentTask != null) {
            ConstructionMaidBinding.jobOf(maid).ifPresent(job -> job.releaseTask(currentTask, maid.getUUID()));
        }
        ConstructionMaidBinding.jobOf(maid).ifPresent(job -> {
            job.releaseAllClaimsFor(maid.getUUID());
            job.releaseScaffoldColumns(maid.getUUID());
            if (job.isPaused()) {
                ConstructionMaidFollow.releaseForIdle(maid);
            }
        });
        resetWorkState();
        ConstructionMaidBinding.clearMaterialWait(maid);
        releaseHover(maid);
        ConstructionPathBrainSync.stopNavigation(maid);
        ConstructionMaidBinding.clearWorkPhase(maid.getUUID());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        long tickStart = BuildPerfLog.start();
        boolean measureTick = false;
        try {
            Optional<BuildJob> jobOpt = ConstructionMaidBinding.jobOf(maid);
            if (jobOpt.isEmpty()) {
                BuildJobService.tryBindMaid(level, maid);
                jobOpt = ConstructionMaidBinding.jobOf(maid);
            }
            if (jobOpt.isEmpty()) {
                // #region agent log
                agentLog("H4", "MaidConstructionBehavior.tick", "tick_no_job", d -> {
                    d.addProperty("maid", ConstructionDebugIds.maid(maid));
                    d.addProperty("phase", phase.name());
                });
                // #endregion
                resetWorkState();
                releaseHover(maid);
                return;
            }
            BuildJob job = jobOpt.get();
            if (job.isPaused()) {
                // #region agent log
                agentLog("H4", "MaidConstructionBehavior.tick", "tick_job_paused", d ->
                        d.addProperty("maid", ConstructionDebugIds.maid(maid)));
                // #endregion
                ConstructionMaidBinding.clearMaterialWait(maid);
                ConstructionMaidFollow.releaseForIdle(maid);
                usingScaffoldNav = false;
                if (ConstructionWalkPolicy.shouldReleaseScaffoldHover(maid.isNoGravity(), usingScaffoldNav)) {
                    releaseHover(maid);
                }
                return;
            }
            if (job.isDone()) {
                // #region agent log
                agentLog("H4", "MaidConstructionBehavior.tick", "tick_job_done", d ->
                        d.addProperty("maid", ConstructionDebugIds.maid(maid)));
                // #endregion
                ConstructionMaidBinding.unbind(maid);
                resetWorkState();
                releaseHover(maid);
                return;
            }
            measureTick = true;
            ticksSinceHeavyNav++;
            ticksSinceStandMiss++;
            // #region agent log
            agentLog("H3", "MaidConstructionBehavior.tick", "tick_heartbeat", d -> {
                d.addProperty("maid", ConstructionDebugIds.maid(maid));
                d.addProperty("phase", phase.name());
                d.addProperty("brainWork", maid.getBrain().isActive(Activity.WORK));
                d.addProperty("schedule", maid.getScheduleDetail() == null ? "-" : maid.getScheduleDetail().getName());
                d.addProperty("pending", job.debugPendingCount());
                d.addProperty("activeLayer", job.activeLayer());
                d.addProperty("hasPlaceable", job.hasPlaceableTaskOnActiveLayer());
                d.addProperty("feet", maid.blockPosition().toShortString());
            });
            // #endregion

            BlockPos hoverFeet = maid.blockPosition();
            if (maid.isNoGravity()) {
                ScaffoldFallDebugLog.hoverForcedOff(maid, false, usingScaffoldNav);
                releaseHover(maid);
            }

            boolean waitingMaterial =
                    phase == Phase.WAITING_MATERIAL || ConstructionMaidBinding.isMaterialWait(maid);
            if (BuildMaterialWaitStayPolicy.shouldRunSiteRescue(waitingMaterial)) {
                tickRescueAndStuck(level, maid, job);
            }
            tickOutOfRangeScaffoldSweep(level, job);
            if (BuildMaterialWaitStayPolicy.shouldClearBody(waitingMaterial)) {
                CoverTerrainClear.tryClearBody(level, job, maid);
            }

            if (BuildMaterialWaitStayPolicy.shouldFreezeInPlace(waitingMaterial)) {
                long phaseStart = BuildPerfLog.start();
                tickMaterialWait(level, maid, job);
                BuildPerfLog.phase(maid, "WAITING_MATERIAL", phaseStart);
                return;
            }

            if (placeCooldown > 0) {
                placeCooldown--;
            }

            if (abandonCooldownTicks > 0) {
                abandonCooldownTicks--;
            }

            if (tickTaskClaimTimeout(level, maid, job)) {
                return;
            }

            long phaseStart = BuildPerfLog.start();
            String phaseName = phase.name();
            switch (phase) {
                case IDLE -> pickNext(level, maid, job);
                case MOVING -> doMove(level, maid, job);
                case FACING -> doFace(level, maid, job);
                case PLACING -> doPlace(level, maid, job, false);
                case BATCH -> drainBatch(level, maid, job);
                default -> {
                }
            }
            BuildPerfLog.phase(maid, phaseName, phaseStart);

            tickBuildDebug(level, maid, job);
        } catch (Throwable t) {
            LOGGER.error(
                    "[TMC-Build] maid tick failure maid={} phase={} task={} feet={}",
                    maid.getUUID(),
                    phase.name(),
                    currentTask == null ? "-" : currentTask.worldPos().toShortString(),
                    maid.blockPosition().toShortString(),
                    t);
            try {
                if (currentTask != null) {
                    ConstructionMaidBinding.jobOf(maid).ifPresent(job -> job.releaseTask(currentTask, maid.getUUID()));
                }
                ConstructionMaidBinding.jobOf(maid).ifPresent(job -> job.releaseAllClaimsFor(maid.getUUID()));
            } catch (Throwable ignored) {
            }
            try {
                releaseHover(maid);
                ConstructionPathBrainSync.stopNavigation(maid);
            } catch (Throwable ignored) {
            }
            resetWorkState();
        } finally {
            if (measureTick) {
                BuildPerfLog.maidTick(maid, phase.name(), tickStart);
            }
            ConstructionMaidBinding.setWorkPhase(maid.getUUID(), phase.name());
        }
    }

    private void tickBuildDebug(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (phase != lastLoggedPhase) {
            BuildDebugLog.phaseChange(maid, lastLoggedPhase.name(), phase.name(), describePhaseContext());
            lastLoggedPhase = phase;
        }
        if (maid.fallDistance > 3.0F && ConstructionMaidBinding.jobOf(maid).isPresent()) {
            ScaffoldFallDebugLog.highFallDistance(maid, maid.fallDistance, phase.name());
        }
        boolean active = phase != Phase.WAITING_MATERIAL
                && (hasActiveClaim() || abandonCooldownTicks > 0 || phase != Phase.IDLE);
        if (!active) {
            buildLogHeartbeat = 0;
            return;
        }
        buildLogHeartbeat++;
        if (buildLogHeartbeat < 10) {
            return;
        }
        buildLogHeartbeat = 0;
        BlockPos target = currentTask != null ? currentTask.worldPos() : null;
        boolean canInteract = target != null && BuildWorkPosition.canInteractNow(level, maid, target);
        BuildDebugLog.heartbeat(
                maid,
                phase.name(),
                target,
                workPosition,
                canInteract,
                placeCooldown,
                faceHoldRemaining,
                taskClaimTicks,
                abandonCooldownTicks,
                localBatch.size(),
                usingScaffoldNav,
                neighborhoodStallTicks(),
                arrivedBlocked.blockedTicks());
    }

    private String describePhaseContext() {
        if (currentTask != null) {
            return "task=" + currentTask.worldPos().toShortString();
        }
        if (!localBatch.isEmpty()) {
            return "batch=" + localBatch.size();
        }
        if (abandonCooldownTicks > 0) {
            return "abandonCd=" + abandonCooldownTicks;
        }
        return "-";
    }

    private void pickNext(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (abandonCooldownTicks > 0) {
            // #region agent log
            agentLog("H5", "MaidConstructionBehavior.pickNext", "pick_blocked_abandon_cd", d -> {
                d.addProperty("maid", ConstructionDebugIds.maid(maid));
                d.addProperty("abandonCooldownTicks", abandonCooldownTicks);
            });
            // #endregion
            return;
        }
        // Out-of-reach hold: with a stand prefer walk/teleport; without a stand vault immediately.
        if (currentTask != null) {
            if (workPosition == null) {
                // Claim without a stand must not sit in IDLE and feed stuck-teleport ping-pong.
                assignClaimedTask(
                        level,
                        maid,
                        job,
                        currentTask,
                        ConstructionMaidBinding.lastPlacedPos(maid.getUUID()));
                return;
            }
            BlockPos held = currentTask.worldPos();
            if (BuildWorkPosition.canInteractNow(level, maid, held)) {
                beginFacing(maid, held);
                return;
            }
            if (tryResolvePathStallToWorkStand(level, maid, job, held)) {
                return;
            }
            walkTo(level, maid, job, workPosition);
            phase = Phase.MOVING;
            return;
        }
        Optional<BlockPos> localAnchor = ConstructionMaidBinding.lastPlacedPos(maid.getUUID());
        if (tryResumeReachableBatch(level, maid, job)) {
            drainBatch(level, maid, job);
            return;
        }
        if (workPosition != null
                && maid.blockPosition().equals(workPosition)
                && currentTask != null
                && BuildWorkPosition.canInteractNow(level, maid, currentTask.worldPos())) {
            beginFacing(maid, currentTask.worldPos());
            return;
        }
        Optional<BuildTask> next = claimNextTask(job, maid, localAnchor);
        if (next.isEmpty()) {
            job.releaseAllClaimsFor(maid.getUUID());
            job.sweepUnplaceableClaims(level);
            if (tryResumeReachableBatch(level, maid, job)) {
                drainBatch(level, maid, job);
                return;
            }
            next = claimNextTask(job, maid, localAnchor);
        }
        if (next.isEmpty()) {
            job.probeCompletion(level);
            if (job.isDone()) {
                ConstructionMaidBinding.unbind(maid);
                resetWorkState();
                return;
            }
            if (job.hasClaimablePlaceableTaskOnActiveLayer(maid.getUUID())) {
                pickClaimStuckTicks++;
                if (pickClaimStuckTicks >= 40) {
                    job.sweepUnplaceableClaims(level);
                    pickClaimStuckTicks = 0;
                }
                MaterialDebugLog.pickNextStuck(maid.getUUID(), maid.getName().getString(), job,
                        "claimEmpty_but_hasPlaceableOnLayer");
                BuildDebugLog.pickNextStuck(maid, "claimEmpty_but_hasPlaceableOnLayer");
                // #region agent log
                AgentDebugLog.log("H1", "MaidConstructionBehavior.pickNext", "pick_claim_stuck", agentJobState(maid, job, d -> {
                    d.addProperty("pickClaimStuckTicks", pickClaimStuckTicks);
                }));
                // #endregion
                return;
            }
            if (job.hasDeferredWork() || job.debugUnreachableCount() > 0) {
                pickClaimStuckTicks++;
                job.refreshWorkQueue(level, true);
                job.sweepUnplaceableClaims(level);
                next = claimNextTask(job, maid, localAnchor);
                if (next.isPresent()) {
                    pickClaimStuckTicks = 0;
                    currentTask = next.get();
                    AgentDebugLog.log("H1", "MaidConstructionBehavior.pickNext", "pick_claimed", agentJobState(maid, job, d ->
                            d.addProperty("task", currentTask.worldPos().toShortString())));
                    assignClaimedTask(level, maid, job, currentTask, localAnchor);
                    return;
                }
                if (job.debugPendingCount() > 0) {
                    if (pickClaimStuckTicks == 1 || pickClaimStuckTicks % 40 == 0) {
                        BuildDebugLog.pickNextStuck(maid,
                                "deferred_recovery_pending_claim pending=" + job.debugPendingCount()
                                        + " deferred=" + job.deferredCount()
                                        + " unreachable=" + job.debugUnreachableCount());
                    }
                    if (pickClaimStuckTicks >= 40) {
                        job.sweepUnplaceableClaims(level);
                        pickClaimStuckTicks = 0;
                    }
                    // Normal queue still has cells — keep picking / material-wait instead of idling on deferred.
                } else {
                    if (pickClaimStuckTicks == 1 || pickClaimStuckTicks % 40 == 0) {
                        BuildDebugLog.pickNextStuck(maid,
                                "deferred_recovery pending=" + job.debugPendingCount()
                                        + " deferred=" + job.deferredCount()
                                        + " unreachable=" + job.debugUnreachableCount());
                    }
                    if (pickClaimStuckTicks >= 40) {
                        job.sweepUnplaceableClaims(level);
                        pickClaimStuckTicks = 0;
                    }
                    return;
                }
            }
            pickClaimStuckTicks = 0;
            Optional<String> missing = nearestMissingMaterial(job, maid, localAnchor);
            // #region agent log
            agentLog("H1", "MaidConstructionBehavior.pickNext", "pick_silent_idle", d -> {
                fillAgentMaidState(d, maid, job);
                d.addProperty("missingMaterial", missing.orElse("-"));
                d.addProperty("jobDone", job.isDone());
                d.addProperty("unreachable", job.debugUnreachableCount());
                d.addProperty("inProgress", job.debugInProgressCount());
            });
            // #endregion
            missing.ifPresent(blockId ->
                    maybeEnterMaterialWait(level, maid, job, blockId, "pickNext_noClaimable", null));
            return;
        }
        BuildTask claimed = next.get();
        pickClaimStuckTicks = 0;
        // #region agent log
        AgentDebugLog.log("H1", "MaidConstructionBehavior.pickNext", "pick_claimed", agentJobState(maid, job, d ->
                d.addProperty("task", claimed.worldPos().toShortString())));
        // #endregion
        // Pass the new claim without writing currentTask first — assignClaimedTask compares
        // previously held cell vs newly claimed for attempt-counter reset.
        assignClaimedTask(level, maid, job, claimed, localAnchor);
    }

    private void assignClaimedTask(
            ServerLevel level, EntityMaid maid, BuildJob job, BuildTask task, Optional<BlockPos> localAnchor) {
        BlockPos previousTaskPos = currentTask != null ? currentTask.worldPos() : null;
        String previousKey = previousTaskPos != null ? previousTaskPos.toShortString() : null;
        String nextKey = task.worldPos().toShortString();
        currentTask = task;
        if (BuildClaimAssignPolicy.shouldResetAttemptCounters(previousKey, nextKey)) {
            taskClaimTicks = 0;
            scaffoldAttempts = 0;
            scaffoldPath = List.of();
        }
        if (tryIsolatedAirImmediatePlace(level, maid, job, currentTask.worldPos())) {
            return;
        }
        BlockPos standTarget = currentTask.worldPos();
        long standKey = standTarget.asLong();
        boolean scanStand = WorkStandMissRescanPolicy.shouldScan(
                standKey == missedStandTarget, ticksSinceStandMiss);
        Optional<BlockPos> work = Optional.empty();
        if (scanStand) {
            work = pickWorkStand(level, maid, standTarget);
            if (work.isEmpty()) {
                CoverTerrainClear.tryClearObstructions(level, job, maid, standTarget, null);
                work = pickWorkStand(level, maid, standTarget);
            }
            if (work.isEmpty()) {
                missedStandTarget = standKey;
                ticksSinceStandMiss = 0;
            } else {
                missedStandTarget = Long.MIN_VALUE;
                ticksSinceStandMiss = WorkStandMissRescanPolicy.RESCAN_INTERVAL_TICKS;
            }
        }
        if (work.isEmpty()) {
            // #region agent log
            AgentDebugLog.log("H6", "MaidConstructionBehavior.assignClaimedTask", "no_work_stand", agentJobState(maid, job, d -> {
                d.addProperty("task", currentTask.worldPos().toShortString());
                d.addProperty("needsVerticalAccess", false);
                d.addProperty("geometricStand", "-");
            }));
            // #endregion
            if (tryNoStandOccupiedClearAndPlace(level, maid, job, currentTask.worldPos())) {
                return;
            }
            if (tryNoStandImmediateVaultPlace(level, maid, job, currentTask.worldPos())) {
                return;
            }
            tryUnreachableStandBudgeted(level, maid, job, currentTask.worldPos());
            return;
        }
        workPosition = ScaffoldColumnSupport.clampStandToColumn(level, job.scaffoldColumnRegistry(), work.get());
        // #region agent log
        AgentDebugLog.log("H1", "MaidConstructionBehavior.assignClaimedTask", "walk_to_work", agentJobState(maid, job, d -> {
            d.addProperty("task", currentTask.worldPos().toShortString());
            d.addProperty("work", workPosition.toShortString());
        }));
        // #endregion
        BuildDebugLog.claimed(
                maid,
                currentTask.worldPos(),
                workPosition,
                localAnchor.orElse(null));
        if (maid.blockPosition().equals(workPosition)
                && BuildWorkPosition.canInteractNow(level, maid, currentTask.worldPos())) {
            beginFacing(maid, currentTask.worldPos());
            return;
        }
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        walkTo(level, maid, job, workPosition);
        phase = Phase.MOVING;
    }

    private void doMove(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (currentTask == null) {
            phase = Phase.IDLE;
            return;
        }
        if (workPosition == null) {
            phase = Phase.IDLE;
            assignClaimedTask(level, maid, job, currentTask,
                    ConstructionMaidBinding.lastPlacedPos(maid.getUUID()));
            return;
        }
        BlockPos feet = maid.blockPosition();
        BlockPos target = currentTask.worldPos();
        CoverTerrainClear.tryClearObstructions(level, job, maid, target, workPosition);
        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
            ConstructionPathBrainSync.stopNavigation(maid);
            releaseHover(maid);
            arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
            clearScaffoldRoute();
            resetNeighborhoodStall();
            beginFacing(maid, target);
            return;
        }

        if (tryResolveAdjacentWorkStall(level, maid, job, target)) {
            return;
        }

        int scaffoldLayer = currentTask != null ? currentTask.layerIndex() : job.activeLayer();
        if (workPosition != null
                && ScaffoldNeedPolicy.allowsScaffoldPlanning(scaffoldLayer)
                && ScaffoldNeedPolicy.shouldForceScaffoldForVerticalGap(
                        feet.getY(), workPosition.getY(), false)
                && !usingScaffoldNav
                && (maid.getNavigation().isDone()
                        || BuildWorkGoalStallPolicy.isStalled(workGoalStall)
                        || neighborhoodStallTicks() >= 10)) {
            tryUnreachableStandBudgeted(level, maid, job, target);
            return;
        }

        if (!scaffoldPath.isEmpty()) {
            boolean adjacentSameLayer = feet.distManhattan(target) <= 1
                    && Math.abs(feet.getY() - target.getY()) <= 1;
            if (adjacentSameLayer) {
                clearScaffoldRoute();
            } else if (placeCooldown <= 0) {
                advanceOrReplanScaffold(level, maid, job, target);
                placeCooldown = BuildPlaceCadencePolicy.PLACE_INTERVAL_TICKS;
            }
        }
        walkTo(level, maid, job, workPosition);

        // workGoalStall is advanced only in tickRescueAndStuck — do not double-count here.
        boolean workGoalStalled = workPosition != null
                && BuildWorkGoalStallPolicy.isStalled(workGoalStall)
                && !BuildWorkPosition.canInteractNow(level, maid, target);

        if (BuildProgressStallPolicy.isStalled(progressStall) || workGoalStalled) {
            if (tryResolvePathStallToWorkStand(level, maid, job, target)) {
                return;
            }
            tryUnreachableStandBudgeted(level, maid, job, target);
            resetNeighborhoodStall();
            return;
        }

        double distWorkSqr = maid.distanceToSqr(
                workPosition.getX() + 0.5D,
                workPosition.getY(),
                workPosition.getZ() + 0.5D);
        boolean navDone = maid.getNavigation().isDone();
        arrivedBlocked = BuildArrivedPolicy.onTick(
                arrivedBlocked, distWorkSqr, navDone, BuildWorkPosition.canInteractNow(level, maid, target));
        if (!usingScaffoldNav && BuildArrivedPolicy.shouldGiveUp(arrivedBlocked)) {
            tryUnreachableStandBudgeted(level, maid, job, target);
        }
    }

    private Optional<BlockPos> pickWorkStand(ServerLevel level, EntityMaid maid, BlockPos target) {
        Optional<BlockPos> geometric = BuildWorkPosition.findNearestGeometric(level, maid, target);
        if (!BuildHeavyNavBudgetPolicy.standSearchNeedsPathProbe(geometric.isPresent())) {
            return Optional.empty();
        }
        if (tryHeavyNav()) {
            Optional<BlockPos> reachable = BuildWorkPosition.find(level, maid, target);
            if (reachable.isPresent()) {
                return reachable;
            }
        }
        return geometric;
    }

    private void tryUnreachableStandBudgeted(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (!tryHeavyNav()) {
            if (workPosition != null) {
                walkTo(level, maid, job, workPosition);
            }
            return;
        }
        handleUnreachableStand(level, maid, job, target);
    }

    private boolean tryHeavyNav() {
        if (!BuildHeavyNavBudget.tryAcquire(ticksSinceHeavyNav)) {
            return false;
        }
        ticksSinceHeavyNav = 0;
        return true;
    }

    /**
     * Scaffold first; missing scaffold → bubble and skip (no direct place);
     * consecutive plan failures → direct place. One cell per successful place.
     */
    private void handleUnreachableStand(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (handlingUnreachableStand) {
            // Re-entrant call from scaffold escalate — break the cycle without another tryScaffold.
            tryDirectPlaceOrDefer(level, maid, job, target, true);
            return;
        }
        handlingUnreachableStand = true;
        try {
            handleUnreachableStandBody(level, maid, job, target);
        } finally {
            handlingUnreachableStand = false;
        }
    }

    private void handleUnreachableStandBody(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (workPosition == null && tryNoStandOccupiedClearAndPlace(level, maid, job, target)) {
            return;
        }
        if (CoverTerrainClear.tryClearObstructions(level, job, maid, target, workPosition) > 0) {
            if (BuildWorkPosition.canInteractNow(level, maid, target)) {
                ConstructionPathBrainSync.stopNavigation(maid);
                releaseHover(maid);
                arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
                clearScaffoldRoute();
                resetNeighborhoodStall();
                beginFacing(maid, target);
                return;
            }
            Optional<BlockPos> work = BuildWorkPosition.find(level, maid, target);
            if (work.isEmpty()) {
                work = BuildWorkPosition.findNearestGeometric(level, maid, target);
            }
            if (work.isPresent()) {
                workPosition = ScaffoldColumnSupport.clampStandToColumn(
                        level, job.scaffoldColumnRegistry(), work.get());
                arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
                walkTo(level, maid, job, workPosition);
                phase = Phase.MOVING;
                return;
            }
        }
        ConstructionPathBrainSync.stopNavigation(maid);
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;

        // No work stand: hold for vault place instead of defer-releasing the claim.
        boolean mayDefer = workPosition != null && mayDeferUnreachableTarget(level, maid, job);
        if (mayDefer && tryDeferUnreachableTarget(level, maid, job, target)) {
            return;
        }
        if (tryVaultDirectPlaceAfterNavFailures(level, maid, job, target)) {
            return;
        }
        // #region agent log
        if (!mayDefer && job.hasOtherPlaceableTaskOnActiveLayer(target)) {
            AgentDebugLog.log("H7", "MaidConstructionBehavior.handleUnreachableStand", "skip_defer_for_recovery",
                    agentJobState(maid, job, d -> {
                        d.addProperty("task", target.toShortString());
                        d.addProperty("neighborhoodStall", neighborhoodStallTicks());
                        d.addProperty("onScaffold", isOnScaffold(level, maid));
                        d.addProperty("nearScaffold", isNearJobScaffold(maid, job));
                    }));
        }
        // #endregion

        if (currentTask != null && job.isUnreachable(currentTask)) {
            beginFacing(maid, target);
            return;
        }

        int scaffoldLayer = currentTask != null ? currentTask.layerIndex() : job.activeLayer();
        if (!ScaffoldNeedPolicy.allowsScaffoldPlanning(scaffoldLayer)) {
            if (tryDirectPlaceOrDefer(level, maid, job, target, true)) {
                return;
            }
            if (workPosition != null) {
                walkTo(level, maid, job, workPosition);
                phase = Phase.MOVING;
            } else {
                beginFacing(maid, target);
            }
            return;
        }

        ScaffoldDebugLog.tryStart(
                maid,
                maid.blockPosition(),
                maid.getRestrictRadius(),
                target,
                scaffoldAttempts,
                job.materials().hasMaterial(ScaffoldLedger.BLOCK_ID),
                ScaffoldMaterials.countInMaid(maid),
                job.scaffolds().size());

        applyScaffoldResult(level, maid, job, target, ScaffoldService.tryScaffold(level, job, maid, target));
    }

    /**
     * Locked path: advance without re-planning. Replan only when the maid leaves the path or the
     * next place cell is blocked.
     */
    private void advanceOrReplanScaffold(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        BlockPos feet = maid.blockPosition();
        List<ScaffoldLayout.Cell> lockedCells = new java.util.ArrayList<>(scaffoldPath.size());
        for (BlockPos pos : scaffoldPath) {
            lockedCells.add(new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ()));
        }
        boolean offPath = ScaffoldPathLockPolicy.isOffLockedPath(
                feet.getX(), feet.getY(), feet.getZ(), lockedCells);
        if (ScaffoldPathLockPolicy.shouldReplan(true, false, offPath)) {
            // #region agent log
            var dbg = AgentDebugLog.data();
            dbg.addProperty("maid", ConstructionDebugIds.maid(maid));
            dbg.addProperty("feet", feet.toShortString());
            dbg.addProperty("offPath", offPath);
            dbg.addProperty("pathLen", scaffoldPath.size());
            AgentDebugLog.log("H4", "MaidConstructionBehavior.advanceOrReplan", "replan_off_path", dbg);
            // #endregion
            ScaffoldDebugLog.tryStart(
                    maid,
                    feet,
                    maid.getRestrictRadius(),
                    target,
                    scaffoldAttempts,
                    job.materials().hasMaterial(ScaffoldLedger.BLOCK_ID),
                    ScaffoldMaterials.countInMaid(maid),
                    job.scaffolds().size());
            if (!tryHeavyNav()) {
                return;
            }
            scaffoldPath = List.of();
            applyScaffoldResult(level, maid, job, target, ScaffoldService.tryScaffold(level, job, maid, target));
            return;
        }
        ScaffoldService.AttemptResult advanced = ScaffoldService.advanceLockedPath(
                level,
                job,
                maid,
                scaffoldPath,
                scaffoldFinalWorkFeet != null ? scaffoldFinalWorkFeet : workPosition);
        // #region agent log
        var advDbg = AgentDebugLog.data();
        advDbg.addProperty("maid", ConstructionDebugIds.maid(maid));
        advDbg.addProperty("feet", feet.toShortString());
        advDbg.addProperty("attempt", advanced.attempt().name());
        advDbg.addProperty("workFeet", advanced.workFeet() != null ? advanced.workFeet().toShortString() : "-");
        advDbg.addProperty("pathLen", scaffoldPath.size());
        AgentDebugLog.log("H4", "MaidConstructionBehavior.advanceOrReplan", "advance_locked", advDbg);
        // #endregion
        if (advanced.attempt() == ScaffoldService.Attempt.PLACE_BLOCKED) {
            if (!tryHeavyNav()) {
                return;
            }
            scaffoldPath = List.of();
            applyScaffoldResult(level, maid, job, target, ScaffoldService.tryScaffold(level, job, maid, target));
            return;
        }
        applyScaffoldResult(level, maid, job, target, advanced);
    }

    private void applyScaffoldResult(
            ServerLevel level,
            EntityMaid maid,
            BuildJob job,
            BlockPos target,
            ScaffoldService.AttemptResult result) {
        ScaffoldDebugLog.result(
                maid,
                result.attempt(),
                result.workFeet(),
                result.placedCount(),
                target);

        switch (result.attempt()) {
            case MISSING_MATERIAL -> {
                chatBubbles.requestScaffold(maid);
                if (currentTask != null) {
                    job.releaseTask(currentTask, maid.getUUID());
                    currentTask = null;
                }
                workPosition = null;
                clearScaffoldRoute();
                scaffoldAttempts = 0;
                phase = Phase.IDLE;
            }
            case NOT_NEEDED, PLACED, WALK_TO -> {
                if (result.path() != null && !result.path().isEmpty()) {
                    scaffoldPath = result.path();
                    if (scaffoldFinalWorkFeet == null) {
                        BlockPos routeFinal = result.routeFinalWorkFeet() != null
                                ? result.routeFinalWorkFeet()
                                : result.workFeet();
                        if (routeFinal != null) {
                            scaffoldFinalWorkFeet = routeFinal;
                            workPosition = ScaffoldColumnSupport.clampStandToColumn(
                                    level, job.scaffoldColumnRegistry(), routeFinal);
                        }
                    }
                    refreshScaffoldWaypoints(maid);
                } else if (result.path() != null) {
                    clearScaffoldRoute();
                }
                // After a budgeted tryScaffold, do not fan out canPathReach again — geometric stand.
                Optional<BlockPos> work = scaffoldFinalWorkFeet != null
                        ? Optional.of(scaffoldFinalWorkFeet)
                        : result.routeFinalWorkFeet() != null
                                ? Optional.of(result.routeFinalWorkFeet())
                                : result.workFeet() != null
                                        ? Optional.of(result.workFeet())
                                        : BuildWorkPosition.findNearestGeometric(level, maid, target);
                BlockPos feet = maid.blockPosition();
                boolean nearSameWork = work.isPresent()
                        && workPosition != null
                        && work.get().equals(workPosition)
                        && ConstructionWalkPolicy.shouldSeekAlternateStand(
                                true, feet.distManhattan(workPosition));
                boolean stalledNearWork = work.isPresent()
                        && workPosition != null
                        && feet.distManhattan(workPosition) <= 2
                        && (work.get().equals(workPosition) || work.get().distManhattan(workPosition) <= 2);
                if (result.attempt() == ScaffoldService.Attempt.NOT_NEEDED
                        && (nearSameWork || stalledNearWork)) {
                    notNeededWhileStuck++;
                    Optional<BlockPos> alternate = findAlternateWorkStand(level, maid, target, workPosition);
                    if (alternate.isPresent()) {
                        workPosition = ScaffoldColumnSupport.clampStandToColumn(
                                level, job.scaffoldColumnRegistry(), alternate.get());
                        scaffoldFinalWorkFeet = workPosition;
                        clearScaffoldRoute();
                        walkTo(level, maid, job, workPosition);
                        phase = Phase.MOVING;
                        return;
                    }
                    if (ScaffoldPlanFailPolicy.afterNotNeededWhileStuck(
                                    notNeededWhileStuck, isClimbingScaffoldOnJob(level, maid, workPosition))
                            == ScaffoldPlanFailPolicy.Next.DIRECT_PLACE) {
                        notNeededWhileStuck = 0;
                        if (tryDirectPlaceOrDefer(level, maid, job, target, true)) {
                            return;
                        }
                    }
                } else {
                    notNeededWhileStuck = 0;
                    scaffoldAttempts = 0;
                }
                if (work.isPresent() && scaffoldPath.isEmpty()) {
                    workPosition = ScaffoldColumnSupport.clampStandToColumn(
                            level, job.scaffoldColumnRegistry(), work.get());
                }
                if (workPosition != null) {
                    walkTo(level, maid, job, workPosition);
                    phase = Phase.MOVING;
                } else {
                    phase = Phase.IDLE;
                }
            }
            case OUT_OF_RANGE -> {
                scaffoldPath = List.of();
                scaffoldAttempts = 0;
                notNeededWhileStuck = 0;
                walkTo(level, maid, job, workPosition != null ? workPosition : target);
                phase = Phase.MOVING;
            }
            case PLACE_BLOCKED -> {
                // Fresh plan already failed its first place — treat like plan fail, do not recurse.
                scaffoldPath = List.of();
                scaffoldAttempts++;
                boolean climbing = isClimbingScaffoldOnJob(level, maid, workPosition);
                ScaffoldPlaceBlockedPolicy.Next blockedNext =
                        ScaffoldPlaceBlockedPolicy.afterPlaceBlocked(scaffoldAttempts, climbing);
                if (blockedNext == ScaffoldPlaceBlockedPolicy.Next.TELEPORT_WORK && workPosition != null) {
                    BlockPos teleportTarget =
                            scaffoldFinalWorkFeet != null ? scaffoldFinalWorkFeet : workPosition;
                    if (tryTeleportToWorkStandOrAlternate(level, maid, job, target, teleportTarget)) {
                        ticksSinceStuckTeleport = 0;
                        resetNeighborhoodStall();
                        lastVanillaWalkTarget = null;
                        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
                            ConstructionPathBrainSync.stopNavigation(maid);
                            clearScaffoldRoute();
                            beginFacing(maid, target);
                        } else {
                            walkTo(level, maid, job, workPosition);
                            phase = Phase.MOVING;
                        }
                        return;
                    }
                }
                if (workPosition == null
                        || blockedNext == ScaffoldPlaceBlockedPolicy.Next.DIRECT_PLACE) {
                    if (workPosition == null) {
                        beginDirectPlace(level, maid, job, target);
                        return;
                    }
                    if (tryDirectPlaceOrDefer(level, maid, job, target, true)) {
                        return;
                    }
                } else if (workPosition != null) {
                    walkTo(level, maid, job, workPosition);
                    phase = Phase.MOVING;
                } else {
                    beginDirectPlace(level, maid, job, target);
                }
            }
            case PLAN_FAILED -> {
                scaffoldAttempts++;
                if (workPosition == null
                        || ScaffoldPlanFailPolicy.afterPlanFailed(
                                scaffoldAttempts, isClimbingScaffoldOnJob(level, maid, workPosition))
                                == ScaffoldPlanFailPolicy.Next.DIRECT_PLACE) {
                    scaffoldPath = List.of();
                    // No work stand (e.g. noSolidStand): vault-place immediately — scaffold cannot
                    // plan without a stand, and holding IDLE only stalls the whole crew.
                    if (workPosition == null
                            && tryNoStandImmediateVaultPlace(level, maid, job, target)) {
                        return;
                    }
                    if (tryDirectPlaceOrDefer(level, maid, job, target, true)) {
                        return;
                    }
                } else if (workPosition != null) {
                    walkTo(level, maid, job, workPosition);
                    phase = Phase.MOVING;
                } else {
                    beginDirectPlace(level, maid, job, target);
                }
            }
        }
    }

    private static boolean isClimbingScaffoldOnJob(ServerLevel level, EntityMaid maid, BlockPos workPosition) {
        if (workPosition == null) {
            return false;
        }
        BlockPos feet = maid.blockPosition();
        boolean onScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet))
                || ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet.below()));
        return onScaffold && workPosition.getY() > feet.getY();
    }

    private void beginFacing(EntityMaid maid, BlockPos target) {
        faceTarget(maid, target);
        placingFromBatch = false;
        faceHoldRemaining = BuildPlaceCadencePolicy.faceHoldTicks(false, isFacingTarget(maid, target));
        phase = Phase.FACING;
    }

    private static Optional<BlockPos> findAlternateWorkStand(
            ServerLevel level, EntityMaid maid, BlockPos target, BlockPos avoid) {
        List<BlockPos> candidates = BuildWorkPosition.gatherCandidates(level, maid, target);
        Optional<BlockPos> fallback = Optional.empty();
        for (BlockPos candidate : candidates) {
            if (avoid != null && candidate.equals(avoid)) {
                continue;
            }
            if (candidate.equals(maid.blockPosition())) {
                continue;
            }
            // Prefer stands at/below the target layer; air-above-tower stands cause top bobbing.
            if (candidate.getY() > target.getY()) {
                if (fallback.isEmpty()) {
                    fallback = Optional.of(candidate);
                }
                continue;
            }
            return Optional.of(candidate);
        }
        return fallback;
    }

    private void beginFacingFromBatch(EntityMaid maid, BlockPos target) {
        faceTarget(maid, target);
        placingFromBatch = true;
        faceHoldRemaining = BuildPlaceCadencePolicy.faceHoldTicks(true, isFacingTarget(maid, target));
        phase = Phase.FACING;
    }

    private void doFace(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (currentTask == null) {
            phase = Phase.IDLE;
            return;
        }
        faceTarget(maid, currentTask.worldPos());
        if (faceHoldRemaining > 0) {
            faceHoldRemaining--;
            return;
        }
        phase = Phase.PLACING;
        doPlace(level, maid, job, placingFromBatch);
    }

    private void doPlace(ServerLevel level, EntityMaid maid, BuildJob job, boolean fromBatch) {
        if (currentTask == null) {
            phase = Phase.IDLE;
            return;
        }
        if (placeCooldown > 0) {
            return;
        }

        boolean unreachable = job.isUnreachable(currentTask);
        if (!BuildWorkPosition.canInteractNow(level, maid, currentTask.worldPos())) {
            BuildDebugLog.placeFailed(maid, currentTask.worldPos(),
                    unreachable ? "cannot_interact_unreachable" : "cannot_interact_release");
            recordPlaceFailure(job, currentTask.worldPos());
            job.releaseTask(currentTask, maid.getUUID());
            currentTask = null;
            phase = Phase.IDLE;
            return;
        }

        if (!job.materials().hasMaterial(currentTask.raw().name())) {
            BuildTask task = currentTask;
            job.releaseTask(task, maid.getUUID());
            currentTask = null;
            maybeEnterMaterialWait(level, maid, job, task.raw().name(), "doPlace_precheck", task.worldPos());
            return;
        }

        faceTarget(maid, currentTask.worldPos());
        maid.swing(InteractionHand.MAIN_HAND);

        BlockPos placedPos = currentTask.worldPos();
        BuildTask task = currentTask;
        if (!job.tryPlace(level, task, maid)) {
            if (job.isDeferred(task)) {
                job.releaseTask(task, maid.getUUID());
                job.refreshWorkQueue(level);
                long packed = BuildJob.pack(task.worldPos());
                boolean repeatFail = layerPlaceFailures.contains(packed);
                recordPlaceFailure(job, task.worldPos());

                if (!job.canSurviveAt(level, task)) {
                    if (job.awaitsSchematicSupport(level, task)) {
                        BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed_await_schematic");
                        currentTask = null;
                        placingFromBatch = false;
                        phase = Phase.IDLE;
                        return;
                    }
                    job.failTask(task);
                    BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed_no_support");
                    currentTask = null;
                    placingFromBatch = false;
                    phase = Phase.IDLE;
                    return;
                }
                if (repeatFail) {
                    job.failTask(task);
                    BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed_repeat");
                    currentTask = null;
                    placingFromBatch = false;
                    phase = Phase.IDLE;
                    return;
                }
                currentTask = task;
                placingFromBatch = false;
                BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed_deferred");
                job.prepareDirectPlace(task);
                beginDirectPlace(level, maid, job, task.worldPos());
                return;
            } else if (!job.materials().hasMaterial(task.raw().name())) {
                job.releaseTask(task, maid.getUUID());
                boolean waiting = maybeEnterMaterialWait(
                        level, maid, job, task.raw().name(), "doPlace_tryPlaceFail", task.worldPos());
                job.finishIfComplete(level);
                currentTask = null;
                placingFromBatch = false;
                recordPlaceFailure(job, task.worldPos());
                BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed");
                if (!waiting) {
                    phase = Phase.IDLE;
                }
                return;
            } else if (!unreachable) {
                job.markUnreachable(task);
                if (!BuildPlacementCheck.wouldOccupyEntity(maid, task.worldPos())) {
                    job.tryPlace(level, task, maid);
                } else {
                    job.markDeferred(task);
                }
            } else {
                job.releaseTask(task, maid.getUUID());
            }
            job.finishIfComplete(level);
            currentTask = null;
            placingFromBatch = false;
            recordPlaceFailure(job, task.worldPos());
            BuildDebugLog.placeFailed(maid, task.worldPos(), "tryPlace_failed");
            phase = Phase.IDLE;
            return;
        }

        BuildDebugLog.placed(maid, placedPos, fromBatch, localBatch.size());
        progressStall = BuildProgressStallPolicy.State.EMPTY;
        ticksSinceLastPlace = 0;
        layerPlaceFailures.remove(BuildJob.pack(placedPos));
        ConstructionMaidBinding.setLastPlacedPos(maid, placedPos);
        syncProgress(level, job, placedPos);
        if (!fromBatch) {
            discardLocalBatch(job, maid);
            syncLayerPlaceFailures(job);
            localBatch.addAll(job.claimReachableBatch(
                    level, maid, maid.blockPosition(), BuildJob.LOCAL_RADIUS, layerPlaceFailures));
        }
        placeCooldown = BuildPlaceCadencePolicy.PLACE_INTERVAL_TICKS;
        currentTask = null;
        placingFromBatch = false;
        phase = localBatch.isEmpty() ? Phase.IDLE : Phase.BATCH;
    }

    private void drainBatch(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (placeCooldown > 0) {
            return;
        }
        while (!localBatch.isEmpty()) {
            BuildTask task = localBatch.pollFirst();
            if (task == null) {
                break;
            }
            if (!job.materials().hasMaterial(task.raw().name())) {
                job.releaseTask(task, maid.getUUID());
                continue;
            }
            currentTask = task;
            taskClaimTicks = 0;
            BuildDebugLog.batchNext(maid, task.worldPos(), localBatch.size());
            beginFacingFromBatch(maid, task.worldPos());
            doFace(level, maid, job);
            if (placeCooldown > 0 || phase == Phase.WAITING_MATERIAL || phase == Phase.FACING) {
                return;
            }
        }
        phase = Phase.IDLE;
    }

    /** @return true when the maid entered material wait */
    private boolean maybeEnterMaterialWait(
            ServerLevel level, EntityMaid maid, BuildJob job, String blockId, String trigger, BlockPos taskPos) {
        if (job.materials().hasMaterial(blockId) || job.hasPlaceableTaskOnActiveLayer()) {
            MaterialDebugLog.materialCheckFailed(
                    trigger,
                    maid.getUUID(),
                    maid.getName().getString(),
                    job,
                    blockId,
                    taskPos,
                    job.materials().hasMaterial(blockId)
                            ? "vault_not_empty"
                            : "otherPlaceableOnLayer");
            phase = Phase.IDLE;
            return false;
        }
        enterMaterialWait(level, maid, job, blockId, trigger, taskPos);
        return true;
    }

    private void enterMaterialWait(
            ServerLevel level, EntityMaid maid, BuildJob job, String blockId, String trigger, BlockPos taskPos) {
        if (job.materials().hasMaterial(blockId)) {
            phase = Phase.IDLE;
            return;
        }
        if (!job.stillNeedsVaultMaterial(blockId)) {
            job.reconcileStalePending(level);
            ConstructionMaidBinding.clearMaterialWait(maid);
            phase = Phase.IDLE;
            syncProgress(level, job, null);
            return;
        }
        MaterialDebugLog.enterMaterialWait(
                maid.getUUID(), maid.getName().getString(), job, blockId, trigger, taskPos);
        ConstructionMaidBinding.setMaterialWait(maid, blockId);
        ConstructionMaidFollow.releaseForIdle(maid);
        ConstructionMaidFollow.pinToCurrentCell(maid);
        currentTask = null;
        workPosition = null;
        discardLocalBatch(job, maid);
        job.releaseAllClaimsFor(maid.getUUID());
        faceHoldRemaining = 0;
        materialCheckCooldown = 0;
        materialVaultRescanCooldown = MaterialWaitVaultRescanPolicy.arm();
        placingFromBatch = false;
        phase = Phase.WAITING_MATERIAL;
        chatBubbles.requestMaterial(maid, blockId);
        syncProgress(level, job, null);
    }

    private void tickMaterialWait(ServerLevel level, EntityMaid maid, BuildJob job) {
        Optional<String> waitBlock = ConstructionMaidBinding.materialWaitBlockId(maid.getUUID());
        if (waitBlock.isEmpty()) {
            phase = Phase.IDLE;
            return;
        }
        String blockId = waitBlock.get();
        ConstructionMaidFollow.pinToCurrentCell(maid);
        materialVaultRescanCooldown--;
        if (MaterialWaitVaultRescanPolicy.due(materialVaultRescanCooldown)) {
            StockIndexedMaterialSource.requestRebuild(job.materials());
            materialVaultRescanCooldown = MaterialWaitVaultRescanPolicy.arm();
            materialCheckCooldown = 0;
        }
        materialCheckCooldown--;
        if (materialCheckCooldown > 0) {
            return;
        }
        materialCheckCooldown = MATERIAL_CHECK_INTERVAL;

        if (!job.stillNeedsVaultMaterial(blockId)) {
            job.reconcileStalePending(level);
            ConstructionMaidBinding.clearMaterialWait(maid);
            ConstructionMaidFollow.resumeActiveWork(maid);
            phase = Phase.IDLE;
            syncProgress(level, job, null);
            return;
        }

        boolean hasWaitBlock = job.materials().hasMaterial(blockId);
        boolean hasPlaceableOnLayer = job.hasPlaceableTaskOnActiveLayer();
        if (hasWaitBlock || hasPlaceableOnLayer) {
            MaterialDebugLog.materialWaitResume(
                    maid.getUUID(),
                    maid.getName().getString(),
                    job,
                    blockId,
                    hasWaitBlock,
                    hasPlaceableOnLayer);
            ConstructionMaidBinding.clearMaterialWait(maid);
            ConstructionMaidFollow.resumeActiveWork(maid);
            phase = Phase.IDLE;
            syncProgress(level, job, null);
            return;
        }

        MaterialDebugLog.materialWaitPoll(
                maid.getUUID(), maid.getName().getString(), job, blockId, hasWaitBlock, hasPlaceableOnLayer);
        if (!hasWaitBlock) {
            chatBubbles.requestMaterial(maid, blockId);
        }
    }

    private Optional<BuildTask> claimNextTask(BuildJob job, EntityMaid maid, Optional<BlockPos> localAnchor) {
        syncLayerPlaceFailures(job);
        return localAnchor
                .map(anchor -> job.claimNearest(maid, anchor, true, layerPlaceFailures))
                .orElseGet(() -> job.claimNearest(maid, null, true, layerPlaceFailures));
    }

    /** Before first placement: nearest to maid; after that: nearest to last placed block. */

    private void syncLayerPlaceFailures(BuildJob job) {
        int layer = job.activeLayer();
        if (layer != layerPlaceFailTrackedLayer) {
            layerPlaceFailures.clear();
            layerPlaceFailTrackedLayer = layer;
        }
    }

    private void recordPlaceFailure(BuildJob job, BlockPos pos) {
        syncLayerPlaceFailures(job);
        layerPlaceFailures.add(BuildJob.pack(pos));
    }

    private void recordReachFailure(BuildJob job, BlockPos pos) {
        syncLayerPlaceFailures(job);
        layerPlaceFailures.add(BuildJob.pack(pos));
    }

    /**
     * Same-layer reach failure with alternatives: release this target and pick another before
     * scaffolding / direct-place loops on the same cell.
     */
    private boolean mayDeferUnreachableTarget(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (isOnScaffold(level, maid) || isNearJobScaffold(maid, job)) {
            return false;
        }
        boolean idleWithoutClaim = phase == Phase.IDLE && !hasActiveClaim();
        if (!idleWithoutClaim && BuildProgressStallPolicy.isStalled(progressStall)) {
            return false;
        }
        return !BuildTaskClaimTimeoutPolicy.shouldSuppressDefer(
                neighborhoodStallTicks(), idleWithoutClaim);
    }

    private static boolean isNearJobScaffold(EntityMaid maid, BuildJob job) {
        BlockPos feet = maid.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (job.scaffoldColumnRegistry().at(feet.getX() + dx, feet.getZ() + dz).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOnScaffold(ServerLevel level, EntityMaid maid) {
        BlockPos feet = maid.blockPosition();
        return ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet))
                || ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet.below()));
    }

    private boolean tryDeferUnreachableTarget(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (currentTask == null || target == null) {
            return false;
        }
        if (!job.hasOtherPlaceableTaskOnActiveLayer(target)) {
            return false;
        }
        recordReachFailure(job, target);
        job.releaseTask(currentTask, maid.getUUID());
        currentTask = null;
        workPosition = null;
        scaffoldAttempts = 0;
        notNeededWhileStuck = 0;
        clearScaffoldRoute();
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        lastVanillaWalkTarget = null;
        ConstructionPathBrainSync.stopNavigation(maid);
        phase = Phase.IDLE;
        return true;
    }

    private boolean tryDirectPlaceOrDefer(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        return tryDirectPlaceOrDefer(level, maid, job, target, false);
    }

    /**
     * @param alreadyInScaffoldEscalate true when called from scaffold result handling
     *                                  ({@code PLAN_FAILED} / {@code PLACE_BLOCKED} / NOT_NEEDED stuck);
     *                                  must not re-enter {@link #handleUnreachableStand}.
     */
    private boolean tryDirectPlaceOrDefer(
            ServerLevel level,
            EntityMaid maid,
            BuildJob job,
            BlockPos target,
            boolean alreadyInScaffoldEscalate) {
        // Scaffold escalate (PLAN_FAILED / noSolidStand / …): vault immediately when no stand;
        // otherwise hold for vault accumulate. Defer-release here resets counters.
        if (workPosition == null && tryNoStandImmediateVaultPlace(level, maid, job, target)) {
            return true;
        }
        if (BuildVaultHoldClaimPolicy.allowDeferRelease(alreadyInScaffoldEscalate)
                && mayDeferUnreachableTarget(level, maid, job)
                && tryDeferUnreachableTarget(level, maid, job, target)) {
            return true;
        }
        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
            beginDirectPlace(level, maid, job, target);
            return true;
        }
        if (tryVaultDirectPlaceAfterNavFailures(level, maid, job, target)) {
            return true;
        }
        if (ScaffoldEscalatePolicy.allowScaffoldReplan(alreadyInScaffoldEscalate)) {
            tryUnreachableStandBudgeted(level, maid, job, target);
            return true;
        }
        // Scaffold already failed: hold claim in IDLE until vault place or timeout.
        beginDirectPlace(level, maid, job, target);
        return true;
    }

    /**
     * After repeated pathfinding failures on the same task cell, place from the material vault
     * without maid reach checks ({@link BuildJob#tryPlace} with {@code placer=null}).
     */
    private boolean tryVaultDirectPlaceAfterNavFailures(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (currentTask == null || target == null) {
            return false;
        }
        // Record while holding so the counter builds before the stall gate opens.
        int failures = job.recordPathNavFailure(level, target);
        if (taskClaimTicks < BuildPathNavFailPolicy.MIN_STALL_TICKS
                && neighborhoodStallTicks() < BuildPathNavFailPolicy.MIN_STALL_TICKS) {
            return false;
        }
        if (!ConstructionMaidBinding.mayVaultDirectPlace(maid.getUUID(), level.getGameTime())) {
            return false;
        }
        if (!BuildPathNavFailPolicy.shouldVaultDirectPlace(
                failures, taskClaimTicks, neighborhoodStallTicks(), workPosition != null)) {
            return false;
        }
        BuildTask task = currentTask;
        job.releaseTask(task, maid.getUUID());
        if (!job.tryPlace(level, task, null)) {
            BuildDebugLog.placeFailed(maid, task.worldPos(), "vault_direct_place_failed");
            phase = Phase.IDLE;
            currentTask = null;
            workPosition = null;
            clearScaffoldRoute();
            return true;
        }
        ConstructionMaidBinding.noteVaultDirectPlace(maid.getUUID(), level.getGameTime());
        BuildDebugLog.vaultDirectPlace(maid, task.worldPos(), failures);
        finishVaultDirectPlace(level, maid, job, task);
        return true;
    }

    /**
     * Target has no solid on sides or above (below ignored): vault-place immediately (no walk /
     * scaffold / nav-fail wait). Still respects canSurvive and materials.
     */
    private boolean tryIsolatedAirImmediatePlace(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (currentTask == null || target == null) {
            return false;
        }
        if (!BuildIsolatedAirPlacePolicy.allowImmediateVaultPlace(
                BuildIsolatedAir.sidesAndAboveAirOrReplaceable(level, target))) {
            return false;
        }
        CoverTerrainClear.tryHarvestOccupiedTargetNoStand(level, job, target);
        BuildTask task = currentTask;
        job.releaseTask(task, maid.getUUID());
        if (!job.tryPlace(level, task, null)) {
            BuildDebugLog.placeFailed(maid, task.worldPos(), "isolated_air_place_failed");
            phase = Phase.IDLE;
            currentTask = null;
            workPosition = null;
            clearScaffoldRoute();
            return true;
        }
        BuildDebugLog.isolatedAirPlace(maid, task.worldPos());
        finishVaultDirectPlace(level, maid, job, task);
        return true;
    }

    /**
     * No solid work stand: vault-place immediately (scaffold cannot plan without a stand). Still
     * respects canSurvive and materials.
     */
    private boolean tryNoStandImmediateVaultPlace(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (currentTask == null || target == null) {
            return false;
        }
        if (!BuildNoWorkStandPlacePolicy.shouldImmediateVaultPlace(workPosition != null)) {
            return false;
        }
        CoverTerrainClear.tryHarvestOccupiedTargetNoStand(level, job, target);
        BuildTask task = currentTask;
        job.releaseTask(task, maid.getUUID());
        if (!job.tryPlace(level, task, null)) {
            BuildDebugLog.placeFailed(maid, task.worldPos(), "no_stand_immediate_place_failed");
            phase = Phase.IDLE;
            currentTask = null;
            workPosition = null;
            clearScaffoldRoute();
            return true;
        }
        BuildDebugLog.noStandImmediatePlace(maid, task.worldPos());
        finishVaultDirectPlace(level, maid, job, task);
        return true;
    }

    /**
     * No reachable stand + target cell occupied by a wrong destroyable block: dig that cell into
     * the vault and place immediately (does not wait for nav-fail accumulation; ignores cover /
     * ignore-overlap toggles).
     */
    private boolean tryNoStandOccupiedClearAndPlace(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (currentTask == null || target == null || workPosition != null) {
            return false;
        }
        if (!CoverTerrainClear.tryHarvestOccupiedTargetNoStand(level, job, target)) {
            return false;
        }
        BuildTask task = currentTask;
        job.releaseTask(task, maid.getUUID());
        if (!job.tryPlace(level, task, null)) {
            BuildDebugLog.placeFailed(maid, task.worldPos(), "no_stand_occupied_place_failed");
            phase = Phase.IDLE;
            currentTask = null;
            workPosition = null;
            clearScaffoldRoute();
            return true;
        }
        BuildDebugLog.noStandOccupiedPlace(maid, task.worldPos());
        finishVaultDirectPlace(level, maid, job, task);
        return true;
    }

    private void finishVaultDirectPlace(
            ServerLevel level, EntityMaid maid, BuildJob job, BuildTask task) {
        progressStall = BuildProgressStallPolicy.State.EMPTY;
        workGoalStall = BuildWorkGoalStallPolicy.State.EMPTY;
        ticksSinceLastPlace = 0;
        layerPlaceFailures.remove(BuildJob.pack(task.worldPos()));
        job.clearPathNavFailure(task.worldPos());
        ConstructionMaidBinding.setLastPlacedPos(maid, task.worldPos());
        syncProgress(level, job, task.worldPos());
        currentTask = null;
        workPosition = null;
        placingFromBatch = false;
        clearScaffoldRoute();
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        notNeededWhileStuck = 0;
        scaffoldAttempts = 0;
        lastVanillaWalkTarget = null;
        ConstructionPathBrainSync.stopNavigation(maid);
        releaseHover(maid);
        phase = Phase.IDLE;
    }

    /**
     * Face for placement when already in reach. When out of reach with a work stand, walk / teleport
     * to the stand first — vault is only for no-stand cells.
     */
    private void beginDirectPlace(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        ConstructionPathBrainSync.stopNavigation(maid);
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        if (workPosition == null && tryNoStandOccupiedClearAndPlace(level, maid, job, target)) {
            return;
        }
        if (!BuildWorkPosition.canInteractNow(level, maid, target)) {
            if (workPosition == null && tryNoStandImmediateVaultPlace(level, maid, job, target)) {
                return;
            }
            if (workPosition != null) {
                if (tryResolvePathStallToWorkStand(level, maid, job, target)) {
                    return;
                }
                walkTo(level, maid, job, workPosition);
                phase = Phase.MOVING;
                return;
            }
            if (tryVaultDirectPlaceAfterNavFailures(level, maid, job, target)) {
                return;
            }
            // No stand: hold claim in IDLE until vault place or timeout.
            phase = Phase.IDLE;
            return;
        }
        if (currentTask != null) {
            if (!job.canSurviveAt(level, currentTask)) {
                if (job.awaitsSchematicSupport(level, currentTask)) {
                    job.markDeferred(currentTask);
                    currentTask = null;
                    phase = Phase.IDLE;
                    return;
                }
                job.failTask(currentTask);
                currentTask = null;
                phase = Phase.IDLE;
                return;
            }
            job.markUnreachable(currentTask);
        }
        clearScaffoldRoute();
        beginFacing(maid, target);
    }

    private static Optional<String> nearestMissingMaterial(
            BuildJob job, EntityMaid maid, Optional<BlockPos> localAnchor) {
        return localAnchor
                .map(anchor -> job.nearestMissingMaterialBlock(maid, anchor))
                .orElseGet(() -> job.nearestMissingMaterialBlock(maid));
    }

    private boolean tickTaskClaimTimeout(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (phase == Phase.WAITING_MATERIAL || !hasActiveClaim()) {
            return false;
        }
        taskClaimTicks++;
        // workGoalStall alone must not abandon here — tryRecover / work-stand teleport runs below.
        if (phase == Phase.MOVING
                && BuildTaskClaimTimeoutPolicy.shouldAbandonStalledMove(
                        taskClaimTicks, neighborhoodStallTicks())) {
            abandonCurrentTask(level, maid, job);
            return true;
        }
        if ((phase == Phase.FACING || phase == Phase.PLACING)
                && BuildTaskClaimTimeoutPolicy.shouldAbandonStalledPlace(
                        taskClaimTicks, neighborhoodStallTicks())) {
            if (currentTask != null) {
                job.failTask(currentTask);
            }
            abandonCurrentTask(level, maid, job);
            return true;
        }
        if (BuildProgressStallPolicy.isStalled(progressStall)
                || (phase == Phase.MOVING
                        && workPosition != null
                        && BuildWorkGoalStallPolicy.isStalled(workGoalStall))) {
            if (tryRecoverFromNeighborhoodStall(level, maid, job)) {
                return false;
            }
            BuildDebugLog.noProgress(maid, progressStall, workPosition);
            if (currentTask != null
                    && workPosition != null
                    && mayDeferUnreachableTarget(level, maid, job)
                    && tryDeferUnreachableTarget(level, maid, job, currentTask.worldPos())) {
                return true;
            }
            if (currentTask != null && workPosition == null) {
                BlockPos target = currentTask.worldPos();
                beginDirectPlace(level, maid, job, target);
                // Vault place may have cleared the claim; otherwise abandon after the stall window
                // instead of looping beginDirectPlace forever (frozen on-site after stuck-hop ban).
                if (!hasActiveClaim()) {
                    return true;
                }
                if (!BuildTaskClaimTimeoutPolicy.shouldAbandonNoWorkStand(
                        taskClaimTicks, neighborhoodStallTicks())) {
                    return true;
                }
            }
            if (currentTask != null) {
                job.markUnreachable(currentTask);
            }
            abandonCurrentTask(level, maid, job);
            return true;
        }
        if (!BuildTaskClaimTimeoutPolicy.shouldAbandon(taskClaimTicks)) {
            return false;
        }
        // Full timeout always releases (including work=-); never beginDirectPlace-loop here.
        if (currentTask != null) {
            job.markUnreachable(currentTask);
        }
        abandonCurrentTask(level, maid, job);
        return true;
    }

    private boolean hasActiveClaim() {
        return currentTask != null || !localBatch.isEmpty();
    }

    private void abandonCurrentTask(ServerLevel level, EntityMaid maid, BuildJob job) {
        BlockPos taskPos = currentTask != null
                ? currentTask.worldPos()
                : localBatch.peekFirst() != null ? localBatch.peekFirst().worldPos() : null;
        int held = taskClaimTicks;
        if (taskPos != null && job.hasOtherPlaceableTaskOnActiveLayer(taskPos)) {
            recordReachFailure(job, taskPos);
        }
        if (currentTask != null) {
            job.releaseTask(currentTask, maid.getUUID());
        }
        discardLocalBatch(job, maid);
        releaseHover(maid);
        ConstructionPathBrainSync.stopNavigation(maid);
        currentTask = null;
        workPosition = null;
        scaffoldAttempts = 0;
        notNeededWhileStuck = 0;
        clearScaffoldRoute();
        taskClaimTicks = 0;
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        resetNeighborhoodStall();
        lastVanillaWalkTarget = null;
        phase = Phase.IDLE;
        int maidsOnJob = ConstructionMaidBinding.keyOf(maid)
                .map(ConstructionMaidBinding::countOnJob)
                .orElse(1);
        int retryCooldown = BuildTaskClaimTimeoutPolicy.soloRetryCooldownTicks(maidsOnJob);
        abandonCooldownTicks = retryCooldown;
        BuildDebugLog.abandoned(maid, taskPos, held, maidsOnJob, retryCooldown);
    }

    /**
     * Claim every block the maid can already reach from her feet and continue batch placement
     */
    private boolean tryResumeReachableBatch(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (!localBatch.isEmpty()) {
            return true;
        }
        syncLayerPlaceFailures(job);
        List<BuildTask> reachable = job.claimReachableBatch(
                level, maid, maid.blockPosition(), BuildJob.LOCAL_RADIUS, layerPlaceFailures);
        if (reachable.isEmpty()) {
            return false;
        }
        localBatch.addAll(reachable);
        return true;
    }

    private static boolean isFacingTarget(EntityMaid maid, BlockPos target) {
        Vec3 eye = maid.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float wantY = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float wantX = (float) (-(Math.atan2(dy, horiz) * (180.0D / Math.PI)));
        return Math.abs(Mth.wrapDegrees(maid.getYRot() - wantY)) < 12.0F
                && Math.abs(maid.getXRot() - wantX) < 12.0F;
    }

    private static void faceTarget(EntityMaid maid, BlockPos target) {
        Vec3 eye = maid.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yRot = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float xRot = (float) (-(Math.atan2(dy, horiz) * (180.0D / Math.PI)));
        maid.setYRot(yRot);
        maid.setXRot(xRot);
        maid.setYHeadRot(yRot);
        maid.setYBodyRot(yRot);
        maid.getLookControl().setLookAt(center.x, center.y, center.z);
    }

    private static void syncProgress(ServerLevel level, BuildJob job, BlockPos placed) {
        BuildJobService.updateProjectByKey(level, job.key(), project -> {
            project.setPlacedBlocks(job.placedCount());
            project.setConsumedMaterials(job.consumedMaterials());
            project.setPlacedByBlockId(job.placedByBlockId());
            if (job.isDone()) {
                project.setStatus(BuildProject.Status.DONE);
                project.setPlacementFailures(job.placementFailures());
            } else if (job.isPaused()) {
                project.setStatus(BuildProject.Status.PAUSED);
            } else {
                project.setStatus(BuildProject.Status.BUILDING);
            }
        });
        job.finishIfComplete(level);
        if (job.isDone()) {
            BuildJobService.notifyJobComplete(job, level);
            return;
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new BuildProgressPacket(
                        job.key(),
                        placed == null ? BlockPos.ZERO : placed,
                        job.placedCount(),
                        job.totalBlocks(),
                        job.consumedMaterials(),
                        job.isDone(),
                        job.isPaused(),
                        job.pauseReason(),
                        ConstructionMaidBinding.materialWaitsForJob(job.key()),
                        job.placedByBlockId(),
                        job.isDone() ? job.placementFailures() : List.of()));
    }

    /** Tear down towers left outside the build range; scanning the whole ledger every tick is wasteful. */
    private void tickOutOfRangeScaffoldSweep(ServerLevel level, BuildJob job) {
        if (outOfRangeSweepCooldown > 0) {
            outOfRangeSweepCooldown--;
            return;
        }
        outOfRangeSweepCooldown = OUT_OF_RANGE_SWEEP_INTERVAL;
        ScaffoldService.reclaimOutOfRange(level, job);
    }

    private void tickRescueAndStuck(ServerLevel level, EntityMaid maid, BuildJob job) {
        ticksSinceStuckTeleport++;
        BlockPos feet = maid.blockPosition();
        boolean trackStall = BuildNeighborhoodStallTrackPolicy.shouldTrack(
                shouldTrackNeighborhoodStall(),
                phase == Phase.IDLE && job.hasPlaceableTaskOnActiveLayer(),
                hasActiveClaim());
        if (trackStall) {
            progressStall = BuildProgressStallPolicy.onTick(
                    progressStall, feet.getX(), feet.getY(), feet.getZ());
        } else {
            progressStall = BuildProgressStallPolicy.State.EMPTY;
        }
        if (phase == Phase.MOVING && workPosition != null) {
            workGoalStall = BuildWorkGoalStallPolicy.onTick(
                    workGoalStall, feet.distManhattan(workPosition));
        } else if (phase != Phase.MOVING) {
            workGoalStall = BuildWorkGoalStallPolicy.State.EMPTY;
        }
        ConstructionMaidFollow.StayArea area = ConstructionMaidFollow.stayAreaOf(maid);
        if (area == null) {
            return;
        }
        BlockPos before = maid.blockPosition();
        ConstructionSiteArrive.teleportIfAbsolutelyFar(maid, area);
        boolean idleWithoutClaim = phase == Phase.IDLE && !hasActiveClaim();
        boolean suppressStuck = ConstructionSiteArrivePolicy.shouldSuppressStuckTeleport(
                idleWithoutClaim, job.hasPlaceableTaskOnActiveLayer());
        boolean canUseStuckRescue = ConstructionSiteArrivePolicy.shouldAllowStuckRescue(
                suppressStuck,
                workPosition != null,
                phase == Phase.MOVING || hasActiveClaim() || usingScaffoldNav);
        int stallTicks = neighborhoodStallTicks();
        boolean teleported = canUseStuckRescue && ConstructionSiteArrive.teleportIfStuck(
                maid, area, workPosition, stallTicks, ticksSinceStuckTeleport);
        if (!teleported && canUseStuckRescue) {
            teleported = ConstructionSiteArrive.teleportIntoBuildRange(
                    maid, area, job.scaffoldBox(), workPosition, stallTicks, ticksSinceStuckTeleport);
        }
        if (teleported) {
            ticksSinceStuckTeleport = 0;
            lastVanillaWalkTarget = null;
            resetNeighborhoodStall();
        }
        if (!maid.blockPosition().equals(before)) {
            lastVanillaWalkTarget = null;
            releaseHover(maid);
            ConstructionPathBrainSync.stopNavigation(maid);
        }
        tickNoPlaceFallback(level, maid, job);
    }

    /**
     * Last-resort rescue: held a work stand but placed no schematic block for 20s → snap to stand.
     */
    private void tickNoPlaceFallback(ServerLevel level, EntityMaid maid, BuildJob job) {
        if (currentTask == null || phase == Phase.WAITING_MATERIAL) {
            return;
        }
        ticksSinceLastPlace++;
        if (!BuildNoPlaceTeleportPolicy.shouldTeleport(ticksSinceLastPlace, ticksSinceStuckTeleport)) {
            return;
        }
        BlockPos target = currentTask.worldPos();
        if (workPosition == null) {
            beginDirectPlace(level, maid, job, target);
            ticksSinceLastPlace = 0;
            ticksSinceStuckTeleport = 0;
            resetNeighborhoodStall();
            lastVanillaWalkTarget = null;
            return;
        }
        BlockPos teleportTarget = scaffoldFinalWorkFeet != null ? scaffoldFinalWorkFeet : workPosition;
        if (!tryTeleportToWorkStandOrAlternate(level, maid, job, target, teleportTarget)) {
            return;
        }
        ticksSinceLastPlace = 0;
        ticksSinceStuckTeleport = 0;
        resetNeighborhoodStall();
        lastVanillaWalkTarget = null;
        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
            ConstructionPathBrainSync.stopNavigation(maid);
            clearScaffoldRoute();
            beginFacing(maid, target);
        }
    }

    private void walkTo(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos work) {
        if (!scaffoldWaypoints.isEmpty()) {
            walkViaWaypoints(level, maid, job, work);
            return;
        }
        walkVanilla(level, maid, work);
    }

    private void walkViaWaypoints(ServerLevel level, EntityMaid maid, BuildJob job, BlockPos work) {
        BlockPos feet = maid.blockPosition();
        BlockPos target = ScaffoldWaypointPolicy.currentTarget(scaffoldWaypoints, scaffoldWaypointIndex, work);
        usingScaffoldNav = true;

        if (ScaffoldWaypointPolicy.hasArrived(feet, target)) {
            if (scaffoldWaypointIndex < scaffoldWaypoints.size() - 1) {
                scaffoldWaypointIndex++;
                lastVanillaWalkTarget = null;
                target = ScaffoldWaypointPolicy.currentTarget(scaffoldWaypoints, scaffoldWaypointIndex, work);
            } else if (ScaffoldWaypointPolicy.hasArrived(feet, work)) {
                ConstructionPathBrainSync.stopNavigation(maid);
                return;
            }
        }

        issueVanillaMoveTo(level, maid, target);
    }

    private void walkVanilla(ServerLevel level, EntityMaid maid, BlockPos work) {
        usingScaffoldNav = false;
        releaseHover(maid);
        issueVanillaMoveTo(level, maid, work);
    }

    private void issueVanillaMoveTo(ServerLevel level, EntityMaid maid, BlockPos work) {
        var nav = maid.getNavigation();
        boolean destChanged = lastVanillaWalkTarget == null || !lastVanillaWalkTarget.equals(work);
        boolean navInProgress = !nav.isDone() && nav.isInProgress();
        int manhattan = maid.blockPosition().distManhattan(work);
        ticksSinceVanillaPathIssue++;
        if (!ConstructionWalkPolicy.shouldRefreshVanillaPath(
                destChanged, navInProgress, nav.isDone(), manhattan, ticksSinceVanillaPathIssue)) {
            ConstructionPathBrainSync.syncFromNavigation(maid);
            return;
        }
        // Same tick as another heavy nav for this maid: keep the paid slot for createPath.
        if (ticksSinceHeavyNav != 0 && !tryHeavyNav()) {
            ConstructionPathBrainSync.syncFromNavigation(maid);
            return;
        }
        lastVanillaWalkTarget = work.immutable();
        ticksSinceVanillaPathIssue = 0;
        var path = nav.createPath(work, ConstructionWalkPolicy.vanillaPathRange(manhattan));
        if (path != null) {
            nav.moveTo(path, WALK_SPEED);
            ConstructionPathBrainSync.publish(maid, path);
            return;
        }
        nav.moveTo(work.getX() + 0.5D, work.getY(), work.getZ() + 0.5D, WALK_SPEED);
        ConstructionPathBrainSync.syncFromNavigation(maid);
    }

    /**
     * Shared path-stall recovery: scaffold waypoint route or direct vanilla walk, neighbourhood
     * stuck for {@link ScaffoldVanillaNavPolicy#PATH_STUCK_TELEPORT_TICKS} → snap to work stand.
     * Also runs while IDLE/FACING if a stand is held so vault cannot skip ahead of teleport.
     */
    private boolean tryResolvePathStallToWorkStand(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        int neighborhoodStall = neighborhoodStallTicks();
        int goalStall = BuildWorkGoalStallPolicy.stallTicks(workGoalStall);
        boolean stallThresholdMet =
                ScaffoldVanillaNavPolicy.shouldTeleportToWork(neighborhoodStall, goalStall);
        if (!ScaffoldVanillaNavPolicy.maySnapWhileHoldingWorkStand(workPosition != null, stallThresholdMet)) {
            return false;
        }
        if (phase == Phase.MOVING
                && !ScaffoldVanillaNavPolicy.maySnapToWorkDespiteNav(
                        isPathfindingTowardWork(maid), stallThresholdMet)) {
            return false;
        }
        int manhattan = maid.blockPosition().distManhattan(workPosition);
        if (manhattan >= 1 && manhattan <= 2) {
            return false;
        }
        BlockPos teleportTarget = scaffoldFinalWorkFeet != null ? scaffoldFinalWorkFeet : workPosition;
        if (!tryTeleportToWorkStandOrAlternate(level, maid, job, target, teleportTarget)) {
            return false;
        }
        if (!scaffoldWaypoints.isEmpty()) {
            scaffoldWaypointIndex = scaffoldWaypoints.size();
        }
        resetNeighborhoodStall();
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        ticksSinceStuckTeleport = 0;
        lastVanillaWalkTarget = null;
        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
            ConstructionPathBrainSync.stopNavigation(maid);
            clearScaffoldRoute();
            beginFacing(maid, target);
        } else {
            walkTo(level, maid, job, workPosition);
            phase = Phase.MOVING;
        }
        return true;
    }

    private boolean isPathfindingTowardWork(EntityMaid maid) {
        if (workPosition == null) {
            return false;
        }
        if (!scaffoldWaypoints.isEmpty() || usingScaffoldNav) {
            return true;
        }
        var nav = maid.getNavigation();
        if (lastVanillaWalkTarget == null || !lastVanillaWalkTarget.equals(workPosition)) {
            return false;
        }
        return nav.isInProgress() || !nav.isDone();
    }

    private boolean tryTeleportToWorkStandOrAlternate(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target, BlockPos teleportTarget) {
        if (tryTeleportToWorkStand(level, maid, teleportTarget)) {
            return true;
        }
        Optional<BlockPos> alternate = findAlternateWorkStand(level, maid, target, teleportTarget);
        if (alternate.isEmpty()) {
            return false;
        }
        workPosition = ScaffoldColumnSupport.clampStandToColumn(
                level, job.scaffoldColumnRegistry(), alternate.get());
        scaffoldFinalWorkFeet = workPosition;
        clearScaffoldRoute();
        if (tryTeleportToWorkStand(level, maid, workPosition)) {
            return true;
        }
        walkVanilla(level, maid, workPosition);
        return true;
    }


    /**
     * Vanilla {@code moveTo} often stalls one cell short of the planned stand. Snap onto the work
     * cell (or an alternate) instead of looping NOT_NEEDED → walk forever.
     */
    private boolean tryResolveAdjacentWorkStall(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target) {
        if (workPosition == null) {
            return false;
        }
        int manhattan = maid.blockPosition().distManhattan(workPosition);
        if (!ConstructionWalkPolicy.shouldSnapAdjacentWork(manhattan, neighborhoodStallTicks())) {
            return false;
        }
        if (snapToWorkStand(level, maid, job, target, workPosition)) {
            return true;
        }
        Optional<BlockPos> alternate = findAlternateWorkStand(level, maid, target, workPosition);
        if (alternate.isPresent()) {
            workPosition = ScaffoldColumnSupport.clampStandToColumn(
                    level, job.scaffoldColumnRegistry(), alternate.get());
            scaffoldFinalWorkFeet = workPosition;
            if (snapToWorkStand(level, maid, job, target, workPosition)) {
                return true;
            }
        }
        if (tryDeferUnreachableTarget(level, maid, job, target)) {
            return true;
        }
        if (currentTask != null) {
            job.markUnreachable(currentTask);
            arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
            resetNeighborhoodStall();
            clearScaffoldRoute();
            beginFacing(maid, target);
            return true;
        }
        return false;
    }

    private boolean snapToWorkStand(
            ServerLevel level, EntityMaid maid, BuildJob job, BlockPos target, BlockPos stand) {
        if (!tryTeleportToWorkStand(level, maid, stand)) {
            return false;
        }
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        resetNeighborhoodStall();
        lastVanillaWalkTarget = null;
        if (BuildWorkPosition.canInteractNow(level, maid, target)) {
            ConstructionPathBrainSync.stopNavigation(maid);
            clearScaffoldRoute();
            beginFacing(maid, target);
        }
        return true;
    }

    private boolean tryTeleportToWorkStand(ServerLevel level, EntityMaid maid, BlockPos work) {
        if (!ScaffoldVanillaNavPolicy.canTeleportToWork(level, work)) {
            return false;
        }
        ConstructionPathBrainSync.stopNavigation(maid);
        float fallBefore = maid.fallDistance;
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
        maid.setDeltaMovement(Vec3.ZERO);
        BlockPos from = maid.blockPosition();
        maid.teleportTo(work.getX() + 0.5D, work.getY(), work.getZ() + 0.5D);
        releaseHover(maid);
        ScaffoldFallDebugLog.teleportClear(maid, "scaffold_stuck", fallBefore, from, work);
        return true;
    }

    private void refreshScaffoldWaypoints(EntityMaid maid) {
        if (scaffoldPath.isEmpty()) {
            scaffoldWaypoints = List.of();
            scaffoldWaypointIndex = 0;
            return;
        }
        scaffoldWaypoints = ScaffoldWaypointPolicy.extract(scaffoldPath, scaffoldFinalWorkFeet);
        scaffoldWaypointIndex = ScaffoldWaypointPolicy.initialIndex(scaffoldWaypoints, maid.blockPosition());
        lastVanillaWalkTarget = null;
    }

    private void clearScaffoldRoute() {
        scaffoldPath = List.of();
        scaffoldWaypoints = List.of();
        scaffoldFinalWorkFeet = null;
        scaffoldWaypointIndex = 0;
        usingScaffoldNav = false;
        lastVanillaWalkTarget = null;
    }

    private static void releaseHover(EntityMaid maid) {
        if (maid.isNoGravity()) {
            maid.setNoGravity(false);
        }
        maid.fallDistance = ScaffoldFallSafety.clearedFallDistance();
    }

    private void discardLocalBatch(BuildJob job, EntityMaid maid) {
        for (BuildTask task : localBatch) {
            job.releaseTask(task, maid.getUUID());
        }
        localBatch.clear();
    }


    private boolean shouldTrackNeighborhoodStall() {
        return phase == Phase.MOVING
                || phase == Phase.FACING
                || phase == Phase.PLACING
                || phase == Phase.BATCH;
    }

    private int neighborhoodStallTicks() {
        return BuildProgressStallPolicy.stallTicks(progressStall);
    }

    private void resetNeighborhoodStall() {
        progressStall = BuildProgressStallPolicy.State.EMPTY;
        workGoalStall = BuildWorkGoalStallPolicy.State.EMPTY;
    }

    private boolean tryRecoverFromNeighborhoodStall(ServerLevel level, EntityMaid maid, BuildJob job) {
        boolean neighborhoodStalled = BuildProgressStallPolicy.isStalled(progressStall);
        boolean workGoalStalled = phase == Phase.MOVING
                && workPosition != null
                && BuildWorkGoalStallPolicy.isStalled(workGoalStall);
        if (!neighborhoodStalled && !workGoalStalled) {
            return false;
        }
        if (currentTask != null && workPosition != null) {
            BlockPos target = currentTask.worldPos();
            if (phase == Phase.MOVING && tryResolveAdjacentWorkStall(level, maid, job, target)) {
                resetNeighborhoodStall();
                return true;
            }
            if (tryResolvePathStallToWorkStand(level, maid, job, target)) {
                return true;
            }
            if (phase == Phase.MOVING) {
                tryUnreachableStandBudgeted(level, maid, job, target);
                resetNeighborhoodStall();
                return true;
            }
        }
        return false;
    }

    private void resetWorkState() {
        phase = Phase.IDLE;
        currentTask = null;
        workPosition = null;
        localBatch.clear();
        arrivedBlocked = BuildArrivedPolicy.State.EMPTY;
        scaffoldAttempts = 0;
        notNeededWhileStuck = 0;
        clearScaffoldRoute();
        resetNeighborhoodStall();
        lastPathLogKey = "";
        lastVanillaWalkTarget = null;
        ticksSinceHeavyNav = BuildHeavyNavBudgetPolicy.MIN_TICKS_BETWEEN;
        missedStandTarget = Long.MIN_VALUE;
        ticksSinceStandMiss = WorkStandMissRescanPolicy.RESCAN_INTERVAL_TICKS;
        ticksSinceStuckTeleport = ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS;
        ticksSinceLastPlace = 0;
        placeCooldown = 0;
        faceHoldRemaining = 0;
        materialCheckCooldown = 0;
        materialVaultRescanCooldown = MaterialWaitVaultRescanPolicy.arm();
        placingFromBatch = false;
        taskClaimTicks = 0;
        abandonCooldownTicks = 0;
        pickClaimStuckTicks = 0;
        lastLoggedPhase = Phase.IDLE;
        buildLogHeartbeat = 0;
        layerPlaceFailures.clear();
        layerPlaceFailTrackedLayer = Integer.MIN_VALUE;
    }

    // #region agent log
    private void agentLog(String hypothesisId, String location, String message, java.util.function.Consumer<JsonObject> extra) {
        if (agentLogCooldown > 0) {
            agentLogCooldown--;
            return;
        }
        agentLogCooldown = 40;
        JsonObject data = AgentDebugLog.data();
        extra.accept(data);
        AgentDebugLog.log(hypothesisId, location, message, data);
    }

    private JsonObject agentMaidState(EntityMaid maid, BuildJob job) {
        JsonObject d = AgentDebugLog.data();
        fillAgentMaidState(d, maid, job);
        return d;
    }

    private JsonObject agentJobState(EntityMaid maid, BuildJob job, java.util.function.Consumer<JsonObject> extra) {
        JsonObject d = agentMaidState(maid, job);
        if (extra != null) {
            extra.accept(d);
        }
        return d;
    }

    private static void fillAgentMaidState(JsonObject d, EntityMaid maid, BuildJob job) {
        if (maid != null) {
            d.addProperty("maid", ConstructionDebugIds.maid(maid));
            d.addProperty("brainWork", maid.getBrain().isActive(Activity.WORK));
            d.addProperty("schedule", maid.getScheduleDetail() == null ? "-" : maid.getScheduleDetail().getName());
            d.addProperty("feet", maid.blockPosition().toShortString());
        }
        if (job != null) {
            d.addProperty("pending", job.debugPendingCount());
            d.addProperty("activeLayer", job.activeLayer());
            d.addProperty("hasPlaceable", job.hasPlaceableTaskOnActiveLayer());
            d.addProperty("paused", job.isPaused());
            d.addProperty("done", job.isDone());
        }
    }
    // #endregion
}
