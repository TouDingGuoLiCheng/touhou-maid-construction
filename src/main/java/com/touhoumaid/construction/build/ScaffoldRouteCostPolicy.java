package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pick the cheapest scaffold approach at planning time (fewest new blocks / columns). */
public final class ScaffoldRouteCostPolicy {
    private static final long PER_BLOCK = 1_024L;
    private static final long PER_NEW_COLUMN = 256L;
    private static final long PER_PATH_CELL = 1L;
    /** Prefer towers planted against the building — Chebyshev rings from the target. */
    private static final long PER_COLUMN_RING_FROM_TARGET = 4_096L;

    private ScaffoldRouteCostPolicy() {
    }

    public static long cost(List<BlockPos> toPlace, List<BlockPos> path, Set<Long> existingColumnXz) {
        return cost(toPlace, path, existingColumnXz, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static long cost(
            List<BlockPos> toPlace,
            List<BlockPos> path,
            Set<Long> existingColumnXz,
            int targetX,
            int targetZ) {
        return costCells(toCells(toPlace), toCells(path), existingColumnXz, targetX, targetZ);
    }

    static long costCells(
            List<ScaffoldLayout.Cell> toPlace,
            List<ScaffoldLayout.Cell> path,
            Set<Long> existingColumnXz) {
        return costCells(toPlace, path, existingColumnXz, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    static long costCells(
            List<ScaffoldLayout.Cell> toPlace,
            List<ScaffoldLayout.Cell> path,
            Set<Long> existingColumnXz,
            int targetX,
            int targetZ) {
        int blocks = toPlace == null ? 0 : toPlace.size();
        int pathLen = path == null ? 0 : path.size();
        int newColumns = countNewColumnsCells(toPlace, existingColumnXz);
        long cost = (long) blocks * PER_BLOCK + (long) newColumns * PER_NEW_COLUMN + pathLen * PER_PATH_CELL;
        if (targetX != Integer.MIN_VALUE && targetZ != Integer.MIN_VALUE) {
            cost += columnRingPenalty(toPlace, existingColumnXz, targetX, targetZ);
        }
        return cost;
    }

    private static long columnRingPenalty(
            List<ScaffoldLayout.Cell> toPlace, Set<Long> existingColumnXz, int targetX, int targetZ) {
        if (toPlace == null || toPlace.isEmpty()) {
            return 0L;
        }
        Set<Long> seen = new HashSet<>();
        long penalty = 0L;
        for (ScaffoldLayout.Cell pos : toPlace) {
            long xz = ScaffoldLayout.packXz(pos.x(), pos.z());
            if (!seen.add(xz)) {
                continue;
            }
            if (existingColumnXz != null && existingColumnXz.contains(xz)) {
                continue;
            }
            int ring = Math.max(Math.abs(pos.x() - targetX), Math.abs(pos.z() - targetZ));
            penalty += (long) ring * PER_COLUMN_RING_FROM_TARGET;
        }
        return penalty;
    }

    public static Set<Long> columnXzFromLedger(Set<Long> packedPositions) {
        Set<Long> xz = new HashSet<>();
        if (packedPositions == null) {
            return xz;
        }
        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            xz.add(ScaffoldLayout.packXz(pos.getX(), pos.getZ()));
        }
        return xz;
    }

    private static int countNewColumnsCells(
            List<ScaffoldLayout.Cell> toPlace, Set<Long> existingColumnXz) {
        if (toPlace == null || toPlace.isEmpty()) {
            return 0;
        }
        Set<Long> seen = new HashSet<>();
        int newColumns = 0;
        for (ScaffoldLayout.Cell pos : toPlace) {
            long xz = ScaffoldLayout.packXz(pos.x(), pos.z());
            if (!seen.add(xz)) {
                continue;
            }
            if (existingColumnXz == null || !existingColumnXz.contains(xz)) {
                newColumns++;
            }
        }
        return newColumns;
    }

    private static List<ScaffoldLayout.Cell> toCells(List<BlockPos> positions) {
        if (positions == null) {
            return List.of();
        }
        return positions.stream()
                .map(pos -> new ScaffoldLayout.Cell(pos.getX(), pos.getY(), pos.getZ()))
                .toList();
    }
}
