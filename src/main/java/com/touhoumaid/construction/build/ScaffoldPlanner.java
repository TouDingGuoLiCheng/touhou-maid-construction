package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plans scaffolding as an approach path to a <em>solid</em> work stand.
 * Simulate reach (air may become scaffold), then place only along the deduced route.
 * Never treats scaffolding itself as the construction work stand.
 */
public final class ScaffoldPlanner {
    public static final int MAX_HORIZONTAL = 7;
    private static final int MAX_TOWER_SEARCH = 12;
    private static final int MAX_GROUND_DROP = 48;

    public enum PlanStatus {
        NOT_NEEDED,
        READY,
        /** Maid is still outside the allowed build range — walk in first, never tower here. */
        OUT_OF_RANGE,
        FAILED
    }

    public record Plan(PlanStatus status, List<BlockPos> path, List<BlockPos> toPlace, BlockPos workFeet) {
        public static Plan notNeeded() {
            return new Plan(PlanStatus.NOT_NEEDED, List.of(), List.of(), null);
        }

        public static Plan notNeeded(BlockPos workFeet) {
            return new Plan(PlanStatus.NOT_NEEDED, List.of(), List.of(), workFeet);
        }

        public static Plan ready(List<BlockPos> path, List<BlockPos> toPlace, BlockPos workFeet) {
            return new Plan(PlanStatus.READY, List.copyOf(path), List.copyOf(toPlace), workFeet);
        }

        public static Plan failed() {
            return new Plan(PlanStatus.FAILED, List.of(), List.of(), null);
        }

        public static Plan outOfRange() {
            return new Plan(PlanStatus.OUT_OF_RANGE, List.of(), List.of(), null);
        }
    }

    private ScaffoldPlanner() {
    }

    public static Plan plan(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds) {
        return plan(level, maid, target, isPendingBlueprintCell, alreadyJobScaffolds, Set.of(), null);
    }

    /**
     * @param blockedColumnXz packed XZ of columns other maids are currently raising
     */
    public static Plan plan(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds,
            Set<Long> blockedColumnXz) {
        return plan(level, maid, target, isPendingBlueprintCell, alreadyJobScaffolds, blockedColumnXz, null);
    }

    public static Plan plan(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds,
            Set<Long> blockedColumnXz,
            ScaffoldBoundsPolicy.Box box) {
        return plan(
                level,
                maid,
                target,
                isPendingBlueprintCell,
                alreadyJobScaffolds,
                null,
                blockedColumnXz,
                box);
    }

    /**
     * @param columnRegistry  job column landing/max-stand metadata for vertical reuse
     * @param blockedColumnXz packed XZ of columns other maids are currently raising
     * @param box             blueprint footprint; scaffolding may only exist inside it plus
     *                        {@link ScaffoldBoundsPolicy#MARGIN}. {@code null} disables the range gate.
     */
    public static Plan plan(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds,
            ScaffoldColumnRegistry columnRegistry,
            Set<Long> blockedColumnXz,
            ScaffoldBoundsPolicy.Box box) {
        if (maid != null) {
            BlockPos notNeededStand = findStandNeedingNoScaffold(level, maid, target, alreadyJobScaffolds);
            if (notNeededStand != null) {
                ScaffoldDebugLog.plan(
                        maid,
                        maid.blockPosition(),
                        target,
                        PlanStatus.NOT_NEEDED,
                        notNeededStand,
                        0,
                        0,
                        alreadyJobScaffolds.size());
                return Plan.notNeeded(notNeededStand);
            }
            BlockPos maidFeet = maid.blockPosition();
            if (!ScaffoldBoundsPolicy.contains(
                    box, ScaffoldBoundsPolicy.MARGIN, maidFeet.getX(), maidFeet.getY(), maidFeet.getZ())) {
                ScaffoldDebugLog.boundsGate(
                        maid, maidFeet, box, ScaffoldBoundsPolicy.MARGIN, "maid-outside");
                return Plan.outOfRange();
            }
        }

        Plan tower = planApproach(
                level, maid, target, isPendingBlueprintCell, alreadyJobScaffolds, columnRegistry,
                blockedColumnXz, box, ScaffoldBoundsPolicy.MARGIN);
        if (tower == null && box != null) {
            tower = planApproach(
                    level, maid, target, isPendingBlueprintCell, alreadyJobScaffolds, columnRegistry,
                    blockedColumnXz, box, ScaffoldBoundsPolicy.RELAXED_MARGIN);
            if (tower != null && maid != null) {
                ScaffoldDebugLog.boundsGate(
                        maid, maid.blockPosition(), box, ScaffoldBoundsPolicy.RELAXED_MARGIN, "relaxed-pass");
            }
        }
        if (tower != null) {
            if (maid != null) {
                ScaffoldDebugLog.plan(
                        maid,
                        maid.blockPosition(),
                        target,
                        PlanStatus.READY,
                        tower.workFeet(),
                        tower.path().size(),
                        tower.toPlace().size(),
                        alreadyJobScaffolds.size());
            }
            return tower;
        }
        if (maid != null) {
            ScaffoldDebugLog.plan(
                    maid,
                    maid.blockPosition(),
                    target,
                    PlanStatus.FAILED,
                    null,
                    0,
                    0,
                    alreadyJobScaffolds.size());
        }
        return Plan.failed();
    }

