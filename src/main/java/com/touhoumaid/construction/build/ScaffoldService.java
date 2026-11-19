package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Place / reclaim job scaffolding and classify attempt outcomes. */
public final class ScaffoldService {
    /** Keep one full block gap between concurrently raised scaffold columns. */
    private static final int COLUMN_MIN_SPACING = 1;

    public enum Attempt {
        /** Existing stand / no new scaffolds required. */
        NOT_NEEDED,
        /** Placed one scaffold this tick; maid should climb/walk onto it. */
        PLACED,
        /** Walk or climb to workFeet before the next place. */
        WALK_TO,
        /** Maid is outside the build range — keep walking in, never tower and never direct-place. */
        OUT_OF_RANGE,
        /** Vault + backpack empty — must NOT direct-place. */
        MISSING_MATERIAL,
        /** Layout failed after a real scaffold attempt — may direct-place. */
        PLAN_FAILED,
        /**
         * Locked-path place cell was occupied / rejected — discard the lock and replan once.
         * Distinct from {@link #PLAN_FAILED} so the maid does not burn plan-fail streak on a stale lock.
         */
        PLACE_BLOCKED
    }

    /**
     * @param workFeet immediate step target (scaffold cell, climb cell, etc.)
     * @param routeFinalWorkFeet planned construction stand from {@link ScaffoldPlanner}; never a
     *     transient scaffold cell when advancing a locked route
     */
    public record AttemptResult(
            Attempt attempt,
            BlockPos workFeet,
            int placedCount,
            List<BlockPos> path,
            BlockPos routeFinalWorkFeet) {
        public AttemptResult(Attempt attempt, BlockPos workFeet, int placedCount, List<BlockPos> path) {
            this(attempt, workFeet, placedCount, path, null);
        }

        public AttemptResult(Attempt attempt, BlockPos workFeet, int placedCount) {
            this(attempt, workFeet, placedCount, List.of(), null);
        }

        public static AttemptResult of(Attempt attempt) {
            return new AttemptResult(attempt, null, 0, List.of(), null);
        }
    }

    private static AttemptResult routeStep(
            Attempt attempt, BlockPos stepFeet, int placed, List<BlockPos> path, BlockPos routeFinal) {
        return new AttemptResult(attempt, stepFeet, placed, path, routeFinal);
    }

    private ScaffoldService() {
    }

