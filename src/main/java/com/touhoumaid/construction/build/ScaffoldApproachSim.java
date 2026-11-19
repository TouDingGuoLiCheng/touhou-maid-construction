package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Simulate reachability treating air as future scaffold, then list cells to place.
 * Work stand is a solid destination — never part of the scaffold to-place list.
 */
public final class ScaffoldApproachSim {
    public record Result(
            List<ScaffoldLayout.Cell> path,
            List<ScaffoldLayout.Cell> toPlace,
            ScaffoldLayout.Cell workStand) {
    }

    private ScaffoldApproachSim() {
    }

    /**
     * @param existingPassable already walkable for pathfinding (scaffolds + maid feet/head)
     * @param airPassable      air/replaceable cells that may become scaffolds
     * @param workStand        solid footing destination (must stay a work stand, not scaffold)
     */
    public static Optional<Result> simulate(
            ScaffoldLayout.Cell from,
            ScaffoldLayout.Cell workStand,
            Set<Long> existingPassable,
            Set<Long> airPassable,
            int maxNodes) {
        if (from == null || workStand == null || existingPassable == null || airPassable == null) {
            return Optional.empty();
        }
        Set<Long> simulated = new HashSet<>(existingPassable);
        simulated.addAll(airPassable);
        simulated.add(ScaffoldPathfinder.key(from));
        simulated.add(ScaffoldPathfinder.key(workStand));
        List<ScaffoldLayout.Cell> path =
                ScaffoldPathfinder.findPath(from, workStand, simulated, maxNodes);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        List<ScaffoldLayout.Cell> toPlace = new ArrayList<>();
        long standKey = ScaffoldPathfinder.key(workStand);
        for (ScaffoldLayout.Cell cell : path) {
            long key = ScaffoldPathfinder.key(cell);
            if (key == standKey) {
                continue;
            }
            // Maid feet/head are in existingPassable for routing but are still air — must be built.
            if (existingPassable.contains(key) && !airPassable.contains(key)) {
                continue;
            }
            if (airPassable.contains(key)) {
                toPlace.add(cell);
            }
        }
        return Optional.of(new Result(List.copyOf(path), List.copyOf(toPlace), workStand));
    }

    /**
     * Path via a preferred column top beside the building, then a short bridge to the solid stand.
     * Keeps vertical climbs on the building edge instead of at the maid's distant feet.
     */
    public static Optional<Result> simulateViaWaypoint(
            ScaffoldLayout.Cell from,
            ScaffoldLayout.Cell waypoint,
            ScaffoldLayout.Cell workStand,
            Set<Long> existingPassable,
            Set<Long> airPassable,
            int maxNodes) {
        if (from == null || waypoint == null || workStand == null
                || existingPassable == null || airPassable == null) {
            return Optional.empty();
        }
        if (waypoint.equals(workStand)) {
            return simulate(from, workStand, existingPassable, airPassable, maxNodes);
        }
        Set<Long> simulated = new HashSet<>(existingPassable);
        simulated.addAll(airPassable);
        simulated.add(ScaffoldPathfinder.key(from));
        simulated.add(ScaffoldPathfinder.key(waypoint));
        simulated.add(ScaffoldPathfinder.key(workStand));
        List<ScaffoldLayout.Cell> leg1 =
                ScaffoldPathfinder.findPath(from, waypoint, simulated, maxNodes);
        if (leg1.isEmpty()) {
            return Optional.empty();
        }
        List<ScaffoldLayout.Cell> leg2 =
                ScaffoldPathfinder.findPath(waypoint, workStand, simulated, maxNodes);
        if (leg2.isEmpty()) {
            return Optional.empty();
        }
        List<ScaffoldLayout.Cell> path = new ArrayList<>(leg1);
        for (int i = 1; i < leg2.size(); i++) {
            path.add(leg2.get(i));
        }
        List<ScaffoldLayout.Cell> toPlace = new ArrayList<>();
        long standKey = ScaffoldPathfinder.key(workStand);
        for (ScaffoldLayout.Cell cell : path) {
            long key = ScaffoldPathfinder.key(cell);
            if (key == standKey) {
                continue;
            }
            if (existingPassable.contains(key) && !airPassable.contains(key)) {
                continue;
            }
            if (airPassable.contains(key)) {
                toPlace.add(cell);
            }
        }
        return Optional.of(new Result(List.copyOf(path), List.copyOf(toPlace), workStand));
    }
}
