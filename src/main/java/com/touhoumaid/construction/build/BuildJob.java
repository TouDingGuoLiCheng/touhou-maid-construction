package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.placement.BlockStateResolve;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import com.touhoumaid.construction.project.MaterialBlockPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shared task queue for one construction project. Layer-gated: lowest incomplete blueprint layer only.
 */
public final class BuildJob {
    public static final int LOCAL_RADIUS = 5;
    /** Max cells one maid may hold in the local place-queue (search radius is {@link #LOCAL_RADIUS}). */
    public static final int LOCAL_BATCH_LIMIT = BuildBatchClaimPolicy.MAX_TASKS;
    /** Server-side retries (deferred / unreachable) without a maid claimant. */
    static final UUID SYSTEM_CLAIMANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final BuildJobKey key;
    private final Blueprint blueprint;
    private final BlockPos anchor;
    private final MaterialSource materials;
    private final List<BuildTask> allTasks;
    private final Map<Long, BuildTask> taskByPos;
    private final Deque<BuildTask> pending = new ArrayDeque<>();
    private final Set<Long> placed = new HashSet<>();
    private final Set<Long> skipped = new HashSet<>();
    private final Set<Long> deferred = new HashSet<>();
    private final Set<Long> unreachable = new HashSet<>();
    private final Set<Long> failed = new HashSet<>();
    /** Block position 鈫?maid (or {@link #SYSTEM_CLAIMANT}) currently working that cell. */
    private final TaskClaimRegistry claims = new TaskClaimRegistry();
    private final List<String> placementFailures = new ArrayList<>();
    private final Set<String> ignoredBlockIds;
    private final ScaffoldLedger scaffolds = new ScaffoldLedger();
    /** Landing Y and max stand Y per scaffold tower XZ for reuse search. */
    private final ScaffoldColumnRegistry scaffoldColumnRegistry = new ScaffoldColumnRegistry();
    /** Which maid is raising which scaffold column, so towers do not overlap. */
    private final ScaffoldColumnClaims scaffoldColumns = new ScaffoldColumnClaims();
    private final int placementYaw;
    private final boolean coverTerrain;
    private final boolean ignoreOverlaps;
    private volatile boolean paused;
    private volatile boolean done;
    private volatile boolean completionNotified;
    private int consumedMaterials;
    /** Throttle full pending/deferred rescans 鈥?large jobs 脳 many maids can stall the server thread. */
    private long lastQueueRefreshTick = Long.MIN_VALUE;
    private static final int QUEUE_REFRESH_INTERVAL_TICKS = 20;
    /** Throttle recorded nav-failure samples for vault direct-place (once per second per cell). */
    private static final int PATH_NAV_FAILURE_RECORD_INTERVAL_TICKS = 20;
    private final Map<String, Integer> placedByBlockId = new HashMap<>();
    /** Non-ignored schematic cells per block id. Remaining need is this minus {@link #placedByBlockId}. */
    private final SchematicDemandIndex schematicDemand = new SchematicDemandIndex();
    /** Per-task-cell pathfinding failures before vault direct-place. */
    private final Map<Long, Integer> pathNavFailures = new HashMap<>();
    private final Map<Long, Long> pathNavFailureRecordedAt = new HashMap<>();
    private String pauseReason = "";
    private volatile BlueprintPlacement.Bounds placedBounds;
    /** Skip full claim scans briefly when the layer has no stocked work. */
    private int claimStarveUntilTick = Integer.MIN_VALUE;
    private int claimStarveRevision = Integer.MIN_VALUE;
    /** Cache {@link #hasPlaceableTaskOnActiveLayer()} while stock + queue shape unchanged. */
    private int placeableScanCacheKey = Integer.MIN_VALUE;
    private boolean placeableScanCacheValue;
    private boolean placeableScanCacheReady;
    /** Per-maid-per-layer outward claim radar (distance-ordered list + cursor). */
    private final Map<ClaimRadarKey, ClaimRadarState> claimRadars = new HashMap<>();
    /** Schematic fluids, placed by the job itself after structural work is finished. */
    private final List<BuildTask> endFluids = new ArrayList<>();
    private int endFluidCursor;
    /** Waterlogged solids maids place dry; the contained water is applied with the end dump. */
    private final List<BuildTask> fluidRetouch = new ArrayList<>();
    private int fluidRetouchCursor;

    public BuildJob(
            BuildJobKey key,
            Blueprint blueprint,
            BlockPos anchor,
            MaterialSource materials,
            List<BuildTask> tasks,
            Set<String> ignoredBlockIds,
            int placementYaw,
            boolean coverTerrain,
            boolean ignoreOverlaps) {
        this.key = key;
        this.blueprint = blueprint;
        this.anchor = anchor.immutable();
        this.materials = materials;
        this.allTasks = List.copyOf(tasks);
        this.taskByPos = new HashMap<>(tasks.size());
        for (BuildTask task : tasks) {
            taskByPos.put(pack(task.worldPos()), task);
        }
        this.ignoredBlockIds = new HashSet<>(ignoredBlockIds == null ? Set.of() : ignoredBlockIds);
        this.placementYaw = com.touhoumaid.construction.placement.SchematicYaw.normalize(placementYaw);
        this.coverTerrain = coverTerrain;
        this.ignoreOverlaps = ignoreOverlaps;
        for (BuildTask task : tasks) {
            if (FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
                endFluids.add(task);
            } else {
                pending.add(task);
                if (FluidEndPlacePolicy.isWaterlogged(task.raw().properties())) {
                    fluidRetouch.add(task);
                }
            }
        }
        applyIgnoredBlocks();
        dropIgnoredEndFluids();
        rebuildSchematicDemand();
    }

    public BuildJobKey key() {
        return key;
    }