    public static AttemptResult tryScaffold(ServerLevel level, BuildJob job, EntityMaid maid, BlockPos target) {
        int layer = job.taskAt(target).map(BuildTask::layerIndex).orElseGet(job::activeLayer);
        if (!ScaffoldNeedPolicy.allowsScaffoldPlanning(layer)) {
            ScaffoldDebugLog.notNeeded(maid, maid.blockPosition(), "lowLayerNoScaffold");
            return AttemptResult.of(Attempt.PLAN_FAILED);
        }
        long gameTime = level.getGameTime();
        BlockPos standing = maid.blockPosition();
        Set<Long> passableFromExisting = ScaffoldGraphs.passableForReuseCheck(level, job, standing);
        List<ScaffoldLayout.Cell> candidates = BuildWorkPosition.gatherCandidates(level, maid, target).stream()
                .map(ScaffoldGraphs::cell)
                .toList();
        Optional<ScaffoldLayout.Cell> reuseStand = ScaffoldReusePolicy.pickReachableStandViaExistingScaffold(
                ScaffoldGraphs.cell(standing),
                ScaffoldGraphs.cell(target),
                candidates,
                passableFromExisting);
        if (reuseStand.isPresent()) {
            BlockPos stand = ScaffoldGraphs.toPos(reuseStand.get());
            ScaffoldDebugLog.notNeeded(maid, stand, "reuseExistingScaffold");
            return routeStep(Attempt.WALK_TO, stand, 0, List.of(), stand);
        }
        Optional<ScaffoldReusePolicy.ReuseStand> reuseColumn = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                ScaffoldGraphs.cell(standing),
                ScaffoldGraphs.cell(target),
                job.scaffoldColumnRegistry(),
                passableFromExisting);
        if (reuseColumn.isPresent()) {
            BlockPos stand = ScaffoldGraphs.toPos(reuseColumn.get().stand());
            ScaffoldDebugLog.notNeeded(maid, stand, "reuseExistingColumn");
            return routeStep(Attempt.WALK_TO, stand, 0, List.of(), stand);
        }
        // The cell she is working from is hers while she keeps at it; others must tower elsewhere.
        job.scaffoldColumns().occupy(standing.getX(), standing.getZ(), maid.getUUID(), gameTime);
        Set<Long> blockedColumns =
                job.scaffoldColumns().occupiedByOthersWithSpacing(maid.getUUID(), gameTime, COLUMN_MIN_SPACING);
        // Always allow extending the column under her own feet — shared towers must climb, not stall.
        blockedColumns.remove(ScaffoldLayout.packXz(standing.getX(), standing.getZ()));
        ScaffoldPlanner.Plan plan = ScaffoldPlanner.plan(
                level,
                maid,
                target,
                job::isPendingPlacementCell,
                job.scaffolds().packedPositions(),
                job.scaffoldColumnRegistry(),
                blockedColumns,
                job.scaffoldBox());
        if (plan.status() == ScaffoldPlanner.PlanStatus.NOT_NEEDED) {
            return new AttemptResult(Attempt.NOT_NEEDED, plan.workFeet(), 0, List.of());
        }
        if (plan.status() == ScaffoldPlanner.PlanStatus.OUT_OF_RANGE) {
            return AttemptResult.of(Attempt.OUT_OF_RANGE);
        }
        if (plan.status() == ScaffoldPlanner.PlanStatus.FAILED) {
            return AttemptResult.of(Attempt.PLAN_FAILED);
        }

