package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.ConstructionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-level last resort:
 * <ul>
 *   <li>All maids IDLE and no placed progress for {@link BuildProjectIdleFallbackPolicy#ALL_IDLE_TICKS}
 *       → random unbuilt cell on the active layer and below</li>
 *   <li>No placed progress for {@link BuildProjectStallFallbackPolicy#STALL_TICKS} → random claimed cell</li>
 * </ul>
 * Both paths share {@link BuildProjectStallFallbackPolicy#FALLBACK_COOLDOWN_TICKS} spacing.
 */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildProjectStallFallback {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<BuildJobKey, StallState> STALL_BY_JOB = new ConcurrentHashMap<>();

    private BuildProjectStallFallback() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Map.Entry<BuildJobKey, BuildJob> entry : BuildJobRegistry.all().entrySet()) {
            tickJob(server, entry.getKey(), entry.getValue());
        }
        STALL_BY_JOB.keySet().removeIf(key -> BuildJobRegistry.get(key).isEmpty());
    }

    private static void tickJob(MinecraftServer server, BuildJobKey key, BuildJob job) {
        if (job.isPaused() || job.isDone()) {
            STALL_BY_JOB.remove(key);
            return;
        }
        Optional<ServerLevel> levelOpt = resolveJobLevel(server, key);
        if (levelOpt.isEmpty()) {
            return;
        }
        ServerLevel level = levelOpt.get();
        job.refreshWorkQueue(level, false);
        job.tickEndFluids(level);
        if (job.isDone()) {
            STALL_BY_JOB.remove(key);
            return;
        }

        StallState state = STALL_BY_JOB.computeIfAbsent(key, ignored -> new StallState(job.placedCount()));
        state.ticksSinceLastFallback++;

        int placedCount = job.placedCount();
        if (placedCount > state.lastPlacedCount) {
            resetProgressTracking(state, placedCount);
        } else {
            state.ticksWithoutProgress++;
        }

        if (ConstructionMaidBinding.allMaidsIdleOnJob(level, key)) {
            state.ticksAllIdle++;
        } else {
            state.ticksAllIdle = 0;
        }

        if (!BuildProjectStallFallbackPolicy.mayForcePlace(state.ticksSinceLastFallback)) {
            return;
        }

        if (BuildProjectIdleFallbackPolicy.shouldAttempt(state.ticksAllIdle, state.ticksWithoutProgress)) {
            if (attemptIdleFallback(level, key, job)) {
                noteFallbackAttempt(state, job.placedCount());
                return;
            }
        }

        if (!BuildProjectStallFallbackPolicy.shouldAttempt(state.ticksWithoutProgress)) {
            return;
        }

        List<BuildJob.ClaimedTask> claims = job.activeMaidClaims();
        if (claims.isEmpty()) {
            return;
        }
        BuildJob.ClaimedTask pick = claims.get(level.getRandom().nextInt(claims.size()));
        BuildTask task = pick.task();
        UUID maidId = pick.maidId();
        int before = job.placedCount();
        boolean placed = job.forcePlaceWithSupport(level, task, maidId);
        if (!placed) {
            BuildDebugLog.projectStallFallbackFailed(key, maidId, task.worldPos());
            return;
        }
        noteMaidProgress(level, maidId, task.worldPos());
        BuildDebugLog.projectStallFallback(key, maidId, task.worldPos(), job.placedCount() - before);
        BuildJobService.syncProgressAfterPlacement(level, job, task.worldPos());
        noteFallbackAttempt(state, job.placedCount());
    }

    private static boolean attemptIdleFallback(ServerLevel level, BuildJobKey key, BuildJob job) {
        Optional<BuildTask> taskOpt = job.randomUnbuiltTaskUpToActiveLayer(level.getRandom());
        if (taskOpt.isEmpty()) {
            return false;
        }
        BuildTask task = taskOpt.get();
        UUID maidId = pickRandomMaid(level, key).orElse(null);
        int before = job.placedCount();
        boolean placed = job.forcePlaceWithSupport(level, task, maidId != null ? maidId : key.ownerId());
        if (!placed) {
            BuildDebugLog.projectIdleFallbackFailed(key, maidId, task.worldPos());
            return false;
        }
        if (maidId != null) {
            noteMaidProgress(level, maidId, task.worldPos());
        }
        BuildDebugLog.projectIdleFallback(key, maidId, task.worldPos(), job.placedCount() - before);
        BuildJobService.syncProgressAfterPlacement(level, job, task.worldPos());
        return true;
    }

    private static Optional<UUID> pickRandomMaid(ServerLevel level, BuildJobKey key) {
        List<UUID> maids = ConstructionMaidBinding.maidIdsOnJob(key);
        if (maids.isEmpty()) {
            return Optional.empty();
        }
        List<UUID> loaded = maids.stream().filter(id -> level.getEntity(id) instanceof EntityMaid).toList();
        List<UUID> pool = loaded.isEmpty() ? maids : loaded;
        return Optional.of(pool.get(level.getRandom().nextInt(pool.size())));
    }

    private static void noteMaidProgress(ServerLevel level, UUID maidId, BlockPos placed) {
        if (level.getEntity(maidId) instanceof EntityMaid maid) {
            ConstructionMaidBinding.setLastPlacedPos(maid, placed);
            ConstructionMaidBinding.clearMaterialWait(maid);
        }
    }

    private static void resetProgressTracking(StallState state, int placedCount) {
        state.lastPlacedCount = placedCount;
        state.ticksWithoutProgress = 0;
    }

    private static void noteFallbackAttempt(StallState state, int placedCount) {
        state.ticksSinceLastFallback = 0;
        resetProgressTracking(state, placedCount);
        state.ticksAllIdle = 0;
    }

    private static Optional<ServerLevel> resolveJobLevel(MinecraftServer server, BuildJobKey key) {
        ServerLevel ownerLevel = Optional.ofNullable(server.getPlayerList().getPlayer(key.ownerId()))
                .map(player -> player.serverLevel())
                .orElse(null);
        if (ownerLevel != null) {
            return Optional.of(ownerLevel);
        }
        for (Map.Entry<UUID, BuildJobKey> entry : ConstructionMaidBinding.activeMaidJobs().entrySet()) {
            if (!entry.getValue().equals(key)) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(entry.getKey()) != null) {
                    return Optional.of(level);
                }
            }
        }
        return Optional.empty();
    }

    private static final class StallState {
        private int lastPlacedCount;
        private int ticksWithoutProgress;
        private int ticksAllIdle;
        private int ticksSinceLastFallback = BuildProjectStallFallbackPolicy.FALLBACK_COOLDOWN_TICKS;

        private StallState(int lastPlacedCount) {
            this.lastPlacedCount = lastPlacedCount;
        }
    }
}