    public Blueprint blueprint() {
        return blueprint;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public boolean coverTerrain() {
        return coverTerrain;
    }

    public boolean ignoreOverlaps() {
        return ignoreOverlaps;
    }

    public int placementYaw() {
        return placementYaw;
    }

    /**
     * After maids finish every claimable cell, place leftovers that can survive, then blueprint
     * water/lava, then leftovers that needed that fluid. Does not walk a maid to the cell.
     */
    public void tickEndFluids(ServerLevel level) {
        if (level == null || paused || done) {
            return;
        }
        if (!FluidEndPlacePolicy.readyToDump(maidClaimablePendingCount(), claims.size())) {
            return;
        }
        int placedBefore = placedCount();
        int budget = FluidEndPlacePolicy.CELLS_PER_TICK;
        budget = dumpLeftoverCells(level, snapshotUnreachableTasks(), budget);
        budget = dumpLeftoverCells(level, snapshotDeferredTasks(), budget);
        budget = dumpFluids(level, endFluids, endFluidCursor, budget, false);
        endFluidCursor = dumpCursor;
        budget = dumpFluids(level, fluidRetouch, fluidRetouchCursor, budget, true);
        fluidRetouchCursor = dumpCursor;
        if (fluidsFinished()) {
            budget = dumpLeftoverCells(level, snapshotUnreachableTasks(), budget);
            dumpLeftoverCells(level, snapshotDeferredTasks(), budget);
            failLeftoversThatCannotSurvive(level);
        }
        if (placedCount() > placedBefore) {
            BuildJobService.syncProgressAfterPlacement(level, this, anchor);
        }
        if (fluidsFinished() && unreachable.isEmpty() && deferred.isEmpty()) {
            checkCompletion(level);
        }
    }

    private int dumpCursor;

    private int dumpFluids(
            ServerLevel level, List<BuildTask> tasks, int cursor, int budget, boolean alreadyPlacedOk) {
        dumpCursor = cursor;
        while (dumpCursor < tasks.size() && budget > 0) {
            BuildTask task = tasks.get(dumpCursor);
            BlockPos pos = task.worldPos();
            if (!level.isLoaded(pos)) {
                level.getChunk(pos);
            }
            dumpCursor++;
            budget--;
            long packed = pack(pos);
            if (skipped.contains(packed) || failed.contains(packed) || isIgnored(task)) {
                continue;
            }
            if (!alreadyPlacedOk && placed.contains(packed)) {
                continue;
            }
            if (BlueprintPlacer.placeBlock(level, task, placementYaw, materials, true)) {
                if (!placed.contains(packed)) {
                    markAlreadyPlaced(task);
                }
            }
        }
        return budget;
    }

    private int maidClaimablePendingCount() {
        int unreachablePending = 0;
        for (BuildTask task : pending) {
            if (unreachable.contains(pack(task.worldPos()))) {
                unreachablePending++;
            }
        }
        return FluidEndPlacePolicy.maidClaimablePending(pending.size(), unreachablePending);
    }

    private List<BuildTask> snapshotUnreachableTasks() {
        List<BuildTask> tasks = new ArrayList<>(unreachable.size());
        for (long packed : List.copyOf(unreachable)) {
            BuildTask task = taskByPos.get(packed);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private List<BuildTask> snapshotDeferredTasks() {
        List<BuildTask> tasks = new ArrayList<>(deferred.size());
        for (long packed : List.copyOf(deferred)) {
            BuildTask task = taskByPos.get(packed);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    /** Vault-place leftover solids the maids could not reach, then fluids dump in the same burst. */
    private int dumpLeftoverCells(ServerLevel level, List<BuildTask> tasks, int budget) {
        for (BuildTask task : tasks) {
            if (budget <= 0) {
                break;
            }
            if (task == null || FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (skipped.contains(packed) || failed.contains(packed) || isIgnored(task)) {
                pending.remove(task);
                unreachable.remove(packed);
                deferred.remove(packed);
                continue;
            }
            BlockPos pos = task.worldPos();
            if (!level.isLoaded(pos)) {
                level.getChunk(pos);
            }
            if (placed.contains(packed) || matchesWorld(level, task)) {
                markAlreadyPlaced(task);
                continue;
            }
            if (!BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                continue;
            }
            budget--;
            if (!materials.hasMaterial(task.raw().name())) {
                continue;
            }
            CoverTerrainClear.tryHarvest(level, this, pos);
            if (!BlueprintPlacer.placeBlock(level, task, placementYaw, materials)) {
                continue;
            }
            if (!materials.tryConsume(task.raw().name())) {
                continue;
            }
            markAlreadyPlaced(task);
        }
        return budget;
    }

    private boolean fluidsFinished() {
        return endFluidCursor >= endFluids.size() && fluidRetouchCursor >= fluidRetouch.size();
    }

    /**
     * After water and lava exist, drop leftovers that still cannot survive
     * (carpet on water, missing terrain). Cells waiting on a real blueprint neighbour stay queued.
     */
    private void failLeftoversThatCannotSurvive(ServerLevel level) {
        failHopelessDeferred(level);
        for (long packed : List.copyOf(unreachable)) {
            BuildTask task = taskByPos.get(packed);
            if (task == null) {
                unreachable.remove(packed);
                continue;
            }
            if (FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
                continue;
            }
            if (BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                continue;
            }
            if (awaitsSchematicSupport(level, task)) {
                continue;
            }
            markPlacementFailed(task);
        }
    }

    public Optional<BuildTask> taskAt(BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskByPos.get(pack(pos)));
    }

    public boolean alreadyBuilt(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long packed = pack(pos);
        return placed.contains(packed) || skipped.contains(packed);
    }

    public boolean worldMatchesTask(ServerLevel level, BuildTask task) {
        return matchesWorld(level, task);
    }

    /**
     * Stay-put covering the schematic footprint. Teleport uses shortest distance to this
     * AABB, not distance to the geometric center.
     */
    public ConstructionMaidFollow.StayArea workStayArea(BlockPos maidStart) {
        BlueprintPlacement.Bounds bounds = placedBounds();
        int margin = 12;
        int minX = bounds.minX() - margin;
        int maxX = bounds.maxX() + margin;
        int minZ = bounds.minZ() - margin;
        int maxZ = bounds.maxZ() + margin;
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int height = Math.max(1, bounds.maxY() - bounds.minY());
        int centerY = (bounds.minY() + bounds.maxY()) / 2;
        BlockPos center = new BlockPos(centerX, centerY, centerZ);
        int radius = 16;
        radius = Math.max(radius, xzDistance(center, minX, minZ));
        radius = Math.max(radius, xzDistance(center, minX, maxZ));
        radius = Math.max(radius, xzDistance(center, maxX, minZ));
        radius = Math.max(radius, xzDistance(center, maxX, maxZ));
        radius = Math.max(radius, height + margin);
        radius = Math.max(radius, (int) ConstructionSiteArrivePolicy.SITE_RESCUE_RANGE);
        return new ConstructionMaidFollow.StayArea(center, Math.min(radius, 256), minX, maxX, minZ, maxZ);
    }

    /** World AABB actually covered by the placed blueprint (the site-pick ghost box). */
    public BlueprintPlacement.Bounds placedBounds() {
        BlueprintPlacement.Bounds cached = placedBounds;
        if (cached == null) {
            BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
            cached = BlueprintPlacement.placedBounds(anchor, footprint, placementYaw);
            placedBounds = cached;
        }
        return cached;
    }

    /** Blueprint AABB in the shape the scaffold planner consumes. */
    public ScaffoldBoundsPolicy.Box scaffoldBox() {
        BlueprintPlacement.Bounds bounds = placedBounds();
        return new ScaffoldBoundsPolicy.Box(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static int xzDistance(BlockPos from, int x, int z) {
        int dx = x - from.getX();
        int dz = z - from.getZ();
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    public MaterialSource materials() {
        return materials;
    }

    public ScaffoldLedger scaffolds() {
        return scaffolds;
    }

    public ScaffoldColumnRegistry scaffoldColumnRegistry() {
        return scaffoldColumnRegistry;
    }

    public ScaffoldColumnClaims scaffoldColumns() {
        return scaffoldColumns;
    }

    public void releaseScaffoldColumns(UUID maidId) {
        scaffoldColumns.release(maidId);
    }

    /** True when this world cell is still an unresolved blueprint placement. */
    public boolean isPendingPlacementCell(BlockPos pos) {
        long packed = pack(pos);
        return taskByPos.containsKey(packed) && !isResolved(packed);
    }

    public int totalBlocks() {
        return allTasks.size();
    }

    public int placedCount() {
        return placed.size() + skipped.size();
    }

    public int consumedMaterials() {
        return consumedMaterials;
    }

    /** Restore persisted consumption after world reload (syncAlreadyBuilt does not replay vault use). */
    public void restoreConsumedMaterials(int saved) {
        consumedMaterials = Math.max(consumedMaterials, Math.max(0, saved));
    }

    /**
     * Keep persisted per-block counts when world rescan under-counts (block-state drift, etc.).
     * Pending queue still reflects the rescan.
     */
    public void restorePlacedProgress(Map<String, Integer> savedByBlockId) {
        if (savedByBlockId == null || savedByBlockId.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : savedByBlockId.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                placedByBlockId.merge(entry.getKey(), count, Math::max);
            }
        }
    }

    public Map<String, Integer> placedByBlockId() {
        return Map.copyOf(placedByBlockId);
    }

    public int schematicCountForBlock(String blockId) {
        return schematicDemand.demandOf(blockId);
    }

    public int pendingCountForBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (BuildTask task : pending) {
            if (blockId.equals(task.raw().name())) {
                count++;
            }
        }
        return count;
    }

    public int remainingMaterialNeed(String blockId) {
        return BuildMaterialNeedPolicy.remainingNeed(
                schematicCountForBlock(blockId),
                placedByBlockId.getOrDefault(blockId, 0));
    }

    public boolean stillNeedsVaultMaterial(String blockId) {
        return BuildMaterialNeedPolicy.stillNeedsVaultMaterial(
                schematicCountForBlock(blockId),
                placedByBlockId.getOrDefault(blockId, 0),
                pendingCountForBlock(blockId));
    }

    /**
     * Drop pending cells for block types already marked fully placed (e.g. after NBT merge on reload).
     */
    public void reconcileStalePending(ServerLevel level) {
        List<BuildTask> toSkip = new ArrayList<>();
        for (BuildTask task : pending) {
            String blockId = task.raw().name();
            if (BuildMaterialNeedPolicy.shouldSkipPendingTask(
                    schematicCountForBlock(blockId),
                    placedByBlockId.getOrDefault(blockId, 0))) {
                toSkip.add(task);
            }
        }
        for (BuildTask task : toSkip) {
            skipTask(task);
        }
        if (!toSkip.isEmpty()) {
            checkCompletion(level);
        }
    }

    /**
     * Re-queue deferred cells whose support is now satisfied and reconcile stale pending counts.
     * Call before claiming when the job appears idle but work may still exist off-queue.
     */
    public void refreshWorkQueue(ServerLevel level) {
        refreshWorkQueue(level, false);
    }

    /**
     * Re-queue deferred cells whose support is now satisfied and reconcile stale pending counts.
     * Throttled to once per second per job unless {@code force}.
     */
    public void refreshWorkQueue(ServerLevel level, boolean force) {
        if (level == null || done) {
            return;
        }
        long now = level.getGameTime();
        if (!force && now - lastQueueRefreshTick < QUEUE_REFRESH_INTERVAL_TICKS) {
            return;
        }
        lastQueueRefreshTick = now;
        failHopelessDeferred(level);
        promoteReadyDeferred(level);
        reconcileStalePending(level);
        sweepUnplaceableClaims(level);
    }

    /** Re-add a deferred cell for direct-place retry (unreachable path, bypasses reach checks). */
    public void prepareDirectPlace(BuildTask task) {
        if (task == null) {
            return;
        }
        long packed = pack(task.worldPos());
        deferred.remove(packed);
        unreachable.add(packed);
        if (!placed.contains(packed) && !skipped.contains(packed) && !failed.contains(packed)) {
            pending.remove(task);
            pending.addFirst(task);
        }
        forceRelease(packed);
    }

    public boolean hasDeferredWork() {
        return !deferred.isEmpty();
    }

    private void promoteReadyDeferred(ServerLevel level) {
        if (deferred.isEmpty()) {
            return;
        }
        List<Long> snapshot = new ArrayList<>(deferred);
        for (long packed : snapshot) {
            BuildTask task = taskByPos.get(packed);
            if (task == null) {
                deferred.remove(packed);
                continue;
            }
            if (isIgnored(task)) {
                deferred.remove(packed);
                skipped.add(packed);
                continue;
            }
            if (matchesWorld(level, task)) {
                deferred.remove(packed);
                markAlreadyPlaced(task);
                continue;
            }
            if (!materials.hasMaterial(task.raw().name())) {
                continue;
            }
            if (!BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                if (!awaitsSchematicSupport(level, task)) {
                    markPlacementFailed(task);
                }
                continue;
            }
            deferred.remove(packed);
            if (!placed.contains(packed) && !skipped.contains(packed) && !failed.contains(packed)) {
                pending.remove(task);
                pending.addLast(task);
            }
        }
    }

    /**
     * Mark cells that cannot survive and are not waiting on another blueprint block as failed.
     * Runs even while other layers still have pending work.
     */
    private void failHopelessDeferred(ServerLevel level) {
        if (deferred.isEmpty() || level == null) {
            return;
        }
        List<Long> snapshot = new ArrayList<>(deferred);
        for (long packed : snapshot) {
            BuildTask task = taskByPos.get(packed);
            if (task == null) {
                deferred.remove(packed);
                continue;
            }
            if (BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                continue;
            }
            if (!awaitsSchematicSupport(level, task)) {
                markPlacementFailed(task);
            }
        }
    }

    /** Release claims on the active layer for cells that can never be placed. */
    public void sweepUnplaceableClaims(ServerLevel level) {
        if (level == null || done) {
            return;
        }
        failHopelessDeferred(level);
        int layer = activeLayer();
        if (layer < 0) {
            return;
        }
        for (long packed : claims.claimedPositions()) {
            BuildTask task = taskByPos.get(packed);
            if (task == null || task.layerIndex() != layer) {
                continue;
            }
            if (BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                continue;
            }
            if (awaitsSchematicSupport(level, task)) {
                forceRelease(packed);
                continue;
            }
            markPlacementFailed(task);
        }
    }

    public boolean canSurviveAt(ServerLevel level, BuildTask task) {
        return BuildPlacementCheck.canSurvive(level, task, placementYaw);
    }

    public boolean awaitsSchematicSupport(ServerLevel level, BuildTask task) {
        return BuildDeferSupportPolicy.awaitsSchematicSupport(
                level,
                task,
                placementYaw,
                taskByPos,
                placed,
                skipped,
                failed,
                this::matchesWorld);
    }

    /** Maid-owned cells currently claimed on this job. */
    public List<ClaimedTask> activeMaidClaims() {
        List<ClaimedTask> result = new ArrayList<>();
        for (long packed : claims.claimedPositions()) {
            UUID owner = claims.owner(packed);
            if (owner == null || SYSTEM_CLAIMANT.equals(owner)) {
                continue;
            }
            BuildTask task = taskByPos.get(packed);
            if (task == null || isIgnored(task)) {
                continue;
            }
            result.add(new ClaimedTask(owner, task));
        }
        return result;
    }

    public Optional<BuildTask> findBlockingSupportTask(ServerLevel level, BuildTask task) {
        return BuildDeferSupportPolicy.findBlockingSupportTask(
                level,
                task,
                placementYaw,
                taskByPos,
                placed,
                skipped,
                failed,
                this::matchesWorld);
    }

    /**
     * Ultimate fallback: place without reach/entity checks. Places schematic support neighbours first.
     */
    public boolean forcePlaceWithSupport(ServerLevel level, BuildTask task, UUID maidId) {
        return forcePlaceWithSupport(level, task, maidId, new HashSet<>());
    }

    private boolean forcePlaceWithSupport(
            ServerLevel level, BuildTask task, UUID maidId, Set<Long> visiting) {
        if (paused || done || task == null || level == null || isIgnored(task)) {
            return false;
        }
        long packed = pack(task.worldPos());
        if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
            return false;
        }
        if (!visiting.add(packed)) {
            return false;
        }
        Optional<BuildTask> support = findBlockingSupportTask(level, task);
        if (support.isPresent()) {
            BuildTask supportTask = support.get();
            long supportPacked = pack(supportTask.worldPos());
            if (!placed.contains(supportPacked)
                    && !skipped.contains(supportPacked)
                    && !failed.contains(supportPacked)
                    && !matchesWorld(level, supportTask)) {
                if (!forcePlaceWithSupport(level, supportTask, maidId, visiting)) {
                    return false;
                }
            }
        }
        return forcePlaceDirect(level, task, maidId);
    }

    private boolean forcePlaceDirect(ServerLevel level, BuildTask task, UUID maidId) {
        if (FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
            return false;
        }
        long packed = pack(task.worldPos());
        forceRelease(packed);
        if (matchesWorld(level, task)) {
            markAlreadyPlaced(task);
            return true;
        }
        if (!materials.hasMaterial(task.raw().name())) {
            MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "forcePlace:noMaterial");
            return false;
        }
        if (!BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
            if (awaitsSchematicSupport(level, task)) {
                MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "forcePlace:awaitSupport");
                return false;
            }
            markPlacementFailed(task);
            return false;
        }
        CoverTerrainClear.tryHarvest(level, this, task.worldPos());
        if (!BlueprintPlacer.placeBlock(level, task, placementYaw, materials)) {
            MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "forcePlace:setBlockFailed");
            return false;
        }
        if (!materials.tryConsume(task.raw().name())) {
            MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "forcePlace:tryConsume=false");
            return false;
        }
        placed.add(packed);
        pending.remove(task);
        unreachable.remove(packed);
        deferred.remove(packed);
        failed.remove(packed);
        pathNavFailures.remove(packed);
        BuildPlacementIndex.track(key, task.worldPos());
        consumedMaterials++;
        recordPlaced(task.raw().name());
        checkCompletion(level);
        return true;
    }

    public record ClaimedTask(UUID maidId, BuildTask task) {
    }

    /**
     * Random unbuilt schematic cell on {@code maxLayer} and below (pending, deferred, unreachable).
     */
    public Optional<BuildTask> randomUnbuiltTaskUpToLayer(int maxLayer, net.minecraft.util.RandomSource random) {
        if (maxLayer < 0 || random == null) {
            return Optional.empty();
        }
        List<BuildTask> candidates = new ArrayList<>();
        for (BuildTask task : allTasks) {
            if (task.layerIndex() > maxLayer || isIgnored(task)) {
                continue;
            }
            if (!FluidEndPlacePolicy.maidMayPlace(task.raw().name())) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
                continue;
            }
            candidates.add(task);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    public Optional<BuildTask> randomUnbuiltTaskUpToActiveLayer(net.minecraft.util.RandomSource random) {
        return randomUnbuiltTaskUpToLayer(activeLayer(), random);
    }

    /** Permanently skip a cell that cannot be placed (missing support, repeated failure, etc.). */
    public void failTask(BuildTask task) {
        markPlacementFailed(task);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isDone() {
        return done;
    }

    /** @return true the first time completion side-effects (reclaim, terminal status) may run */
    public boolean tryMarkCompletionNotified() {
        if (!done || completionNotified) {
            return false;
        }
        completionNotified = true;
        return true;
    }

    public String pauseReason() {
        return pauseReason;
    }

    public int deferredCount() {
        return deferred.size();
    }

    public int debugPendingCount() {
        return pending.size();
    }

    public int debugUnreachableCount() {
        return unreachable.size();
    }

    public int debugInProgressCount() {
        return claims.size();
    }

    public List<String> placementFailures() {
        return List.copyOf(placementFailures);
    }

    public int failedCount() {
        return failed.size();
    }

    public void setPaused(boolean paused, String reason) {
        this.paused = paused;
        this.pauseReason = reason == null ? "" : reason;
    }

    public void resumeFromPause() {
        paused = false;
        pauseReason = "";
    }

    public void syncIgnoredBlocks(Set<String> blockIds) {
        ignoredBlockIds.clear();
        if (blockIds != null) {
            ignoredBlockIds.addAll(blockIds);
        }
        rebuildSchematicDemand();
        applyIgnoredBlocks();
        if (dropIgnoredEndFluids()) {
            endFluidCursor = 0;
            fluidRetouchCursor = 0;
        }
    }

    private void applyIgnoredBlocks() {
        List<BuildTask> toSkip = new ArrayList<>();
        for (BuildTask task : pending) {
            if (isIgnored(task)) {
                toSkip.add(task);
            }
        }
        for (BuildTask task : toSkip) {
            skipTask(task);
        }
    }

    /** @return true when any end-fluid cell was moved to skipped */
    private boolean dropIgnoredEndFluids() {
        boolean removed = false;
        for (int i = endFluids.size() - 1; i >= 0; i--) {
            BuildTask task = endFluids.get(i);
            if (!isIgnored(task)) {
                continue;
            }
            skipped.add(pack(task.worldPos()));
            endFluids.remove(i);
            removed = true;
        }
        for (int i = fluidRetouch.size() - 1; i >= 0; i--) {
            BuildTask task = fluidRetouch.get(i);
            if (!isIgnored(task)) {
                continue;
            }
            fluidRetouch.remove(i);
            removed = true;
        }
        return removed;
    }

    public void syncAlreadyBuilt(ServerLevel level) {
        int previousMatched = placed.size();
        pending.clear();
        placed.clear();
        skipped.clear();
        deferred.clear();
        unreachable.clear();
        failed.clear();
        claims.clearAll();
        placementFailures.clear();
        placedByBlockId.clear();
        endFluids.clear();
        endFluidCursor = 0;
        fluidRetouch.clear();
        fluidRetouchCursor = 0;
        for (BuildTask task : allTasks) {
            if (isIgnored(task)) {
                skipped.add(pack(task.worldPos()));
            } else if (matchesWorld(level, task)) {
                placed.add(pack(task.worldPos()));
                BuildPlacementIndex.track(key, task.worldPos());
                recordPlaced(task.raw().name());
            } else if (FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
                endFluids.add(task);
            } else {
                pending.add(task);
                if (FluidEndPlacePolicy.isWaterlogged(task.raw().properties())) {
                    fluidRetouch.add(task);
                }
            }
        }
        consumedMaterials = BuildStartRescanPolicy.adjustConsumedAfterRescan(
                consumedMaterials, previousMatched, placed.size());
        checkCompletion(level);
    }

    /** Register every currently placed block with {@link BuildPlacementIndex}. */
    public void indexAllPlaced() {
        for (BuildTask task : allTasks) {
            if (placed.contains(pack(task.worldPos()))) {
                BuildPlacementIndex.track(key, task.worldPos());
            }
        }
    }

    /**
     * A tracked placed block was destroyed 鈥?return it to the pending queue for repair.
     *
     * @return true when the position was re-queued
     */
    public boolean revertPlacedBlock(BlockPos pos) {
        long packed = pack(pos);
        if (!placed.contains(packed)) {
            return false;
        }
        BuildTask task = taskByPos.get(packed);
        if (task == null || isIgnored(task)) {
            return false;
        }
        placed.remove(packed);
        BuildPlacementIndex.untrack(pos);
        if (FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
            int index = endFluids.indexOf(task);
            if (index < 0) {
                endFluids.add(task);
                index = endFluids.size() - 1;
            }
            if (endFluidCursor > index) {
                endFluidCursor = index;
            }
        } else {
            pending.addFirst(task);
        }
        unreachable.remove(packed);
        deferred.remove(packed);
        failed.remove(packed);
        forceRelease(packed);
        if (consumedMaterials > 0) {
            consumedMaterials--;
        }
        unrecordPlaced(task.raw().name());
        done = false;
        return true;
    }

    private boolean matchesWorld(ServerLevel level, BuildTask task) {
        BlockState expected = BlockStateResolve.resolveRotated(task.raw(), placementYaw);
        BlockState actual = level.getBlockState(task.worldPos());
        boolean match = BuildWorldMatch.matchesPlanned(task.raw(), expected, actual);
        return match;
    }

    private boolean isIgnored(BuildTask task) {
        return isIgnoredBlockId(task.raw().name());
    }

    private boolean isIgnoredBlockId(String blockId) {
        return MaterialBlockPolicy.isAutoIgnored(blockId) || ignoredBlockIds.contains(blockId);
    }

    /** One pass over the schematic. Call when the job is created or the ignore list changes. */
    private void rebuildSchematicDemand() {
        schematicDemand.clear();
        for (BuildTask task : allTasks) {
            String blockId = task.raw().name();
            if (!isIgnoredBlockId(blockId)) {
                schematicDemand.count(blockId);
            }
        }
    }

    private boolean isResolved(long packed) {
        return placed.contains(packed) || skipped.contains(packed) || deferred.contains(packed)
                || failed.contains(packed);
    }

    /** Lowest layer index that still has pending (non-deferred) work. */
    public int activeLayer() {
        int minLayer = Integer.MAX_VALUE;
        int maxLayer = Integer.MIN_VALUE;
        for (BuildTask task : pending) {
            int layer = task.layerIndex();
            minLayer = Math.min(minLayer, layer);
            maxLayer = Math.max(maxLayer, layer);
        }
        if (minLayer == Integer.MAX_VALUE) {
            return lowestOpenLayer();
        }
        for (int layer = minLayer; layer <= maxLayer; layer++) {
            if (!isLayerComplete(layer)) {
                return layer;
            }
        }
        // Only unreachable leftovers 鈥?still expose the lowest layer so maids can direct-place them
        return minLayer;
    }

    /** Lowest layer among cells not yet placed, skipped, or permanently failed. */
    private int lowestOpenLayer() {
        int min = Integer.MAX_VALUE;
        for (BuildTask task : allTasks) {
            long packed = pack(task.worldPos());
            if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
                continue;
            }
            min = Math.min(min, task.layerIndex());
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    /** A layer is done for gating when every remaining pending block on it is unreachable-only. */
    private boolean isLayerComplete(int layer) {
        for (BuildTask task : pending) {
            if (task.layerIndex() != layer) {
                continue;
            }
            if (!unreachable.contains(pack(task.worldPos()))) {
                return false;
            }
        }
        return true;
    }

    public Optional<BuildTask> claimNearest(net.minecraft.world.entity.Entity from) {
        return claimNearest(from, null, true);
    }

    public Optional<BuildTask> claimNearest(net.minecraft.world.entity.Entity from, boolean requireMaterial) {
        return claimNearest(from, null, requireMaterial);
    }

    public Optional<BuildTask> claimNearest(
            net.minecraft.world.entity.Entity from,
            BlockPos localAnchor,
            boolean requireMaterial) {
        return claimNearest(from, localAnchor, requireMaterial, Set.of());
    }

    public Optional<BuildTask> claimNearest(
            net.minecraft.world.entity.Entity from,
            BlockPos localAnchor,
            boolean requireMaterial,
            Set<Long> deprioritizeFailed) {
        if (paused || done) {
            return Optional.empty();
        }
        if (requireMaterial && isClaimStarved(from)) {
            return Optional.empty();
        }
        int primaryLayer = activeLayer();
        if (primaryLayer < 0) {
            return Optional.empty();
        }
        Optional<BuildTask> claimed = claimNearestOnLayer(
                from, localAnchor, requireMaterial, deprioritizeFailed, primaryLayer);
        if (claimed.isPresent()) {
            return claimed;
        }
        if (deferred.isEmpty() || pending.isEmpty()) {
            return Optional.empty();
        }
        // Active layer may be wedged on deferred dependencies; upper layers can still have ready work.
        List<Integer> upperLayers = pending.stream()
                .mapToInt(BuildTask::layerIndex)
                .filter(layer -> layer > primaryLayer)
                .distinct()
                .sorted()
                .boxed()
                .toList();
        for (int layer : upperLayers) {
            claimed = claimNearestOnLayer(from, localAnchor, requireMaterial, deprioritizeFailed, layer);
            if (claimed.isPresent()) {
                return claimed;
            }
        }
        return Optional.empty();
    }

    private boolean isClaimStarved(net.minecraft.world.entity.Entity from) {
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        int revision = index == null ? claimStarveRevision : index.revision();
        int now = serverTickOf(from);
        if (ClaimStarvePolicy.shouldSkip(now, claimStarveUntilTick, revision, claimStarveRevision)) {
            return true;
        }
        // Starve window ended: pick up GUI deposits that bypassed incremental hooks.
        if (claimStarveUntilTick != Integer.MIN_VALUE && now >= claimStarveUntilTick) {
            StockIndexedMaterialSource.requestRebuild(materials);
            claimStarveUntilTick = Integer.MIN_VALUE;
        }
        return false;
    }

    private void noteClaimStarve(net.minecraft.world.entity.Entity from, int materialRejected, int materialAccepted) {
        if (!ClaimStarvePolicy.shouldEnterStarve(materialRejected, materialAccepted)) {
            return;
        }
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        claimStarveUntilTick = ClaimStarvePolicy.starveUntilTick(serverTickOf(from));
        claimStarveRevision = index == null ? claimStarveRevision : index.revision();
    }

    private static int serverTickOf(net.minecraft.world.entity.Entity from) {
        if (from != null && from.level() instanceof ServerLevel level && level.getServer() != null) {
            return level.getServer().getTickCount();
        }
        return 0;
    }

    private Optional<BuildTask> claimNearestOnLayer(
            net.minecraft.world.entity.Entity from,
            BlockPos localAnchor,
            boolean requireMaterial,
            Set<Long> deprioritizeFailed,
            int layer) {
        long perfStart = BuildPerfLog.start();
        int checked = 0;
        int hasMaterialCalls = 0;
        UUID maidId = from.getUUID();
        ServerLevel claimLevel = from.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        int stockRevision = 0;
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        if (index != null) {
            stockRevision = index.revision();
        }
        BlockPos center = localAnchor != null ? localAnchor : from.blockPosition();
        long centerKey = pack(center);
        ClaimRadarKey radarKey = new ClaimRadarKey(maidId, layer);
        ClaimRadarState radar = claimRadars.computeIfAbsent(radarKey, key -> new ClaimRadarState());
        boolean cursorAtStart = radar.cursor == 0;
        boolean needRebuild = radar.ordered.isEmpty()
                || ClaimRadarPolicy.shouldRebuildOrdered(
                        cursorAtStart,
                        layer,
                        radar.layer,
                        pending.size(),
                        radar.pendingSize,
                        stockRevision,
                        radar.stockRevision,
                        centerKey,
                        radar.centerKey);
        if (needRebuild) {
            BlockPos frozenCenter = center;
            List<BuildTask> ordered = new ArrayList<>();
            for (BuildTask task : pending) {
                if (task.layerIndex() != layer) {
                    continue;
                }
                long packed = pack(task.worldPos());
                if (isResolved(packed) || isClaimedByOther(packed, maidId)) {
                    continue;
                }
                if (unreachable.contains(packed)) {
                    continue;
                }
                if (!FluidEndPlacePolicy.maidMayPlace(task.raw().name())) {
                    continue;
                }
                // Do not filter wouldOccupyEntity here — occupancy is transient; skip only in the window.
                ordered.add(task);
            }
            ordered.sort(Comparator
                    .comparingInt(BuildTask::attachPriority)
                    .thenComparingDouble(t -> frozenCenter.distSqr(t.worldPos())));
            radar.ordered = ordered;
            radar.cursor = 0;
            radar.layer = layer;
            radar.pendingSize = pending.size();
            radar.stockRevision = stockRevision;
            radar.centerKey = centerKey;
            radar.passRejected = 0;
            radar.passAccepted = 0;
        } else if (radar.cursor == 0) {
            radar.passRejected = 0;
            radar.passAccepted = 0;
        }
        ClaimRadarPolicy.Window window = ClaimRadarPolicy.nextWindow(
                radar.cursor,
                radar.ordered.size(),
                ClaimRadarPolicy.MAX_MATERIAL_CHECKS_PER_CLAIM);
        // Pass 1: only the current radar ring gets stock checks.
        List<BuildTask> stocked = new ArrayList<>();
        for (int i = window.fromInclusive(); i < window.toExclusive(); i++) {
            BuildTask task = radar.ordered.get(i);
            long packed = pack(task.worldPos());
            if (isResolved(packed) || isClaimedByOther(packed, maidId) || unreachable.contains(packed)) {
                continue;
            }
            if (BuildPlacementCheck.wouldOccupyEntity(from, task.worldPos())) {
                continue;
            }
            if (claimLevel != null && !BuildCellSatisfiedPolicy.shouldClaim(matchesWorld(claimLevel, task))) {
                markAlreadyPlaced(task);
                continue;
            }
            checked++;
            if (requireMaterial) {
                hasMaterialCalls++;
                if (!materials.hasMaterial(task.raw().name())) {
                    radar.passRejected++;
                    continue;
                }
                radar.passAccepted++;
            }
            stocked.add(task);
        }
        radar.cursor = window.nextCursor();
        // Pass 2: only the nearest stocked prefix gets expensive survival / stand checks.
        List<BuildTask> near = ClaimScanBudgetPolicy.capForClaimOrder(
                stocked, ClaimScanBudgetPolicy.MAX_STOCKED_FOR_CLAIM_ORDER);
        List<BuildTask> candidates = new ArrayList<>();
        int surviveChecked = 0;
        for (BuildTask task : near) {
            if (claimLevel != null) {
                if (ClaimScanBudgetPolicy.surviveBudgetExhausted(
                        surviveChecked, ClaimScanBudgetPolicy.MAX_SURVIVE_CHECKS_PER_CLAIM)) {
                    break;
                }
                surviveChecked++;
                if (!BuildPlacementCheck.canSurvive(claimLevel, task, placementYaw)) {
                    if (awaitsSchematicSupport(claimLevel, task)) {
                        markDeferred(task);
                    } else {
                        markPlacementFailed(task);
                    }
                    continue;
                }
            }
            candidates.add(task);
        }
        if (from instanceof EntityMaid maid && claimLevel != null) {
            candidates = BuildClaimSpreadPolicy.orderForClaim(
                    candidates,
                    task -> BuildWorkStandOccupancy.hasFreeStand(claimLevel, maid, task.worldPos()),
                    localAnchor != null);
        }
        if (claimLevel != null) {
            candidates = BuildClaimEasePolicy.orderByEase(
                    candidates,
                    task -> BuildClaimEase.score(claimLevel, task.worldPos()),
                    BuildClaimEasePolicy.MAX_SCORED_PER_CLAIM);
        }
        candidates = BuildPlaceFailRetryPolicy.deprioritizeFailed(
                candidates, deprioritizeFailed, task -> pack(task.worldPos()));
        Optional<BuildTask> result = Optional.empty();
        for (BuildTask task : candidates) {
            if (tryClaim(pack(task.worldPos()), maidId)) {
                result = Optional.of(task);
                break;
            }
        }
        if (result.isPresent()) {
            clearClaimRadarsForMaid(maidId);
        } else if (requireMaterial
                && ClaimRadarPolicy.shouldStarveAfterPass(
                        window.passExhausted(), radar.passRejected, radar.passAccepted)) {
            noteClaimStarve(from, radar.passRejected, radar.passAccepted);
            claimRadars.remove(radarKey);
        }
        BuildPerfLog.claimNearest(key.projectId(), layer, checked, hasMaterialCalls, perfStart);
        return result;
    }

    private void clearClaimRadarsForMaid(UUID maidId) {
        claimRadars.keySet().removeIf(key -> key.maidId().equals(maidId));
    }

    private record ClaimRadarKey(UUID maidId, int layer) {
    }

    private static final class ClaimRadarState {
        private List<BuildTask> ordered = List.of();
        private int cursor;
        private int layer = Integer.MIN_VALUE;
        private int pendingSize = Integer.MIN_VALUE;
        private int stockRevision = Integer.MIN_VALUE;
        private long centerKey = Long.MIN_VALUE;
        private int passRejected;
        private int passAccepted;
    }

    /** True when the active layer has at least one pending block with stock (not ignored). */
    public boolean hasPlaceableTaskOnActiveLayer() {
        int layer = activeLayer();
        if (layer < 0) {
            return false;
        }
        int stockRevision = 0;
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        if (index != null) {
            stockRevision = index.revision();
        }
        int cacheKey = PlaceableScanCachePolicy.cacheKey(
                stockRevision, layer, pending.size(), unreachable.size(), deferred.size());
        if (PlaceableScanCachePolicy.isHit(placeableScanCacheKey, cacheKey, placeableScanCacheReady)) {
            return placeableScanCacheValue;
        }
        long perfStart = BuildPerfLog.start();
        int scanned = 0;
        boolean found = false;
        HashSet<String> seenBlockIds = new HashSet<>();
        try {
            boolean useIndex = PlaceableStockScanPolicy.preferStockIndex(index != null && index.isReady());
            for (BuildTask task : pending) {
                if (task.layerIndex() != layer) {
                    continue;
                }
                long packed = pack(task.worldPos());
                if (isResolved(packed) || unreachable.contains(packed)) {
                    continue;
                }
                String blockId = task.raw().name();
                if (PlaceableStockScanPolicy.shouldSkipDuplicate(blockId, !seenBlockIds.add(blockId))) {
                    continue;
                }
                scanned++;
                if (MaterialBlockPolicy.isAutoFree(blockId)) {
                    continue;
                }
                if (useIndex) {
                    if (index.hasForBlockId(blockId, MaterialVault.matchItemIds(blockId))) {
                        found = true;
                        break;
                    }
                } else if (materials.hasMaterial(blockId)) {
                    found = true;
                    break;
                }
            }
            placeableScanCacheKey = cacheKey;
            placeableScanCacheValue = found;
            placeableScanCacheReady = true;
            return found;
        } finally {
            BuildPerfLog.hasPlaceableScan(key.projectId(), layer, scanned, perfStart);
        }
    }

    /**
     * Like {@link #hasPlaceableTaskOnActiveLayer()} but only counts cells this maid may still
     * claim (not already held by another maid).
     */
    public boolean hasClaimablePlaceableTaskOnActiveLayer(UUID maidId) {
        int layer = activeLayer();
        if (layer < 0 || maidId == null) {
            return false;
        }
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        boolean useIndex = PlaceableStockScanPolicy.preferStockIndex(index != null && index.isReady());
        HashSet<String> seenBlockIds = new HashSet<>();
        for (BuildTask task : pending) {
            if (task.layerIndex() != layer) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (isResolved(packed) || unreachable.contains(packed) || isClaimedByOther(packed, maidId)) {
                continue;
            }
            String blockId = task.raw().name();
            if (PlaceableStockScanPolicy.shouldSkipDuplicate(blockId, !seenBlockIds.add(blockId))) {
                continue;
            }
            if (MaterialBlockPolicy.isAutoFree(blockId)) {
                continue;
            }
            if (useIndex) {
                if (index.hasForBlockId(blockId, MaterialVault.matchItemIds(blockId))) {
                    return true;
                }
            } else if (materials.hasMaterial(blockId)) {
                return true;
            }
        }
        return false;
    }

    /** Like {@link #hasPlaceableTaskOnActiveLayer()} but ignores one cell (e.g. the target just failed). */
    public boolean hasOtherPlaceableTaskOnActiveLayer(BlockPos exclude) {
        int layer = activeLayer();
        if (layer < 0) {
            return false;
        }
        long excludePacked = exclude == null ? Long.MIN_VALUE : pack(exclude);
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(materials);
        boolean useIndex = PlaceableStockScanPolicy.preferStockIndex(index != null && index.isReady());
        HashSet<String> seenBlockIds = new HashSet<>();
        for (BuildTask task : pending) {
            if (task.layerIndex() != layer) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (packed == excludePacked) {
                continue;
            }
            if (isResolved(packed) || unreachable.contains(packed)) {
                continue;
            }
            String blockId = task.raw().name();
            if (PlaceableStockScanPolicy.shouldSkipDuplicate(blockId, !seenBlockIds.add(blockId))) {
                continue;
            }
            if (MaterialBlockPolicy.isAutoFree(blockId)) {
                continue;
            }
            if (useIndex) {
                if (index.hasForBlockId(blockId, MaterialVault.matchItemIds(blockId))) {
                    return true;
                }
            } else if (materials.hasMaterial(blockId)) {
                return true;
            }
        }
        return false;
    }

    /** @deprecated use {@link #hasPlaceableTaskOnActiveLayer()} */
    @Deprecated
    public boolean hasPlaceableTask() {
        return hasPlaceableTaskOnActiveLayer();
    }

    public Optional<String> nearestMissingMaterialBlock(net.minecraft.world.entity.Entity from) {
        return nearestMissingMaterialBlock(from, null);
    }

    public Optional<String> nearestMissingMaterialBlock(net.minecraft.world.entity.Entity from, BlockPos localAnchor) {
        if (paused || done) {
            return Optional.empty();
        }
        int layer = activeLayer();
        if (layer < 0) {
            return Optional.empty();
        }
        BuildTask best = null;
        double bestDist = Double.MAX_VALUE;
        for (BuildTask task : pending) {
            if (task.layerIndex() != layer) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (isResolved(packed) || unreachable.contains(packed) || isClaimedByOther(packed, from.getUUID())) {
                continue;
            }
            if (BuildPlacementCheck.wouldOccupyEntity(from, task.worldPos())) {
                continue;
            }
            if (materials.hasMaterial(task.raw().name())) {
                continue;
            }
            if (!stillNeedsVaultMaterial(task.raw().name())) {
                continue;
            }
            double dist = distance(from, localAnchor, task.worldPos());
            if (dist < bestDist) {
                bestDist = dist;
                best = task;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.raw().name());
    }

    /** Release every cell this maid still holds so another pass can reclaim them. */
    public void releaseAllClaimsFor(UUID maidId) {
        claims.releaseAllFor(maidId);
    }

    static boolean wouldOccupyEntity(net.minecraft.world.entity.Entity entity, BlockPos target) {
        return BuildPlacementCheck.wouldOccupyEntity(entity, target);
    }

    public List<BuildTask> reachableFrom(ServerLevel level, net.minecraft.world.entity.LivingEntity entity,
                                         BlockPos fromFeet, int radius) {
        int layer = activeLayer();
        List<BuildTask> hits = new ArrayList<>();
        for (BuildTask task : snapshotPending()) {
            if (layer >= 0 && task.layerIndex() != layer) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (isResolved(packed) || unreachable.contains(packed)) {
                continue;
            }
            if (entity != null && isClaimedByOther(packed, entity.getUUID())) {
                continue;
            }
            if (BuildPlacementCheck.wouldOccupyEntity(entity, task.worldPos())) {
                continue;
            }
            if (manhattan(fromFeet, task.worldPos()) > radius) {
                continue;
            }
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) {
                continue;
            }
            if (!BuildWorkPosition.canInteractNow(level, maid, task.worldPos())) {
                continue;
            }
            hits.add(task);
        }
        hits.sort(Comparator
                .comparingInt(BuildTask::attachPriority)
                .thenComparingDouble(t -> fromFeet.distSqr(t.worldPos())));
        return hits;
    }

    /** Claim reachable tasks on the active layer for batch placement (strategy A). */
    public List<BuildTask> claimReachableBatch(
            ServerLevel level,
            net.minecraft.world.entity.LivingEntity entity,
            BlockPos fromFeet,
            int radius) {
        return claimReachableBatch(level, entity, fromFeet, radius, Set.of());
    }

    public List<BuildTask> claimReachableBatch(
            ServerLevel level,
            net.minecraft.world.entity.LivingEntity entity,
            BlockPos fromFeet,
            int radius,
            Set<Long> deprioritizeFailed) {
        UUID maidId = entity.getUUID();
        List<BuildTask> claimed = new ArrayList<>();
        List<BuildTask> reachable = BuildPlaceFailRetryPolicy.deprioritizeFailed(
                reachableFrom(level, entity, fromFeet, radius),
                deprioritizeFailed,
                task -> pack(task.worldPos()));
        for (BuildTask task : BuildBatchClaimPolicy.take(reachable, BuildBatchClaimPolicy.MAX_TASKS)) {
            if (!BuildCellSatisfiedPolicy.shouldClaim(matchesWorld(level, task))) {
                markAlreadyPlaced(task);
                continue;
            }
            if (!materials.hasMaterial(task.raw().name())) {
                continue;
            }
            long packed = pack(task.worldPos());
            if (tryClaim(packed, maidId)) {
                claimed.add(task);
            }
        }
        return claimed;
    }

    public boolean tryPlace(ServerLevel level, BuildTask task, net.minecraft.world.entity.Entity placer) {
        if (task != null && FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
            return false;
        }
        long packed = pack(task.worldPos());
        UUID claimant = placer == null ? SYSTEM_CLAIMANT : placer.getUUID();
        if (!tryClaim(packed, claimant)) {
            return false;
        }
        if (placed.contains(packed) || skipped.contains(packed)) {
            forceRelease(packed);
            pending.remove(task);
            return true;
        }
        if (isIgnored(task)) {
            skipTask(task);
            return true;
        }
        if (placer != null && BuildPlacementCheck.wouldOccupyEntity(placer, task.worldPos())) {
            releaseClaim(packed, claimant);
            return false;
        }
        if (placer instanceof EntityMaid maid
                && !BuildWorkPosition.canInteractNow(level, maid, task.worldPos())) {
            releaseClaim(packed, claimant);
            return false;
        }
        if (!materials.hasMaterial(task.raw().name())) {
            MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "hasMaterial=false");
            releaseClaim(packed, claimant);
            return false;
        }
        if (!BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
            if (awaitsSchematicSupport(level, task)) {
                markDeferred(task);
            } else {
                markPlacementFailed(task);
            }
            return false;
        }
        if (matchesWorld(level, task)) {
            markAlreadyPlaced(task);
            return true;
        }
        CoverTerrainClear.tryHarvest(level, this, task.worldPos());
        boolean ok = BlueprintPlacer.placeBlock(level, task, placementYaw, materials);
        if (!ok) {
            markDeferred(task);
            return false;
        }
        if (!materials.tryConsume(task.raw().name())) {
            MaterialDebugLog.tryPlaceFailed(this, task.raw().name(), task.worldPos(), "tryConsume=false");
            releaseClaim(packed, claimant);
            return false;
        }
        placed.add(packed);
        pending.remove(task);
        unreachable.remove(packed);
        deferred.remove(packed);
        failed.remove(packed);
        pathNavFailures.remove(packed);
        forceRelease(packed);
        BuildPlacementIndex.track(key, task.worldPos());
        consumedMaterials++;
        recordPlaced(task.raw().name());
        checkCompletion(level);
        return true;
    }

    /** Increment pathfinding-failure count for a task cell; at most once per second per cell. */
    public int recordPathNavFailure(ServerLevel level, BlockPos pos) {
        if (pos == null || level == null) {
            return 0;
        }
        long packed = pack(pos);
        long now = level.getGameTime();
        if (now - pathNavFailureRecordedAt.getOrDefault(packed, Long.MIN_VALUE)
                < PATH_NAV_FAILURE_RECORD_INTERVAL_TICKS) {
            return pathNavFailures.getOrDefault(packed, 0);
        }
        pathNavFailureRecordedAt.put(packed, now);
        return pathNavFailures.merge(packed, 1, Integer::sum);
    }

    public int pathNavFailuresAt(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        return pathNavFailures.getOrDefault(pack(pos), 0);
    }

    public void clearPathNavFailure(BlockPos pos) {
        if (pos != null) {
            pathNavFailures.remove(pack(pos));
        }
    }

    private void recordPlaced(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        placedByBlockId.merge(blockId, 1, Integer::sum);
    }

    private void unrecordPlaced(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        placedByBlockId.computeIfPresent(blockId, (id, count) -> count > 1 ? count - 1 : null);
    }

    public boolean isDeferred(BuildTask task) {
        return deferred.contains(pack(task.worldPos()));
    }

    public boolean isUnreachable(BuildTask task) {
        return unreachable.contains(pack(task.worldPos()));
    }

    public void markUnreachable(BuildTask task) {
        long packed = pack(task.worldPos());
        unreachable.add(packed);
        forceRelease(packed);
    }

    public void markDeferred(BuildTask task) {
        long packed = pack(task.worldPos());
        deferred.add(packed);
        pending.remove(task);
        unreachable.remove(packed);
        forceRelease(packed);
    }

    private void markPlacementFailed(BuildTask task) {
        if (task == null) {
            return;
        }
        long packed = pack(task.worldPos());
        if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
            return;
        }
        deferred.remove(packed);
        unreachable.remove(packed);
        pending.remove(task);
        failed.add(packed);
        forceRelease(packed);
        placementFailures.add(task.raw().name());
    }

    private void skipTask(BuildTask task) {
        long packed = pack(task.worldPos());
        pending.remove(task);
        skipped.add(packed);
        deferred.remove(packed);
        unreachable.remove(packed);
        failed.remove(packed);
        forceRelease(packed);
    }

    /** World already matches schematic 鈥?count progress without consuming materials. */
    private void markAlreadyPlaced(BuildTask task) {
        long packed = pack(task.worldPos());
        placed.add(packed);
        pending.remove(task);
        unreachable.remove(packed);
        deferred.remove(packed);
        failed.remove(packed);
        forceRelease(packed);
        BuildPlacementIndex.track(key, task.worldPos());
        recordPlaced(task.raw().name());
    }

    public void finishIfComplete(ServerLevel level) {
        checkCompletion(level);
    }

    /** Completion probe 鈥?same as {@link #finishIfComplete}; idle pickNext uses this for clarity. */
    public void probeCompletion(ServerLevel level) {
        checkCompletion(level);
    }

    public void releaseTask(BuildTask task, UUID maidId) {
        releaseClaim(pack(task.worldPos()), maidId);
    }

    private boolean tryClaim(long packed, UUID maidId) {
        return claims.tryClaim(packed, maidId);
    }

    private boolean isClaimedByOther(long packed, UUID maidId) {
        return claims.isClaimedByOther(packed, maidId);
    }

    private void releaseClaim(long packed, UUID maidId) {
        claims.releaseClaim(packed, maidId);
    }

    private void forceRelease(long packed) {
        claims.forceRelease(packed);
    }

    private void checkCompletion(ServerLevel level) {
        if (done) {
            return;
        }
        if (level != null) {
            failHopelessDeferred(level);
        }
        promoteReadyDeferred(level);
        if (!pending.isEmpty()) {
            return;
        }
        if (!deferred.isEmpty() && level != null) {
            retryDeferred(level);
        }
        if (!pending.isEmpty()) {
            return;
        }
        if (!deferred.isEmpty()) {
            if (FluidEndPlacePolicy.readyToDump(maidClaimablePendingCount(), claims.size())) {
                return;
            }
            recordRemainingDeferredAsFailures();
        }
        if (endFluidCursor < endFluids.size() || fluidRetouchCursor < fluidRetouch.size()) {
            return;
        }
        if (level != null) {
            BlueprintPlacer.spawnEntities(level, blueprint, anchor, placementYaw);
        }
        done = true;
        if (level != null) {
            BuildJobService.notifyJobComplete(this, level);
        }
    }

    private void recordRemainingDeferredAsFailures() {
        Map<String, Integer> counts = new HashMap<>();
        for (BuildTask task : allTasks) {
            long packed = pack(task.worldPos());
            if (!deferred.contains(packed)) {
                continue;
            }
            deferred.remove(packed);
            failed.add(packed);
            forceRelease(packed);
            counts.merge(task.raw().name(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            placementFailures.add(entry.getKey() + " x" + entry.getValue());
        }
    }

    private void retryDeferred(ServerLevel level) {
        List<BuildTask> retry = new ArrayList<>();
        for (BuildTask task : allTasks) {
            if (deferred.contains(pack(task.worldPos()))) {
                retry.add(task);
            }
        }
        deferred.clear();
        for (BuildTask task : retry) {
            if (isIgnored(task)) {
                skipped.add(pack(task.worldPos()));
                continue;
            }
            if (!materials.hasMaterial(task.raw().name())) {
                deferred.add(pack(task.worldPos()));
                continue;
            }
            if (!BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
                if (awaitsSchematicSupport(level, task)) {
                    deferred.add(pack(task.worldPos()));
                } else {
                    markPlacementFailed(task);
                }
                continue;
            }
            if (matchesWorld(level, task)) {
                markAlreadyPlaced(task);
                continue;
            }
            CoverTerrainClear.tryHarvest(level, this, task.worldPos());
            if (!BlueprintPlacer.placeBlock(level, task, placementYaw, materials)) {
                deferred.add(pack(task.worldPos()));
                continue;
            }
            if (!materials.tryConsume(task.raw().name())) {
                deferred.add(pack(task.worldPos()));
                continue;
            }
            placed.add(pack(task.worldPos()));
            BuildPlacementIndex.track(key, task.worldPos());
            consumedMaterials++;
            recordPlaced(task.raw().name());
        }
    }

    private List<BuildTask> snapshotPending() {
        return new ArrayList<>(pending);
    }

    private static double distance(net.minecraft.world.entity.Entity from, BlockPos localAnchor, BlockPos target) {
        if (localAnchor == null) {
            return from.distanceToSqr(
                    target.getX() + 0.5D,
                    target.getY() + 0.5D,
                    target.getZ() + 0.5D);
        }
        return localAnchor.distSqr(target);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    /** Visible to same-package unit tests. */
    boolean tryClaimPosition(BlockPos pos, UUID maidId) {
        return tryClaim(pack(pos), maidId);
    }

    /** Visible to same-package unit tests. */
    UUID claimOwner(BlockPos pos) {
        return claims.owner(pack(pos));
    }

    public static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }
}