        List<BlockPos> path = plan.path().isEmpty() ? plan.toPlace() : plan.path();
        if (path.isEmpty()) {
            return new AttemptResult(Attempt.NOT_NEEDED, plan.workFeet(), 0, List.of());
        }
        return advanceAlongPath(level, job, maid, path, plan.workFeet(), !plan.toPlace().isEmpty());
    }

    /**
     * Continue a previously planned scaffold route without re-running {@link ScaffoldPlanner}.
     * Call this while the lock is valid; on {@link Attempt#PLACE_BLOCKED} discard the path and
     * call {@link #tryScaffold} once.
     */
    public static AttemptResult advanceLockedPath(
            ServerLevel level,
            BuildJob job,
            EntityMaid maid,
            List<BlockPos> lockedPath,
            BlockPos workFeet) {
        if (lockedPath == null || lockedPath.isEmpty()) {
            return AttemptResult.of(Attempt.PLAN_FAILED);
        }
        boolean needsMaterial = false;
        for (BlockPos pos : lockedPath) {
            if (!ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(pos))
                    && !job.scaffolds().contains(pos)) {
                needsMaterial = true;
                break;
            }
        }
        return advanceAlongPath(level, job, maid, lockedPath, workFeet, needsMaterial);
    }

    private static AttemptResult advanceAlongPath(
            ServerLevel level,
            BuildJob job,
            EntityMaid maid,
            List<BlockPos> path,
            BlockPos workFeet,
            boolean needsMaterial) {
        long gameTime = level.getGameTime();
        Set<Long> present = new HashSet<>();
        List<ScaffoldLayout.Cell> cells = new ArrayList<>();
        for (BlockPos pos : path) {
            cells.add(new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ()));
            if (ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(pos))
                    || job.scaffolds().contains(pos)) {
                present.add(ScaffoldLayout.packCell(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        BlockPos maidFeet = maid.blockPosition();
        ScaffoldLayout.Cell routeWorkStand = workFeet != null
                ? new ScaffoldLayout.Cell(workFeet.getX(), workFeet.getY(), workFeet.getZ())
                : null;
        ScaffoldIncrementPolicy.Next next = ScaffoldIncrementPolicy.next(
                cells,
                present,
                new ScaffoldLayout.Cell(maidFeet.getX(), maidFeet.getY(), maidFeet.getZ()),
                routeWorkStand);
        ScaffoldDebugLog.next(
                maid,
                next.kind(),
                next.cell(),
                next.stand(),
                maidFeet);

        return switch (next.kind()) {
            case FAILED -> AttemptResult.of(Attempt.PLAN_FAILED);
            case DONE -> routeStep(Attempt.NOT_NEEDED, workFeet, 0, path, workFeet);
            case CLIMB -> {
                ScaffoldColumnSupport.snapOntoColumnIfBelowTop(level, maid);
                yield routeStep(
                        Attempt.WALK_TO,
                        toPos(next.cell(), workFeet),
                        0,
                        path,
                        workFeet);
            }
            case APPROACH -> {
                if (needsMaterial && !ScaffoldMaterials.hasAny(job.materials(), maid)) {
                    yield AttemptResult.of(Attempt.MISSING_MATERIAL);
                }
                // Walk to the footing stand so the next PLACE can happen; unfinished workFeet is
                // not pathable yet. Mid-climb, ConstructionWalkPolicy keeps a higher workPosition.
                ScaffoldLayout.Cell walkCell = ScaffoldIncrementPolicy.approachWalkCell(
                        next, ScaffoldGraphs.cell(workFeet != null ? workFeet : path.get(path.size() - 1)));
                yield routeStep(Attempt.WALK_TO, toPos(walkCell, workFeet), 0, path, workFeet);
            }
            case PLACE -> {
                if (!ScaffoldMaterials.hasAny(job.materials(), maid)) {
                    yield AttemptResult.of(Attempt.MISSING_MATERIAL);
                }
                BlockPos cell = toPos(next.cell(), null);
                if (cell == null) {
                    yield AttemptResult.of(Attempt.PLAN_FAILED);
                }
                if (workFeet != null && cell.getY() > workFeet.getY()) {
                    yield routeStep(Attempt.PLACE_BLOCKED, workFeet, 0, path, workFeet);
                }
                if (workFeet != null
                        && cell.getX() == workFeet.getX()
                        && cell.getZ() == workFeet.getZ()
                        && cell.getY() >= workFeet.getY()) {
                    yield routeStep(Attempt.WALK_TO, workFeet, 0, path, workFeet);
                }
                if (!ScaffoldPlacementPhasePolicy.mayPlaceNow(maidFeet, cell, cells, present)) {
                    BlockPos waitAt = toPos(next.stand(), workFeet);
                    yield routeStep(
                            Attempt.WALK_TO,
                            waitAt != null ? waitAt : cell,
                            0,
                            path,
                            workFeet);
                }
                // #region agent log
                var placeDbg = AgentDebugLog.data();
                placeDbg.addProperty("maid", ConstructionDebugIds.maid(maid));
                placeDbg.addProperty("cell", cell.toShortString());
                placeDbg.addProperty("maidAt", maid.blockPosition().toShortString());
                placeDbg.addProperty("blockAbove1", level.getBlockState(cell.above()).getBlock().getDescriptionId());
                placeDbg.addProperty("blockAbove2", level.getBlockState(cell.above(2)).getBlock().getDescriptionId());
                placeDbg.addProperty("solidAbove", !level.getBlockState(cell.above()).isAir()
                        && !level.getBlockState(cell.above()).canBeReplaced());
                AgentDebugLog.log("H3", "ScaffoldService.advanceAlongPath", "place_attempt", placeDbg);
                // #endregion
                BlockState existing = level.getBlockState(cell);
                if (ConstructionScaffoldBlocks.isMaidPlacedScaffold(existing)) {
                    recordScaffoldBlock(job, cell);
                    yield routeStep(Attempt.WALK_TO, cell, 0, path, workFeet);
                }
                if (ConstructionScaffoldBlocks.isWalkableScaffold(existing)) {
                    yield routeStep(Attempt.WALK_TO, cell, 0, path, workFeet);
                }
                if (!level.getBlockState(cell).isAir() && !level.getBlockState(cell).canBeReplaced()) {
                    yield routeStep(Attempt.PLACE_BLOCKED, workFeet, 0, path, workFeet);
                }
                if (!ScaffoldHeadroomPolicy.hasStandHeadroom(level, cell)) {
                    yield routeStep(Attempt.PLACE_BLOCKED, workFeet, 0, path, workFeet);
                }
                java.util.List<BlockPos> ledgerCells = new java.util.ArrayList<>();
                for (long packed : job.scaffolds().packedPositions()) {
                    ledgerCells.add(BlockPos.of(packed));
                }
                if (!ScaffoldAttachmentPolicy.canPlaceAt(
                        level, cell, ledgerCells, maid.blockPosition())) {
                    // #region agent log
                    var attachDbg = AgentDebugLog.data();
                    attachDbg.addProperty("maid", ConstructionDebugIds.maid(maid));
                    attachDbg.addProperty("cell", cell.toShortString());
                    attachDbg.addProperty("maidAt", maid.blockPosition().toShortString());
                    AgentDebugLog.log("H8", "ScaffoldService.advanceAlongPath", "place_no_attach", attachDbg);
                    // #endregion
                    yield routeStep(Attempt.PLACE_BLOCKED, workFeet, 0, path, workFeet);
                }
                ScaffoldMaterials.Result stock = ScaffoldMaterials.tryConsumeOne(job.materials(), maid);
                if (stock == ScaffoldMaterials.Result.MISSING) {
                    yield AttemptResult.of(Attempt.MISSING_MATERIAL);
                }
                if (!ScaffoldPlanner.placeScaffoldBlock(
                        level, cell, ledgerCells, maid.blockPosition())) {
                    ScaffoldMaterials.depositToVault(job.materials(), 1);
                    yield routeStep(Attempt.PLACE_BLOCKED, workFeet, 0, path, workFeet);
                }
                recordScaffoldBlock(job, cell);
                job.scaffoldColumns().occupy(cell.getX(), cell.getZ(), maid.getUUID(), gameTime);
                BlockPos feetBeforePlace = maid.blockPosition();
                if (ConstructionWalkPolicy.shouldSnapUpAfterVerticalPlace(
                        feetBeforePlace.getX(),
                        feetBeforePlace.getY(),
                        feetBeforePlace.getZ(),
                        cell.getX(),
                        cell.getY(),
                        cell.getZ())) {
                    ScaffoldColumnSupport.snapOntoColumnIfBelowTop(level, maid);
                }
                yield routeStep(Attempt.PLACED, cell, 1, path, workFeet);
            }
        };
    }

    /** Place a single scaffold at {@code cell} (under-feet fill / local column). */
    public static boolean tryPlaceOne(ServerLevel level, BuildJob job, EntityMaid maid, BlockPos cell) {
        if (cell == null || job == null || maid == null) {
            return false;
        }
        if (!ScaffoldBoundsPolicy.contains(
                job.scaffoldBox(), ScaffoldBoundsPolicy.MARGIN, cell.getX(), cell.getY(), cell.getZ())) {
            ScaffoldDebugLog.boundsGate(
                    maid, cell, job.scaffoldBox(), ScaffoldBoundsPolicy.MARGIN, "cell-outside");
            return false;
        }
        BlockState existing = level.getBlockState(cell);
        if (ConstructionScaffoldBlocks.isMaidPlacedScaffold(existing)) {
            recordScaffoldBlock(job, cell);
            return false;
        }
        if (ConstructionScaffoldBlocks.isWalkableScaffold(existing)) {
            return false;
        }
        if (!level.getBlockState(cell).isAir() && !level.getBlockState(cell).canBeReplaced()) {
            return false;
        }
        if (!ScaffoldMaterials.hasAny(job.materials(), maid)) {
            return false;
        }
        ScaffoldMaterials.Result stock = ScaffoldMaterials.tryConsumeOne(job.materials(), maid);
        if (stock == ScaffoldMaterials.Result.MISSING) {
            return false;
        }
        java.util.List<BlockPos> ledgerCells = new java.util.ArrayList<>();
        for (long packed : job.scaffolds().packedPositions()) {
            ledgerCells.add(BlockPos.of(packed));
        }
        if (!ScaffoldPlanner.placeScaffoldBlock(level, cell, ledgerCells, maid.blockPosition())) {
            ScaffoldMaterials.depositToVault(job.materials(), 1);
            return false;
        }
        recordScaffoldBlock(job, cell);
        job.scaffoldColumns().occupy(cell.getX(), cell.getZ(), maid.getUUID(), level.getGameTime());
        ScaffoldDebugLog.fillUnderFeet(maid, cell);
        return true;
    }

    private static void recordScaffoldBlock(BuildJob job, BlockPos cell, int baseY) {
        job.scaffolds().record(cell);
        job.scaffoldColumnRegistry().recordBlock(cell.getX(), cell.getY(), cell.getZ(), baseY);
    }

    private static void recordScaffoldBlock(BuildJob job, BlockPos cell) {
        recordScaffoldBlock(job, cell, cell.getY() - 1);
    }

    private static void removeScaffoldBlock(BuildJob job, BlockPos cell) {
        job.scaffolds().remove(cell);
        job.scaffoldColumnRegistry().rescanColumn(
                cell.getX(), cell.getZ(), job.scaffolds().packedPositions());
    }

    private static BlockPos toPos(ScaffoldLayout.Cell cell, BlockPos fallback) {
        if (cell == null) {
            return fallback;
        }
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    /** Break maid staging scaffolds recorded on this job, then any still standing around the site. */
    public static int reclaimAll(ServerLevel level, BuildJob job) {
        int reclaimed = breakAndRefund(level, job, ledgerTopDown(job));
        reclaimed += reclaimAround(level, job.scaffoldBox(), job.materials(), job.anchor(), job);
        job.scaffolds().clear();
        job.scaffoldColumnRegistry().clear();
        return reclaimed;
    }

    /**
     * Break maid staging blocks in loaded chunks around the footprint, including towers outside
     * the tight build margin that were never on the in-memory ledger (for example after a rejoin).
     * Vanilla blueprint scaffolding is left alone. Unloaded chunks are not force-loaded.
     */
    public static int reclaimAround(
            ServerLevel level,
            ScaffoldBoundsPolicy.Box box,
            MaterialSource vault,
            BlockPos dropAt) {
        return reclaimAround(level, box, vault, dropAt, null);
    }

    /**
     * Tear down scaffolds this job left outside the build range — a stale tower a maid raised
     * before the range gate, or one left behind by a relocated site.
     */
    public static int reclaimOutOfRange(ServerLevel level, BuildJob job) {
        ScaffoldBoundsPolicy.Box box = job.scaffoldBox();
        if (box == null) {
            return 0;
        }
        List<BlockPos> stray = new ArrayList<>();
        for (BlockPos pos : ledgerTopDown(job)) {
            if (!ScaffoldBoundsPolicy.contains(
                    box, ScaffoldBoundsPolicy.MARGIN, pos.getX(), pos.getY(), pos.getZ())) {
                stray.add(pos);
            }
        }
        if (stray.isEmpty()) {
            return 0;
        }
        int reclaimed = breakAndRefund(level, job, stray);
        ScaffoldDebugLog.reclaimOutOfRange(job, box, ScaffoldBoundsPolicy.MARGIN, stray.size());
        return reclaimed;
    }

    private static List<BlockPos> ledgerTopDown(BuildJob job) {
        List<BlockPos> positions = new ArrayList<>();
        for (long p : job.scaffolds().packedPositions()) {
            positions.add(BlockPos.of(p));
        }
        positions.sort(Comparator
                .<BlockPos>comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return positions;
    }

    private static int reclaimAround(
            ServerLevel level,
            ScaffoldBoundsPolicy.Box box,
            MaterialSource vault,
            BlockPos dropAt,
            BuildJob owner) {
        if (level == null || box == null || dropAt == null) {
            return 0;
        }
        int margin = ScaffoldReclaimSweepPolicy.MARGIN;
        int minX = box.minX() - margin;
        int maxX = box.maxX() + margin;
        int minZ = box.minZ() - margin;
        int maxZ = box.maxZ() + margin;
        int minY = Math.max(level.getMinBuildHeight(), box.minY() - margin);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, box.maxY() + margin);
        if (minY > maxY) {
            return 0;
        }
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                int x0 = Math.max(minX, cx << 4);
                int x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4);
                int z1 = Math.min(maxZ, (cz << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = maxY; y >= minY; y--) {
                            cursor.set(x, y, z);
                            if (recordedOnAnotherJob(owner, cursor)) {
                                continue;
                            }
                            if (ConstructionScaffoldBlocks.isMaidPlacedScaffold(level.getBlockState(cursor))) {
                                found.add(cursor.immutable());
                            }
                        }
                    }
                }
            }
        }
        return breakPresent(level, owner, vault, dropAt, found);
    }

    private static boolean recordedOnAnotherJob(BuildJob owner, BlockPos pos) {
        for (BuildJob other : BuildJobRegistry.all().values()) {
            if (other == owner) {
                continue;
            }
            if (other.scaffolds().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static int breakAndRefund(ServerLevel level, BuildJob job, List<BlockPos> positions) {
        int reclaimed = 0;
        for (BlockPos pos : positions) {
            if (!ConstructionScaffoldBlocks.isMaidPlacedScaffold(level.getBlockState(pos))) {
                removeScaffoldBlock(job, pos);
                continue;
            }
            if (level.destroyBlock(pos, false)) {
                reclaimed++;
                removeScaffoldBlock(job, pos);
            }
        }
        refund(level, job.materials(), job.anchor(), reclaimed);
        return reclaimed;
    }

    /** Positions already known to be maid scaffolds. Does not drop missing ledger entries. */
    private static int breakPresent(
            ServerLevel level, BuildJob owner, MaterialSource vault, BlockPos dropAt, List<BlockPos> positions) {
        int reclaimed = 0;
        for (BlockPos pos : positions) {
            if (!ConstructionScaffoldBlocks.isMaidPlacedScaffold(level.getBlockState(pos))) {
                continue;
            }
            if (level.destroyBlock(pos, false)) {
                reclaimed++;
                if (owner != null) {
                    removeScaffoldBlock(owner, pos);
                }
            }
        }
        refund(level, vault, dropAt, reclaimed);
        return reclaimed;
    }

    private static void refund(ServerLevel level, MaterialSource vault, BlockPos dropAt, int reclaimed) {
        if (reclaimed <= 0 || level == null || dropAt == null) {
            return;
        }
        int leftover = vault == null ? reclaimed : ScaffoldMaterials.depositToVault(vault, reclaimed);
        while (leftover > 0) {
            int count = Math.min(64, leftover);
            ItemStack stack = new ItemStack(Items.SCAFFOLDING, count);
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    level,
                    dropAt.getX() + 0.5D,
                    dropAt.getY() + 1.0D,
                    dropAt.getZ() + 0.5D,
                    stack));
            leftover -= count;
        }
    }
}
