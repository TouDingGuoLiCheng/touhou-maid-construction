package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid placement: extend columns vertically ahead; defer horizontal spans until the maid
 * stands on the source column top.
 */
public final class ScaffoldPlacementPhasePolicy {
    private ScaffoldPlacementPhasePolicy() {
    }

    public static boolean mayPlaceNow(
            BlockPos maidFeet,
            BlockPos cell,
            List<ScaffoldLayout.Cell> path,
            Set<Long> presentCells) {
        if (maidFeet == null || cell == null) {
            return false;
        }
        return mayPlaceNowCells(
                new ScaffoldLayout.Cell(maidFeet.getX(), maidFeet.getY(), maidFeet.getZ()),
                new ScaffoldLayout.Cell(cell.getX(), cell.getY(), cell.getZ()),
                path,
                presentCells);
    }

    static boolean mayPlaceNowCells(
            ScaffoldLayout.Cell maidFeet,
            ScaffoldLayout.Cell cell,
            List<ScaffoldLayout.Cell> path,
            Set<Long> presentCells) {
        if (maidFeet == null || cell == null || path == null || path.isEmpty()) {
            return false;
        }
        ScaffoldLayout.Cell previous = previousScaffoldCell(cell, path, presentCells);
        if (previous == null) {
            return true;
        }
        if (previous.x() == cell.x() && previous.z() == cell.z()) {
            return true;
        }
        int sourceTopY = columnTopY(path, presentCells, previous.x(), previous.z());
        return maidFeet.x() == previous.x()
                && maidFeet.z() == previous.z()
                && maidFeet.y() >= sourceTopY;
    }

    private static ScaffoldLayout.Cell previousScaffoldCell(
            ScaffoldLayout.Cell cell, List<ScaffoldLayout.Cell> path, Set<Long> presentCells) {
        ScaffoldLayout.Cell prev = null;
        for (ScaffoldLayout.Cell step : path) {
            if (step.equals(cell)) {
                return prev;
            }
            long key = ScaffoldLayout.packCell(step.x(), step.y(), step.z());
            if (presentCells.contains(key)) {
                prev = step;
            }
        }
        return prev;
    }

    private static int columnTopY(
            List<ScaffoldLayout.Cell> path, Set<Long> presentCells, int x, int z) {
        Map<Long, Integer> tops = columnTops(path, presentCells);
        Integer top = tops.get(ScaffoldLayout.packXz(x, z));
        return top != null ? top : Integer.MIN_VALUE;
    }

    static Map<Long, Integer> columnTops(List<ScaffoldLayout.Cell> path, Set<Long> presentCells) {
        Map<Long, Integer> tops = new HashMap<>();
        for (ScaffoldLayout.Cell step : path) {
            long key = ScaffoldLayout.packCell(step.x(), step.y(), step.z());
            if (!presentCells.contains(key)) {
                continue;
            }
            long xz = ScaffoldLayout.packXz(step.x(), step.z());
            tops.merge(xz, step.y(), Math::max);
        }
        return tops;
    }
}
