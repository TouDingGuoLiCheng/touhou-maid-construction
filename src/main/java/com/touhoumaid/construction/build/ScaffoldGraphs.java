package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the scaffolding occupancy graph used by {@link ScaffoldPathfinder}. */
public final class ScaffoldGraphs {
    public static final int SCAN_XZ = 8;
    public static final int SCAN_Y_DOWN = 8;
    public static final int SCAN_Y_UP = 24;
    public static final int MAX_NODES = 4096;

    private ScaffoldGraphs() {
    }

    public static Set<Long> passable(
            ServerLevel level,
            BuildJob job,
            BlockPos maidFeet,
            List<BlockPos> plannedPath) {
        Set<Long> cells = new HashSet<>();
        if (maidFeet != null) {
            add(cells, maidFeet);
        }
        if (job != null) {
            for (long packed : job.scaffolds().packedPositions()) {
                add(cells, BlockPos.of(packed));
            }
            addRegistryColumns(cells, job.scaffoldColumnRegistry(), maidFeet);
        }
        if (plannedPath != null && level != null) {
            for (BlockPos pos : plannedPath) {
                if (ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(pos))) {
                    add(cells, pos);
                }
            }
        }
        if (level != null && maidFeet != null) {
            scanWorldScaffolds(level, maidFeet, cells);
        }
        return cells;
    }

    public static Set<Long> passableFromLedger(Set<Long> ledgerBlockPosPacked, BlockPos maidFeet) {
        Set<Long> cells = new HashSet<>();
        if (maidFeet != null) {
            add(cells, maidFeet);
        }
        if (ledgerBlockPosPacked != null) {
            for (long packed : ledgerBlockPosPacked) {
                add(cells, BlockPos.of(packed));
            }
        }
        return cells;
    }

    public static boolean canReach(BlockPos from, BlockPos to, Set<Long> passable) {
        if (from == null || to == null || passable == null) {
            return false;
        }
        long dest = ScaffoldLayout.packCell(to.getX(), to.getY(), to.getZ());
        if (!passable.contains(dest)) {
            return false;
        }
        return ScaffoldPathfinder.canReach(cell(from), cell(to), passable, MAX_NODES);
    }

    public static List<ScaffoldLayout.Cell> findRoute(BlockPos from, BlockPos to, Set<Long> passable) {
        return findRoute(from, to, passable, null);
    }

    public static List<ScaffoldLayout.Cell> findRoute(
            BlockPos from, BlockPos to, Set<Long> passable, ScaffoldColumnRegistry registry) {
        if (from == null || to == null || passable == null) {
            return List.of();
        }
        BlockPos dest = resolveRouteCell(to, passable, registry);
        if (passable.contains(ScaffoldLayout.packCell(dest.getX(), dest.getY(), dest.getZ()))) {
            List<ScaffoldLayout.Cell> direct =
                    ScaffoldPathfinder.findPath(cell(from), cell(dest), passable, MAX_NODES);
            if (!direct.isEmpty()) {
                return direct;
            }
        }
        BlockPos nearest = nearestPassableToward(from, dest, passable, registry);
        if (nearest == null || nearest.equals(from)) {
            return List.of();
        }
        return ScaffoldPathfinder.findPath(cell(from), cell(nearest), passable, MAX_NODES);
    }

    /** One scaffold step toward {@code to}, or null when no progress is possible. */
    public static BlockPos nextStepToward(BlockPos from, BlockPos to, Set<Long> passable) {
        return nextStepToward(from, to, passable, null);
    }

    public static BlockPos nextStepToward(
            BlockPos from, BlockPos to, Set<Long> passable, ScaffoldColumnRegistry registry) {
        if (from == null || to == null || passable == null || passable.isEmpty()) {
            return null;
        }
        List<ScaffoldLayout.Cell> route = findRoute(from, to, passable, registry);
        if (route.size() >= 2) {
            return toPos(route.get(1));
        }
        BlockPos dest = resolveRouteCell(to, passable, registry);
        BlockPos nearest = nearestPassableToward(from, dest, passable, registry);
        if (nearest != null && !nearest.equals(from)) {
            List<ScaffoldLayout.Cell> partial =
                    ScaffoldPathfinder.findPath(cell(from), cell(nearest), passable, MAX_NODES);
            if (partial.size() >= 2) {
                return toPos(partial.get(1));
            }
        }
        return greedyStepToward(from, dest, passable);
    }

    /**
     * Horizontal scaffold steps deferred — vertical-only phase.
     */
    public static BlockPos horizontalStepToward(BlockPos from, BlockPos to, Set<Long> passable) {
        ScaffoldLayout.Cell step = ScaffoldClimbPolicy.horizontalStepToward(cell(from), cell(to), passable);
        return step == null ? null : toPos(step);
    }

    public static BlockPos greedyStepToward(BlockPos from, BlockPos to, Set<Long> passable) {
        ScaffoldLayout.Cell step = ScaffoldClimbPolicy.greedyStepToward(cell(from), cell(to), passable);
        return step == null ? null : toPos(step);
    }

    static BlockPos resolveRouteCell(BlockPos target, Set<Long> passable) {
        return resolveRouteCell(target, passable, null);
    }

    static BlockPos resolveRouteCell(BlockPos target, Set<Long> passable, ScaffoldColumnRegistry registry) {
        if (passable.contains(ScaffoldLayout.packCell(target.getX(), target.getY(), target.getZ()))) {
            return target;
        }
        BlockPos below = target.below();
        if (passable.contains(ScaffoldLayout.packCell(below.getX(), below.getY(), below.getZ()))) {
            return below;
        }
        BlockPos above = target.above();
        if (passable.contains(ScaffoldLayout.packCell(above.getX(), above.getY(), above.getZ()))) {
            return above;
        }
        if (registry != null) {
            BlockPos onColumn = resolveOnRegistryColumn(target, registry, passable);
            if (onColumn != null) {
                return onColumn;
            }
        }
        return target;
    }

    /** Pick the highest passable stand on a job column that still reaches toward {@code target}. */
    static BlockPos resolveOnRegistryColumn(
            BlockPos target, ScaffoldColumnRegistry registry, Set<Long> passable) {
        return registry.at(target.getX(), target.getZ())
                .map(column -> {
                    if (column.maxStandY() < target.getY()) {
                        return null;
                    }
                    int wantY = Math.min(target.getY(), column.maxStandY());
                    for (int y = wantY; y <= column.maxStandY(); y++) {
                        BlockPos stand = new BlockPos(column.x(), y, column.z());
                        if (passable.contains(ScaffoldLayout.packCell(stand.getX(), stand.getY(), stand.getZ()))) {
                            return stand;
                        }
                        BlockPos inside = stand.below();
                        if (passable.contains(ScaffoldLayout.packCell(inside.getX(), inside.getY(), inside.getZ()))) {
                            return inside;
                        }
                    }
                    for (int y = wantY - 1; y >= column.baseY() + 1; y--) {
                        BlockPos stand = new BlockPos(column.x(), y, column.z());
                        if (passable.contains(ScaffoldLayout.packCell(stand.getX(), stand.getY(), stand.getZ()))) {
                            return stand;
                        }
                        BlockPos inside = stand.below();
                        if (passable.contains(ScaffoldLayout.packCell(inside.getX(), inside.getY(), inside.getZ()))) {
                            return inside;
                        }
                    }
                    return null;
                })
                .orElse(null);
    }

    /** Closest passable scaffold cell to {@code target} (used when the stand cell is land, not scaffolding). */
    public static BlockPos nearestPassableToward(BlockPos from, BlockPos target, Set<Long> passable) {
        return nearestPassableToward(from, target, passable, null);
    }

    public static BlockPos nearestPassableToward(
            BlockPos from, BlockPos target, Set<Long> passable, ScaffoldColumnRegistry registry) {
        if (from == null || target == null || passable == null || passable.isEmpty()) {
            return null;
        }
        if (registry != null && from.getX() == target.getX() && from.getZ() == target.getZ()) {
            BlockPos onColumn = resolveOnRegistryColumn(target, registry, passable);
            if (onColumn != null && !onColumn.equals(from)) {
                return onColumn;
            }
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (long packed : passable) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.equals(from)) {
                continue;
            }
            double score = pos.distSqr(target) + from.distSqr(pos) * 0.01D;
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    public static ScaffoldLayout.Cell cell(BlockPos pos) {
        return new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toPos(ScaffoldLayout.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static void scanWorldScaffolds(ServerLevel level, BlockPos maidFeet, Set<Long> cells) {
        addWorldScaffolds(level, maidFeet, cells);
    }

    /** Include already-placed world scaffolds in a planning passable set (ledger may be empty). */
    public static void addWorldScaffolds(ServerLevel level, BlockPos maidFeet, Set<Long> cells) {
        if (level == null || maidFeet == null || cells == null) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x0 = maidFeet.getX();
        int y0 = maidFeet.getY();
        int z0 = maidFeet.getZ();
        for (int dx = -SCAN_XZ; dx <= SCAN_XZ; dx++) {
            for (int dy = -SCAN_Y_DOWN; dy <= SCAN_Y_UP; dy++) {
                for (int dz = -SCAN_XZ; dz <= SCAN_XZ; dz++) {
                    cursor.set(x0 + dx, y0 + dy, z0 + dz);
                    if (ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(cursor))) {
                        add(cells, cursor);
                    }
                }
            }
        }
    }

    private static void add(Set<Long> cells, BlockPos pos) {
        addPacked(cells, pos.getX(), pos.getY(), pos.getZ());
    }

    private static void addPacked(Set<Long> cells, int x, int y, int z) {
        cells.add(ScaffoldLayout.packCell(x, y, z));
        cells.add(ScaffoldLayout.packCell(x, y + 1, z));
    }

    /** Fill vertical job columns into the passable graph using landing/max-stand metadata. */
    static void addRegistryColumns(
            Set<Long> cells, ScaffoldColumnRegistry registry, BlockPos near) {
        if (registry == null || near == null) {
            return;
        }
        addRegistryColumnsNear(cells, registry, near.getX(), near.getZ());
    }

    static void addRegistryColumnsNear(
            Set<Long> cells, ScaffoldColumnRegistry registry, int nearX, int nearZ) {
        if (registry == null) {
            return;
        }
        for (ScaffoldColumnRegistry.Column column : registry.columns()) {
            if (Math.abs(column.x() - nearX) > SCAN_XZ || Math.abs(column.z() - nearZ) > SCAN_XZ) {
                continue;
            }
            for (int y = column.baseY() + 1; y <= column.maxBlockY(); y++) {
                addPacked(cells, column.x(), y, column.z());
            }
        }
    }

    /** JVM unit-test entry without BlockPos in test sources. */
    static List<ScaffoldLayout.Cell> findRouteCells(
            int fromX,
            int fromY,
            int fromZ,
            int toX,
            int toY,
            int toZ,
            Set<Long> passable,
            ScaffoldColumnRegistry registry) {
        return findRoute(
                new BlockPos(fromX, fromY, fromZ),
                new BlockPos(toX, toY, toZ),
                passable,
                registry);
    }

    /** JVM unit-test entry without BlockPos in test sources. */
    static int resolveRouteCellY(int x, int y, int z, Set<Long> passable, ScaffoldColumnRegistry registry) {
        return resolveRouteCell(new BlockPos(x, y, z), passable, registry).getY();
    }

    /** Ledger + nearby world scaffolding only — no registry ghost column fill for reuse reachability. */
    public static Set<Long> passableForReuseCheck(
            ServerLevel level, BuildJob job, BlockPos maidFeet) {
        Set<Long> cells = new HashSet<>();
        if (maidFeet != null) {
            add(cells, maidFeet);
        }
        if (job != null) {
            for (long packed : job.scaffolds().packedPositions()) {
                add(cells, BlockPos.of(packed));
            }
        }
        if (level != null && maidFeet != null) {
            scanWorldScaffolds(level, maidFeet, cells);
        }
        return cells;
    }
}
