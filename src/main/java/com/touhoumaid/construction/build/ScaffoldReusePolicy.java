package com.touhoumaid.construction.build;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Before raising a new tower, try walking via already placed scaffolding.
 * Column reuse is gated by an interact-capable stand, not mere height.
 */
public final class ScaffoldReusePolicy {
    /** Reuse shortcuts only when the stand is at most one scaffold step above/below the maid. */
    public static final int MAX_REUSE_VERTICAL_GAP = 1;

    public record ReuseStand(ScaffoldColumnRegistry.Column column, ScaffoldLayout.Cell stand) {
    }

    private ScaffoldReusePolicy() {
    }

    /**
     * Pick an existing job column that offers a stand from which {@code target} can be interacted
     * with, and that the maid can reach on the scaffold graph. Own-column short towers return empty
     * so the planner extends instead of borrowing a distant tower.
     */
    public static Optional<ReuseStand> pickColumnForVerticalReuse(
            ScaffoldLayout.Cell maidFeet,
            ScaffoldLayout.Cell target,
            ScaffoldColumnRegistry registry,
            Set<Long> scaffoldPassable) {
        if (!ScaffoldWorkStandPolicy.allowsScaffoldColumnWorkStand()) {
            return Optional.empty();
        }
        if (maidFeet == null || target == null || registry == null) {
            return Optional.empty();
        }
        double eye = ScaffoldWorkStandPolicy.DEFAULT_EYE_HEIGHT;
        Optional<ScaffoldColumnRegistry.Column> own = registry.at(maidFeet.x(), maidFeet.z());
        if (own.isPresent()) {
            Optional<ScaffoldLayout.Cell> stand =
                    ScaffoldWorkStandPolicy.pickInteractStandOnColumn(own.get(), target, eye);
            if (stand.isEmpty()) {
                return Optional.empty();
            }
            if (canReachColumnStand(maidFeet, stand.get(), scaffoldPassable)) {
                return Optional.of(new ReuseStand(own.get(), stand.get()));
            }
            return Optional.empty();
        }
        ScaffoldColumnRegistry.Column bestColumn = null;
        ScaffoldLayout.Cell bestStand = null;
        long bestScore = Long.MAX_VALUE;
        for (ScaffoldColumnRegistry.Column column : registry.columns()) {
            Optional<ScaffoldLayout.Cell> stand =
                    ScaffoldWorkStandPolicy.pickInteractStandOnColumn(column, target, eye);
            if (stand.isEmpty()) {
                continue;
            }
            if (!canReachColumnStand(maidFeet, stand.get(), scaffoldPassable)) {
                continue;
            }
            long score = weightedColumnScore(column, maidFeet, stand.get());
            if (score < bestScore) {
                bestScore = score;
                bestColumn = column;
                bestStand = stand.get();
            }
        }
        if (bestColumn == null || bestStand == null) {
            return Optional.empty();
        }
        return Optional.of(new ReuseStand(bestColumn, bestStand));
    }

    private static boolean canReachColumnStand(
            ScaffoldLayout.Cell maidFeet, ScaffoldLayout.Cell stand, Set<Long> scaffoldPassable) {
        if (scaffoldPassable == null || scaffoldPassable.isEmpty()) {
            return false;
        }
        if (!withinReuseVerticalGap(maidFeet, stand)) {
            return false;
        }
        ScaffoldLayout.Cell standCell = stand;
        if (!scaffoldPassable.contains(ScaffoldPathfinder.key(standCell))) {
            ScaffoldLayout.Cell below = new ScaffoldLayout.Cell(stand.x(), stand.y() - 1, stand.z());
            if (!scaffoldPassable.contains(ScaffoldPathfinder.key(below))) {
                return false;
            }
            standCell = below;
        }
        return ScaffoldPathfinder.canReach(
                maidFeet, standCell, scaffoldPassable, ScaffoldPathfinder.MAX_NODES);
    }

    private static long weightedColumnScore(
            ScaffoldColumnRegistry.Column column, ScaffoldLayout.Cell maidFeet, ScaffoldLayout.Cell stand) {
        int toMaid = Math.abs(column.x() - maidFeet.x()) + Math.abs(column.z() - maidFeet.z());
        int dy = Math.abs(stand.y() - maidFeet.y());
        return (long) toMaid * 256L + dy;
    }

    /**
     * Pick a work stand that is reachable from {@code maidFeet} via the existing scaffold graph.
     * Lower score is better: closer to target first, then closer to the maid.
     */
    public static Optional<ScaffoldLayout.Cell> pickReachableStandViaExistingScaffold(
            ScaffoldLayout.Cell maidFeet,
            ScaffoldLayout.Cell target,
            List<ScaffoldLayout.Cell> candidates,
            Set<Long> scaffoldPassable) {
        if (maidFeet == null || target == null || candidates == null || scaffoldPassable == null) {
            return Optional.empty();
        }
        ScaffoldLayout.Cell best = null;
        long bestScore = Long.MAX_VALUE;
        for (ScaffoldLayout.Cell stand : candidates) {
            if (stand == null) {
                continue;
            }
            if (!withinReuseVerticalGap(maidFeet, stand)) {
                continue;
            }
            if (!ScaffoldPathfinder.canReach(maidFeet, stand, scaffoldPassable, ScaffoldPathfinder.MAX_NODES)) {
                continue;
            }
            long score = weightedScore(stand, target, maidFeet);
            if (score < bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        return Optional.ofNullable(best);
    }

    private static long weightedScore(
            ScaffoldLayout.Cell stand, ScaffoldLayout.Cell target, ScaffoldLayout.Cell maidFeet) {
        long toTarget = manhattan(stand, target);
        long toMaid = manhattan(stand, maidFeet);
        return toTarget * 256L + toMaid;
    }

    private static int manhattan(ScaffoldLayout.Cell a, ScaffoldLayout.Cell b) {
        return Math.abs(a.x() - b.x())
                + Math.abs(a.y() - b.y())
                + Math.abs(a.z() - b.z());
    }

    static boolean withinReuseVerticalGap(ScaffoldLayout.Cell maidFeet, ScaffoldLayout.Cell stand) {
        if (maidFeet == null || stand == null) {
            return false;
        }
        return Math.abs(maidFeet.y() - stand.y()) <= MAX_REUSE_VERTICAL_GAP;
    }
}
