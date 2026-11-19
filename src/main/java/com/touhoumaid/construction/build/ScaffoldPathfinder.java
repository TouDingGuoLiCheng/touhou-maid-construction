package com.touhoumaid.construction.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Grid BFS over scaffolding (and other passable cells). Vanilla land pathfinding cannot climb
 * scaffolding, so construction uses this graph as the maid's scaffold route.
 */
public final class ScaffoldPathfinder {
    public static final int MAX_NODES = 4096;

    private static final int[][] DIRS = {
            {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private ScaffoldPathfinder() {
    }

    public static boolean canReach(
            ScaffoldLayout.Cell from, ScaffoldLayout.Cell to, Set<Long> passable, int maxNodes) {
        return !findPath(from, to, passable, maxNodes).isEmpty();
    }

    public static List<ScaffoldLayout.Cell> findPath(
            ScaffoldLayout.Cell from, ScaffoldLayout.Cell to, Set<Long> passable, int maxNodes) {
        if (from == null || to == null || passable == null || passable.isEmpty()) {
            return List.of();
        }
        if (!passable.contains(key(from)) || !passable.contains(key(to))) {
            return List.of();
        }
        if (from.equals(to)) {
            return List.of(from);
        }
        Queue<ScaffoldLayout.Cell> queue = new ArrayDeque<>();
        Map<ScaffoldLayout.Cell, ScaffoldLayout.Cell> parent = new HashMap<>();
        queue.add(from);
        parent.put(from, from);
        int visited = 0;
        while (!queue.isEmpty() && visited < maxNodes) {
            ScaffoldLayout.Cell cur = queue.poll();
            visited++;
            for (int[] d : DIRS) {
                ScaffoldLayout.Cell next = new ScaffoldLayout.Cell(cur.x() + d[0], cur.y() + d[1], cur.z() + d[2]);
                if (!passable.contains(key(next)) || parent.containsKey(next)) {
                    continue;
                }
                parent.put(next, cur);
                if (next.equals(to)) {
                    return unwind(parent, from, to);
                }
                queue.add(next);
            }
        }
        return List.of();
    }

    public static long key(ScaffoldLayout.Cell cell) {
        return ScaffoldLayout.packCell(cell.x(), cell.y(), cell.z());
    }

    private static List<ScaffoldLayout.Cell> unwind(
            Map<ScaffoldLayout.Cell, ScaffoldLayout.Cell> parent,
            ScaffoldLayout.Cell from,
            ScaffoldLayout.Cell to) {
        List<ScaffoldLayout.Cell> path = new ArrayList<>();
        ScaffoldLayout.Cell cursor = to;
        while (cursor != null) {
            path.add(cursor);
            if (cursor.equals(from)) {
                break;
            }
            ScaffoldLayout.Cell prev = parent.get(cursor);
            if (prev == null || prev.equals(cursor)) {
                break;
            }
            cursor = prev;
        }
        Collections.reverse(path);
        return path;
    }
}