    /**
     * A stand she can already use: vanilla pathing, the existing scaffold graph, or simply a
     * step she can walk/jump. Returns {@code null} when scaffolding is genuinely required.
     */
    private static BlockPos findStandNeedingNoScaffold(
            ServerLevel level, EntityMaid maid, BlockPos target, Set<Long> alreadyJobScaffolds) {
        Set<Long> scaffoldPassable =
                ScaffoldGraphs.passableFromLedger(alreadyJobScaffolds, maid.blockPosition());
        BlockPos maidFeet = maid.blockPosition();
        boolean maidOnScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(maidFeet))
                || ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(maidFeet.below()));
        for (BlockPos feet : BuildWorkPosition.gatherCandidates(level, maid, target)) {
            if (feet.getY() > target.getY()) {
                continue;
            }
            int dx = feet.getX() - maidFeet.getX();
            int dy = feet.getY() - maidFeet.getY();
            int dz = feet.getZ() - maidFeet.getZ();
            if (ScaffoldNeedPolicy.isTriviallyReachable(dx, dy, dz, maidOnScaffold) && canStandAt(level, feet)) {
                ScaffoldDebugLog.notNeeded(maid, feet, "trivialGeometry");
                return feet;
            }
            if (ScaffoldFillUnderFeetPolicy.shouldSkipNotNeededNeighborStand(Math.abs(dx) + Math.abs(dz), dy)) {
                continue;
            }
            if (ScaffoldNeedPolicy.trustVanillaPathReach(dy) && maid.canPathReach(feet)) {
                ScaffoldDebugLog.notNeeded(maid, feet, "vanilla");
                return feet;
            }
            if (ScaffoldNeedPolicy.trustVanillaPathReach(dy)) {
                Set<Long> withStand = new HashSet<>(scaffoldPassable);
                withStand.add(ScaffoldLayout.packCell(feet.getX(), feet.getY(), feet.getZ()));
                if (ScaffoldGraphs.canReach(maidFeet, feet, withStand)) {
                    ScaffoldDebugLog.notNeeded(maid, feet, "scaffoldGraph");
                    return feet;
                }
            }
        }
        return null;
    }

    /**
     * Simulate vertical+horizontal reach treating air as future scaffolds, then place only along
     * the deduced path. Work stand stays solid footing — never a scaffold cell.
     */
    private static Plan planApproach(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds,
            ScaffoldColumnRegistry columnRegistry,
            Set<Long> blockedColumnXz,
            ScaffoldBoundsPolicy.Box box,
            int margin) {
        if (maid == null) {
            return null;
        }
        BlockPos maidFeet = maid.blockPosition();
        List<BlockPos> solidStands = BuildWorkPosition.gatherSolidStands(level, maid, target);
        if (solidStands.isEmpty()) {
            ScaffoldDebugLog.approachSim(
                    maid, maidFeet, target, null, "noSolidStand", 0, 0, 0, 0, "-");
            return null;
        }
        Set<Long> existing = new HashSet<>(
                ScaffoldGraphs.passableFromLedger(alreadyJobScaffolds, maidFeet));
        if (columnRegistry != null) {
            ScaffoldGraphs.addRegistryColumns(existing, columnRegistry, maidFeet);
        }
        ScaffoldGraphs.addWorldScaffolds(level, maidFeet, existing);
        Set<Long> air = collectAirPassable(
                level, maidFeet, solidStands, isPendingBlueprintCell, box, margin);
        ScaffoldDebugLog.approachSim(
                maid,
                maidFeet,
                target,
                null,
                "begin",
                existing.size(),
                air.size(),
                0,
                solidStands.size(),
                "candidates=" + solidStands.size());
        ScaffoldLayout.Cell from = ScaffoldGraphs.cell(maidFeet);
        Set<Long> existingColumnXz = columnXz(alreadyJobScaffolds);
        if (columnRegistry != null) {
            for (ScaffoldColumnRegistry.Column column : columnRegistry.columns()) {
                existingColumnXz.add(ScaffoldLayout.packXz(column.x(), column.z()));
            }
        }
        Plan best = null;
        long bestCost = Long.MAX_VALUE;
        for (BlockPos stand : solidStands) {
            Optional<ScaffoldApproachSim.Result> sim = simulateApproachForStand(
                    level,
                    from,
                    stand,
                    target,
                    maidFeet,
                    existing,
                    air,
                    isPendingBlueprintCell,
                    blockedColumnXz);
            if (sim.isEmpty()) {
                continue;
            }
            List<BlockPos> path = new ArrayList<>();
            List<BlockPos> toPlace = new ArrayList<>();
            Set<Long> planned = new HashSet<>(alreadyJobScaffolds);
            boolean ok = true;
            String failReason = null;
            for (ScaffoldLayout.Cell cell : sim.get().toPlace()) {
                BlockPos pos = ScaffoldGraphs.toPos(cell);
                if (box != null && !ScaffoldBoundsPolicy.contains(
                        box, margin, pos.getX(), pos.getY(), pos.getZ())) {
                    ok = false;
                    failReason = "cell-outside:" + pos.toShortString();
                    break;
                }
                if (!appendScaffoldCell(level, pos, isPendingBlueprintCell, planned, toPlace, maidFeet)) {
                    ok = false;
                    failReason = "place-reject:" + pos.toShortString();
                    break;
                }
            }
            if (!ok) {
                ScaffoldDebugLog.approachSim(
                        maid,
                        maidFeet,
                        target,
                        stand,
                        "placeFail",
                        existing.size(),
                        air.size(),
                        sim.get().path().size(),
                        sim.get().toPlace().size(),
                        failReason);
                continue;
            }
            for (ScaffoldLayout.Cell cell : sim.get().path()) {
                path.add(ScaffoldGraphs.toPos(cell));
            }
            String route = ScaffoldDebugLog.summarizeRoute(sim.get().path());
            long cost = ScaffoldRouteCostPolicy.cost(
                    toPlace, path, existingColumnXz, target.getX(), target.getZ());
            if (cost >= bestCost) {
                continue;
            }
            bestCost = cost;
            if (toPlace.isEmpty()) {
                ScaffoldDebugLog.approachSim(
                        maid,
                        maidFeet,
                        target,
                        stand,
                        "existing",
                        existing.size(),
                        air.size(),
                        path.size(),
                        0,
                        route);
                ScaffoldDebugLog.notNeeded(maid, stand, "simPathExisting");
                best = Plan.notNeeded(stand);
            } else {
                ScaffoldDebugLog.approachSim(
                        maid,
                        maidFeet,
                        target,
                        stand,
                        "ready",
                        existing.size(),
                        air.size(),
                        path.size(),
                        toPlace.size(),
                        route);
                best = Plan.ready(path, toPlace, stand);
            }
        }
        if (best != null) {
            return best;
        }
        ScaffoldDebugLog.approachSim(
                maid,
                maidFeet,
                target,
                null,
                "allMissed",
                existing.size(),
                air.size(),
                0,
                solidStands.size(),
                "candidates=" + solidStands.size());
        return null;
    }

    /**
     * Prefer climbing on a column planted against the building (nudged outward when the shaft is
     * blocked), then bridge to the solid stand. Falls back to a direct air path when that fails.
     */
    private static Optional<ScaffoldApproachSim.Result> simulateApproachForStand(
            ServerLevel level,
            ScaffoldLayout.Cell from,
            BlockPos stand,
            BlockPos target,
            BlockPos maidFeet,
            Set<Long> existing,
            Set<Long> air,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> blockedColumnXz) {
        ScaffoldLayout.Cell standCell = ScaffoldGraphs.cell(stand);
        int baseY = Math.min(maidFeet.getY(), stand.getY());
        int topY = Math.max(maidFeet.getY(), stand.getY());
        Optional<ScaffoldLayout.Cell> column = ScaffoldColumnSitePolicy.pick(
                stand.getX(),
                stand.getY(),
                stand.getZ(),
                target.getX(),
                target.getZ(),
                baseY,
                topY,
                (x, y, z) -> shaftCellBlocked(
                        level, x, y, z, isPendingBlueprintCell, blockedColumnXz, stand),
                ScaffoldColumnSitePolicy.DEFAULT_MAX_OUTWARD);
        if (column.isPresent()) {
            ScaffoldLayout.Cell climbTop = new ScaffoldLayout.Cell(
                    column.get().x(), stand.getY(), column.get().z());
            Optional<ScaffoldApproachSim.Result> via = ScaffoldApproachSim.simulateViaWaypoint(
                    from,
                    climbTop,
                    standCell,
                    existing,
                    air,
                    ScaffoldPathfinder.MAX_NODES);
            if (via.isPresent()) {
                return via;
            }
        }
        return ScaffoldApproachSim.simulate(
                from, standCell, existing, air, ScaffoldPathfinder.MAX_NODES);
    }

    private static boolean shaftCellBlocked(
            ServerLevel level,
            int x,
            int y,
            int z,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> blockedColumnXz,
            BlockPos stand) {
        if (blockedColumnXz != null
                && blockedColumnXz.contains(ScaffoldLayout.packXz(x, z))
                && (stand.getX() != x || stand.getZ() != z)) {
            return true;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (isPendingBlueprintCell != null && isPendingBlueprintCell.test(pos)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return false;
        }
        return !ConstructionScaffoldBlocks.isWalkableScaffold(state);
    }

    /** Air/replaceable cells the maid could pass through after scaffolds are placed. */
    private static Set<Long> collectAirPassable(
            ServerLevel level,
            BlockPos maidFeet,
            List<BlockPos> solidStands,
            Predicate<BlockPos> isPendingBlueprintCell,
            ScaffoldBoundsPolicy.Box box,
            int margin) {
        int minX = maidFeet.getX();
        int maxX = maidFeet.getX();
        int minY = maidFeet.getY();
        int maxY = maidFeet.getY();
        int minZ = maidFeet.getZ();
        int maxZ = maidFeet.getZ();
        for (BlockPos stand : solidStands) {
            minX = Math.min(minX, stand.getX());
            maxX = Math.max(maxX, stand.getX());
            minY = Math.min(minY, stand.getY());
            maxY = Math.max(maxY, stand.getY());
            minZ = Math.min(minZ, stand.getZ());
            maxZ = Math.max(maxZ, stand.getZ());
        }
        int pad = MAX_HORIZONTAL;
        minX -= pad;
        maxX += pad;
        minY -= 2;
        maxY += 2;
        minZ -= pad;
        maxZ += pad;
        Set<Long> air = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (box != null && !ScaffoldBoundsPolicy.contains(box, margin, x, y, z)) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    if (isPendingBlueprintCell.test(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (ConstructionScaffoldBlocks.isWalkableScaffold(state)) {
                        continue;
                    }
                    if (state.isAir() || state.canBeReplaced()) {
                        air.add(ScaffoldLayout.packCell(x, y, z));
                    }
                }
            }
        }
        return air;
    }

    private static Set<Long> columnXz(Set<Long> packedPositions) {
        Set<Long> xz = new HashSet<>();
        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            xz.add(ScaffoldLayout.packXz(pos.getX(), pos.getZ()));
        }
        return xz;
    }

    /** Overload without maid path checks (tests / fallback). */
    @Deprecated
    public static Plan plan(
            ServerLevel level,
            BlockPos target,
            Predicate<BlockPos> isPendingBlueprintCell,
            Set<Long> alreadyJobScaffolds) {
        return plan(level, null, target, isPendingBlueprintCell, alreadyJobScaffolds);
    }

    private static List<BlockPos> candidateWorkFeet(
            ServerLevel level, BlockPos target, Predicate<BlockPos> isPending) {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos candidate : BlockPos.withinManhattan(target, 4, 8, 4)) {
            BlockPos feet = candidate.immutable();
            if (feet.equals(target)) {
                continue;
            }
            if (isPending.test(feet) || isPending.test(feet.above())) {
                continue;
            }
            if (canInteractFrom(level, feet, target)) {
                list.add(feet);
            }
        }
        list.sort((a, b) -> Integer.compare(manhattan(a, target), manhattan(b, target)));
        return list;
    }

    private static Optional<List<BlockPos>> buildScaffoldPath(
            ServerLevel level,
            BlockPos feet,
            int maidY,
            Predicate<BlockPos> isPending,
            Set<Long> alreadyJobScaffolds) {
        BlockPos support = findColumnSupport(level, feet, maidY, isPending);
        if (support == null) {
            return Optional.empty();
        }

        List<BlockPos> place = new ArrayList<>();
        Set<Long> planned = new HashSet<>(alreadyJobScaffolds);

        int groundY = support.getY();
        for (int y = groundY + 1; y <= feet.getY(); y++) {
            BlockPos cell = new BlockPos(support.getX(), y, support.getZ());
            if (!appendScaffoldCell(level, cell, isPending, planned, place, null)) {
                return Optional.empty();
            }
        }

        int manhattanHoriz = Math.abs(feet.getX() - support.getX()) + Math.abs(feet.getZ() - support.getZ());
        if (manhattanHoriz > MAX_HORIZONTAL) {
            return Optional.empty();
        }

        BlockPos cursor = new BlockPos(support.getX(), feet.getY(), support.getZ());
        while (cursor.getX() != feet.getX() || cursor.getZ() != feet.getZ()) {
            int stepX = Integer.signum(feet.getX() - cursor.getX());
            int stepZ = Integer.signum(feet.getZ() - cursor.getZ());
            if (stepX != 0) {
                cursor = cursor.offset(stepX, 0, 0);
            } else {
                cursor = cursor.offset(0, 0, stepZ);
            }
            if (!appendScaffoldCell(level, cursor, isPending, planned, place, null)) {
                return Optional.empty();
            }
        }

        if (!ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet)) && !planned.contains(pack(feet))) {
            if (!appendScaffoldCell(level, feet, isPending, planned, place, null)) {
                return Optional.empty();
            }
        }

        if (place.isEmpty() || isFlatWalkableOnly(level, feet, place, maidY)) {
            return Optional.empty();
        }

        return Optional.of(place);
    }

    /**
     * True when the plan is only a same-level carpet the maid could walk, not a climb.
     * Stands above the maid (wall top, next layer) are never treated as flat.
     */
    private static boolean isFlatWalkableOnly(ServerLevel level, BlockPos feet, List<BlockPos> place, int maidY) {
        if (maidY != Integer.MIN_VALUE && feet.getY() > maidY + 1) {
            return false;
        }
        for (BlockPos cell : place) {
            if (cell.getY() != feet.getY()) {
                return false;
            }
            BlockPos below = cell.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return false;
            }
        }
        return true;
    }

    private static boolean appendScaffoldCell(
            ServerLevel level,
            BlockPos cell,
            Predicate<BlockPos> isPending,
            Set<Long> planned,
            List<BlockPos> place,
            BlockPos maidFeet) {
        if (isPending.test(cell)) {
            return false;
        }
        long packed = pack(cell);
        if (planned.contains(packed) || ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(cell))) {
            planned.add(packed);
            return true;
        }
        if (!isReplaceableForScaffold(level, cell)) {
            return false;
        }
        if (!ScaffoldHeadroomPolicy.hasStandHeadroom(level, cell)) {
            return false;
        }
        boolean vanillaAttach = ScaffoldAttachmentPolicy.canPlaceAt(
                level, cell, plannedPositions(planned), maidFeet);
        if (!vanillaAttach) {
            return false;
        }
        if (maidFeet != null
                && ScaffoldAttachmentPolicy.isTowerSeedCell(cell, maidFeet)
                && !ScaffoldAttachmentPolicy.canPlaceAt(level, cell, plannedPositions(planned), null)) {
            // #region agent log
            JsonObject h11 = new JsonObject();
            h11.addProperty("cell", cell.toShortString());
            h11.addProperty("maidFeet", maidFeet.toShortString());
            AgentDebugLog.log("H11", "ScaffoldPlanner.appendScaffoldCell", "tower_seed_attach", h11);
            // #endregion
        } else if (maidFeet != null) {
            BlockPos below = cell.below();
            if (!ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(below))
                    && planned.contains(pack(below))
                    && !ScaffoldAttachmentPolicy.isTowerSeedCell(cell, maidFeet)) {
                // #region agent log
                JsonObject h12 = new JsonObject();
                h12.addProperty("cell", cell.toShortString());
                h12.addProperty("below", below.toShortString());
                if (maidFeet != null) {
                    h12.addProperty("maidFeet", maidFeet.toShortString());
                }
                AgentDebugLog.log("H12", "ScaffoldPlanner.appendScaffoldCell", "planned_below_attach", h12);
                // #endregion
            }
        }
        planned.add(packed);
        place.add(cell.immutable());
        return true;
    }

    private static BlockPos findColumnSupport(
            ServerLevel level, BlockPos feet, int maidY, Predicate<BlockPos> isPending) {
        BlockPos under = scanDownToSupport(level, feet, isPending);
        if (isUsableGroundSupport(under, maidY)) {
            return under;
        }
        for (int r = 1; r <= MAX_TOWER_SEARCH; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    if (Math.abs(dx) + Math.abs(dz) > MAX_HORIZONTAL) {
                        continue;
                    }
                    BlockPos tip = feet.offset(dx, 0, dz);
                    BlockPos base = scanDownToSupport(level, tip, isPending);
                    if (isUsableGroundSupport(base, maidY)) {
                        return base;
                    }
                }
            }
        }
        return null;
    }

    /** Tower base must be at/below the maid so she can climb from the ground, not from a wall top. */
    private static boolean isUsableGroundSupport(BlockPos support, int maidY) {
        if (support == null) {
            return false;
        }
        if (maidY == Integer.MIN_VALUE) {
            return true;
        }
        return support.getY() <= maidY;
    }

    private static BlockPos scanDownToSupport(ServerLevel level, BlockPos tip, Predicate<BlockPos> isPending) {
        BlockPos.MutableBlockPos cursor = tip.mutable();
        for (int i = 0; i < MAX_GROUND_DROP; i++) {
            if (isPending.test(cursor.immutable())) {
                return null;
            }
            BlockPos below = cursor.below();
            BlockState belowState = level.getBlockState(below);
            if (ConstructionScaffoldBlocks.isWalkableScaffold(belowState)) {
                cursor.move(Direction.DOWN);
                continue;
            }
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                return below.immutable();
            }
            BlockState here = level.getBlockState(cursor);
            if (!isReplaceableForScaffold(level, cursor) && !ConstructionScaffoldBlocks.isWalkableScaffold(here)) {
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean isReplaceableForScaffold(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || ConstructionScaffoldBlocks.isWalkableScaffold(state);
    }

    static boolean canStandAt(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState above = level.getBlockState(feet.above());
        boolean feetIsScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(feetState);
        boolean belowIsScaffold = ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(feet.below()));
        if (!ScaffoldWorkStandPolicy.allowsWorkStand(feetIsScaffold, belowIsScaffold)) {
            return false;
        }
        boolean feetOk = feetState.isPathfindable(level, feet, PathComputationType.LAND)
                || feetState.isAir()
                || feetState.canBeReplaced();
        boolean headOk = above.isPathfindable(level, feet.above(), PathComputationType.LAND)
                || above.isAir()
                || above.canBeReplaced();
        BlockState below = level.getBlockState(feet.below());
        boolean floorOk = below.isFaceSturdy(level, feet.below(), Direction.UP);
        return feetOk && headOk && floorOk;
    }

    private static boolean canInteractFrom(ServerLevel level, BlockPos feet, BlockPos target) {
        Vec3 eye = new Vec3(feet.getX() + 0.5D, feet.getY() + 1.62D, feet.getZ() + 0.5D);
        return BuildInteractionReach.canReach(
                eye.x, eye.y, eye.z,
                target.getX(), target.getY(), target.getZ(),
                BuildInteractionReach.REACH_DISTANCE)
                && BuildLineOfSight.hasClearView(level, eye, target);
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Iterable<BlockPos> plannedPositions(Set<Long> planned) {
        return () -> planned.stream().map(BlockPos::of).iterator();
    }

    /** Place one scaffolding with vanilla distance update. */
    public static boolean placeScaffoldBlock(ServerLevel level, BlockPos pos) {
        return placeScaffoldBlock(level, pos, null, null);
    }

    public static boolean placeScaffoldBlock(
            ServerLevel level,
            BlockPos pos,
            Iterable<BlockPos> alsoConnected,
            BlockPos maidFeet) {
        if (!ScaffoldAttachmentPolicy.canPlaceAt(level, pos, alsoConnected, maidFeet)) {
            return false;
        }
        BlockState state = ConstructionScaffoldBlocks.maidBlock().defaultBlockState();
        int distance = ScaffoldAttachmentPolicy.placementDistance(level, pos, alsoConnected, maidFeet);
        if (distance >= 7) {
            return false;
        }
        state = state.setValue(ScaffoldingBlock.DISTANCE, distance);
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        return ScaffoldAttachmentPolicy.wouldStayPlacedAfterPlacement(level, pos, alsoConnected, maidFeet);
    }
}
