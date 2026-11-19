package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Distinguishes deferred cells waiting on another blueprint block from cells that can never
 * survive (missing terrain / external support). The latter should be marked failed, not retried.
 */
public final class BuildDeferSupportPolicy {
    private static final BlockPos[] NEIGHBOR_OFFSETS = {
            new BlockPos(0, 1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, -1),
            new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, 0)
    };

    private BuildDeferSupportPolicy() {
    }

    /**
     * @return true when {@link BuildPlacementCheck#canSurvive} is false only because an
     *         unplaced blueprint neighbour still needs to be built first
     */
    public static boolean awaitsSchematicSupport(
            ServerLevel level,
            BuildTask task,
            int placementYaw,
            Map<Long, BuildTask> taskByPos,
            Set<Long> placed,
            Set<Long> skipped,
            Set<Long> failed,
            BiPredicate<ServerLevel, BuildTask> worldMatches) {
        if (BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
            return false;
        }
        BlockPos pos = task.worldPos();
        for (BlockPos offset : NEIGHBOR_OFFSETS) {
            long packed = BuildJob.pack(pos.offset(offset));
            BuildTask neighbor = taskByPos.get(packed);
            if (neighbor == null) {
                continue;
            }
            if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
                continue;
            }
            if (worldMatches.test(level, neighbor)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * @return the first unplaced blueprint neighbour that still blocks {@code task} from surviving
     */
    public static Optional<BuildTask> findBlockingSupportTask(
            ServerLevel level,
            BuildTask task,
            int placementYaw,
            Map<Long, BuildTask> taskByPos,
            Set<Long> placed,
            Set<Long> skipped,
            Set<Long> failed,
            BiPredicate<ServerLevel, BuildTask> worldMatches) {
        if (BuildPlacementCheck.canSurvive(level, task, placementYaw)) {
            return Optional.empty();
        }
        BlockPos pos = task.worldPos();
        for (BlockPos offset : NEIGHBOR_OFFSETS) {
            long packed = BuildJob.pack(pos.offset(offset));
            BuildTask neighbor = taskByPos.get(packed);
            if (neighbor == null) {
                continue;
            }
            if (placed.contains(packed) || skipped.contains(packed) || failed.contains(packed)) {
                continue;
            }
            if (worldMatches.test(level, neighbor)) {
                continue;
            }
            if (!FluidEndPlacePolicy.isForcePlaceSupport(neighbor.raw().name())) {
                continue;
            }
            return Optional.of(neighbor);
        }
        return Optional.empty();
    }
}
