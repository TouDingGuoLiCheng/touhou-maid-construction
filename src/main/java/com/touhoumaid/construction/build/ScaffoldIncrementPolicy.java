package com.touhoumaid.construction.build;

import java.util.List;
import java.util.Set;

/**
 * One increment at a time: the maid must stand on the previous cell (or beside the base)
 * before the next scaffolding is placed.
 */
public final class ScaffoldIncrementPolicy {
    public enum Kind {
        PLACE,
        APPROACH,
        CLIMB,
        DONE,
        FAILED
    }

    public record Next(Kind kind, ScaffoldLayout.Cell cell, ScaffoldLayout.Cell stand) {
        public static Next failed() {
            return new Next(Kind.FAILED, null, null);
        }
    }

    private ScaffoldIncrementPolicy() {
    }

    public static Next next(List<ScaffoldLayout.Cell> path, Set<Long> presentCells, ScaffoldLayout.Cell maidFeet) {
        return next(path, presentCells, maidFeet, null);
    }

    /**
     * @param routeWorkStand solid construction stand from {@link ScaffoldPlanner}; never scaffolded
     */
    public static Next next(
            List<ScaffoldLayout.Cell> path,
            Set<Long> presentCells,
            ScaffoldLayout.Cell maidFeet,
            ScaffoldLayout.Cell routeWorkStand) {
        if (path == null || path.isEmpty()) {
            return Next.failed();
        }
        ScaffoldLayout.Cell missing = null;
        ScaffoldLayout.Cell previous = null;
        for (ScaffoldLayout.Cell cell : path) {
            if (routeWorkStand != null && cell.equals(routeWorkStand)) {
                break;
            }
            if (!presentCells.contains(ScaffoldLayout.packCell(cell.x(), cell.y(), cell.z()))) {
                missing = cell;
                break;
            }
            previous = cell;
        }
        ScaffoldLayout.Cell workFeet = routeWorkStand != null
                ? routeWorkStand
                : path.get(path.size() - 1);
        if (missing == null) {
            if (maidFeet.equals(workFeet)) {
                return new Next(Kind.DONE, workFeet, workFeet);
            }
            if (path.indexOf(maidFeet) < 0) {
                ScaffoldLayout.Cell nearest = nearestPathCell(path, maidFeet);
                // If the maid is strictly above the nearest path cell (e.g. standing on column
                // top while the scaffold arm was built from below), go straight to the final
                // workFeet rather than asking her to step DOWN back into the path.
                if (maidFeet.y() > nearest.y()) {
                    return new Next(Kind.CLIMB, workFeet, workFeet);
                }
                return new Next(Kind.APPROACH, nearest, nearest);
            }
            return new Next(Kind.CLIMB, workFeet, workFeet);
        }
        ScaffoldLayout.Cell stand = previous != null ? previous : missing;
        if (canPlaceFrom(maidFeet, missing, previous)) {
            return new Next(Kind.PLACE, missing, stand);
        }
        if (shouldClimbToPresentStand(maidFeet, stand, presentCells)) {
            return new Next(Kind.CLIMB, stand, stand);
        }
        return new Next(Kind.APPROACH, missing, stand);
    }

    static boolean shouldClimbToPresentStand(
            ScaffoldLayout.Cell maidFeet, ScaffoldLayout.Cell stand, Set<Long> presentCells) {
        if (stand == null || presentCells == null) {
            return false;
        }
        if (stand.x() != maidFeet.x() || stand.z() != maidFeet.z() || stand.y() <= maidFeet.y()) {
            return false;
        }
        return presentCells.contains(ScaffoldLayout.packCell(stand.x(), stand.y(), stand.z()));
    }

    /**
     * APPROACH walk destination is the footing {@code stand}, never the unfinished final work
     * stand. Mid-climb retention of a higher workFeet is handled by ConstructionWalkPolicy.
     */
    public static ScaffoldLayout.Cell approachWalkCell(Next next, ScaffoldLayout.Cell finalWorkFeet) {
        if (next == null) {
            return finalWorkFeet;
        }
        return next.stand() != null ? next.stand() : finalWorkFeet;
    }

    static boolean canPlaceFrom(
            ScaffoldLayout.Cell maidFeet,
            ScaffoldLayout.Cell cell,
            ScaffoldLayout.Cell previous) {
        int dx = Math.abs(cell.x() - maidFeet.x());
        int dz = Math.abs(cell.z() - maidFeet.z());
        int dy = cell.y() - maidFeet.y();
        if (Math.max(dx, dz) > 1 || dy < 0 || dy > 2) {
            return false;
        }
        if (previous == null) {
            return dy <= 1;
        }
        if (maidFeet.equals(previous)) {
            return true;
        }
        // Standing inside the missing cell (partial tower / stacked maids): place it in place.
        if (maidFeet.equals(cell)) {
            int pdx = Math.abs(previous.x() - maidFeet.x());
            int pdz = Math.abs(previous.z() - maidFeet.z());
            int pdy = maidFeet.y() - previous.y();
            return Math.max(pdx, pdz) <= 1 && pdy >= 0 && pdy <= 1;
        }
        int pdx = Math.abs(previous.x() - maidFeet.x());
        int pdz = Math.abs(previous.z() - maidFeet.z());
        boolean besidePreviousAtSameLevel = Math.max(pdx, pdz) <= 1 && maidFeet.y() == previous.y();
        return besidePreviousAtSameLevel && dy <= 1;
    }

    private static ScaffoldLayout.Cell nearestPathCell(List<ScaffoldLayout.Cell> path, ScaffoldLayout.Cell maidFeet) {
        ScaffoldLayout.Cell best = path.get(0);
        int bestDist = manhattan(best, maidFeet);
        for (ScaffoldLayout.Cell cell : path) {
            int dist = manhattan(cell, maidFeet);
            if (dist < bestDist) {
                bestDist = dist;
                best = cell;
            }
        }
        return best;
    }

    private static int manhattan(ScaffoldLayout.Cell a, ScaffoldLayout.Cell b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
}
