package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Key nodes along a locked scaffold route for vanilla navigation. */
public final class ScaffoldWaypointPolicy {
    private ScaffoldWaypointPolicy() {
    }

    /**
     * Extract waypoints: route start, each column top (in path order), then final work feet.
     */
    public static List<BlockPos> extract(List<BlockPos> path, BlockPos workFeet) {
        if (path == null || path.isEmpty()) {
            return workFeet != null ? List.of(workFeet) : List.of();
        }
        List<ScaffoldLayout.Cell> cells = new ArrayList<>(path.size());
        for (BlockPos pos : path) {
            cells.add(new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ()));
        }
        ScaffoldLayout.Cell end = workFeet != null
                ? new ScaffoldLayout.Cell(workFeet.getX(), workFeet.getY(), workFeet.getZ())
                : cells.get(cells.size() - 1);
        return extractCells(cells, end).stream().map(ScaffoldWaypointPolicy::toPos).toList();
    }

    static List<ScaffoldLayout.Cell> extractCells(
            List<ScaffoldLayout.Cell> path, ScaffoldLayout.Cell workFeet) {
        if (path == null || path.isEmpty()) {
            return workFeet != null ? List.of(workFeet) : List.of();
        }
        List<ScaffoldLayout.Cell> waypoints = new ArrayList<>();
        appendIfDistinct(waypoints, path.get(0));

        Map<Long, ScaffoldLayout.Cell> columnTop = new HashMap<>();
        for (ScaffoldLayout.Cell pos : path) {
            long xz = ScaffoldLayout.packXz(pos.x(), pos.z());
            ScaffoldLayout.Cell prev = columnTop.get(xz);
            if (prev == null || pos.y() > prev.y()) {
                columnTop.put(xz, pos);
            }
        }
        Set<Long> emitted = new HashSet<>();
        for (ScaffoldLayout.Cell pos : path) {
            long xz = ScaffoldLayout.packXz(pos.x(), pos.z());
            ScaffoldLayout.Cell top = columnTop.get(xz);
            if (!emitted.contains(xz) && pos.equals(top)) {
                appendIfDistinct(waypoints, pos);
                emitted.add(xz);
            }
        }

        ScaffoldLayout.Cell end = workFeet != null ? workFeet : path.get(path.size() - 1);
        appendIfDistinct(waypoints, end);
        return List.copyOf(waypoints);
    }

    /** First waypoint index the maid has not clearly passed yet. */
    public static int initialIndex(List<BlockPos> waypoints, BlockPos feet) {
        if (waypoints == null || waypoints.isEmpty() || feet == null) {
            return 0;
        }
        List<ScaffoldLayout.Cell> cells = new ArrayList<>(waypoints.size());
        for (BlockPos pos : waypoints) {
            cells.add(new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ()));
        }
        return initialIndexCells(cells, new ScaffoldLayout.Cell(feet.getX(), feet.getY(), feet.getZ()));
    }

    static int initialIndexCells(List<ScaffoldLayout.Cell> waypoints, ScaffoldLayout.Cell feet) {
        if (waypoints == null || waypoints.isEmpty() || feet == null) {
            return 0;
        }
        for (int i = 0; i < waypoints.size(); i++) {
            if (!hasPassedCells(feet, waypoints.get(i))) {
                return i;
            }
        }
        return waypoints.size() - 1;
    }

    public static BlockPos currentTarget(List<BlockPos> waypoints, int index, BlockPos fallback) {
        if (waypoints == null || waypoints.isEmpty()) {
            return fallback;
        }
        if (index < 0) {
            return waypoints.get(0);
        }
        if (index >= waypoints.size()) {
            return fallback != null ? fallback : waypoints.get(waypoints.size() - 1);
        }
        return waypoints.get(index);
    }

    public static boolean hasArrived(BlockPos feet, BlockPos waypoint) {
        if (feet == null || waypoint == null) {
            return false;
        }
        return hasArrivedCells(
                new ScaffoldLayout.Cell(feet.getX(), feet.getY(), feet.getZ()),
                new ScaffoldLayout.Cell(waypoint.getX(), waypoint.getY(), waypoint.getZ()));
    }

    static boolean hasArrivedCells(ScaffoldLayout.Cell feet, ScaffoldLayout.Cell waypoint) {
        if (feet == null || waypoint == null) {
            return false;
        }
        if (feet.x() != waypoint.x() || feet.z() != waypoint.z()) {
            return false;
        }
        return feet.y() >= waypoint.y() - 1 && feet.y() <= waypoint.y() + 1;
    }

    static boolean hasPassedCells(ScaffoldLayout.Cell feet, ScaffoldLayout.Cell waypoint) {
        if (feet == null || waypoint == null) {
            return false;
        }
        return feet.x() == waypoint.x() && feet.z() == waypoint.z() && feet.y() >= waypoint.y();
    }

    private static void appendIfDistinct(List<ScaffoldLayout.Cell> waypoints, ScaffoldLayout.Cell pos) {
        if (pos == null) {
            return;
        }
        if (waypoints.isEmpty() || !waypoints.get(waypoints.size() - 1).equals(pos)) {
            waypoints.add(pos);
        }
    }

    private static BlockPos toPos(ScaffoldLayout.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
