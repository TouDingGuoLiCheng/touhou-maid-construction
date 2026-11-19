package com.touhoumaid.construction.build;

import java.util.List;
import java.util.Set;

/** Greedy next cell along a sparse scaffold path. */
public final class ScaffoldClimbPolicy {
    private ScaffoldClimbPolicy() {
    }

    public static ScaffoldLayout.Cell nextStep(ScaffoldLayout.Cell maidFeet, List<ScaffoldLayout.Cell> path) {
        if (path == null || path.isEmpty()) {
            return maidFeet;
        }
        int idx = -1;
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).equals(maidFeet)) {
                idx = i;
                break;
            }
        }
        if (idx == path.size() - 1) {
            return maidFeet;
        }
        if (idx >= 0) {
            return path.get(idx + 1);
        }
        ScaffoldLayout.Cell best = path.get(0);
        int bestD = manhattan(maidFeet, best);
        for (ScaffoldLayout.Cell cell : path) {
            int d = manhattan(maidFeet, cell);
            if (d < bestD) {
                bestD = d;
                best = cell;
            }
        }
        return best;
    }

    /**
     * When BFS has no connected path (isolated tower / incomplete arm), still take a local step.
     * Align XZ toward the work first whenever a same-layer neighbor helps — do not shoot up the
     * first scaffold column the maid merely touches while the real stand is still far sideways.
     */
    public static ScaffoldLayout.Cell greedyStepToward(
            ScaffoldLayout.Cell from, ScaffoldLayout.Cell to, Set<Long> passable) {
        if (from == null || to == null || passable == null || passable.isEmpty()) {
            return null;
        }
        int dy = to.y() - from.y();
        /*
        int dxz = Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z());
        // Horizontal arm / walkway deferred — vertical-only phase.
        if (dxz > 0) {
            ScaffoldLayout.Cell horiz = bestHorizontalStep(from, to, passable);
            if (horiz != null) {
                return horiz;
            }
        }
        */
        if (dy > 0) {
            ScaffoldLayout.Cell up = new ScaffoldLayout.Cell(from.x(), from.y() + 1, from.z());
            if (passable.contains(ScaffoldPathfinder.key(up))) {
                return up;
            }
        }
        if (dy < 0) {
            ScaffoldLayout.Cell down = new ScaffoldLayout.Cell(from.x(), from.y() - 1, from.z());
            if (passable.contains(ScaffoldPathfinder.key(down))) {
                return down;
            }
        }
        // return bestHorizontalStep(from, to, passable);
        return null;
    }

    /** Horizontal scaffold steps along a walkway toward sideways work. */
    public static ScaffoldLayout.Cell horizontalStepToward(
            ScaffoldLayout.Cell from, ScaffoldLayout.Cell to, Set<Long> passable) {
        return bestHorizontalStep(from, to, passable);
    }

    private static ScaffoldLayout.Cell bestHorizontalStep(
            ScaffoldLayout.Cell from, ScaffoldLayout.Cell to, Set<Long> passable) {
        ScaffoldLayout.Cell best = null;
        int bestScore = Integer.MAX_VALUE;
        int[][] horiz = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : horiz) {
            ScaffoldLayout.Cell next = new ScaffoldLayout.Cell(from.x() + d[0], from.y(), from.z() + d[1]);
            if (!passable.contains(ScaffoldPathfinder.key(next))) {
                continue;
            }
            int score = manhattan(next, to);
            if (score < bestScore) {
                bestScore = score;
                best = next;
            }
        }
        if (best != null && manhattan(best, to) < manhattan(from, to)) {
            return best;
        }
        return null;
    }

    private static int manhattan(ScaffoldLayout.Cell a, ScaffoldLayout.Cell b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
}
