package com.touhoumaid.construction.placement;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.blueprint.RawBlockState;
import com.touhoumaid.construction.build.StartLayerPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates schematic placement conflicts against the world. */
public final class ConflictScanner {
    private static final int MAX_SAMPLE_POSITIONS = 64;
    private static final int MAX_BLOCK_TYPES_IN_SUMMARY = 5;

    private ConflictScanner() {
    }

    public record Result(int conflictCount, List<BlockPos> sampleConflicts, Map<Block, Integer> blockingBlocks) {
        public Result {
            sampleConflicts = List.copyOf(sampleConflicts);
            blockingBlocks = Map.copyOf(blockingBlocks);
        }

        public Result(int conflictCount, List<BlockPos> sampleConflicts) {
            this(conflictCount, sampleConflicts, Map.of());
        }

        public boolean hasConflicts() {
            return conflictCount > 0;
        }

        public String blockingSummary() {
            if (blockingBlocks.isEmpty()) {
                return String.valueOf(conflictCount);
            }
            List<Map.Entry<Block, Integer>> sorted = blockingBlocks.entrySet().stream()
                    .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                    .limit(MAX_BLOCK_TYPES_IN_SUMMARY)
                    .toList();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                Map.Entry<Block, Integer> entry = sorted.get(i);
                sb.append(entry.getKey().getName().getString()).append("×").append(entry.getValue());
            }
            int shown = sorted.stream().mapToInt(Map.Entry::getValue).sum();
            if (conflictCount > shown) {
                sb.append("…");
            }
            return sb.toString();
        }

        public Component blockingMessage(boolean coverTerrain) {
            return blockingMessage(coverTerrain, false);
        }

        public Component blockingMessage(boolean coverTerrain, boolean ignoreOverlaps) {
            String summary = blockingSummary();
            if (ignoreOverlaps) {
                return Component.translatable("message.touhou_maid_construction.site_pick_blocked_unbreakable", summary);
            }
            if (coverTerrain) {
                return Component.translatable("message.touhou_maid_construction.site_pick_blocked", summary);
            }
            return Component.translatable("message.touhou_maid_construction.site_pick_bad_detail", summary);
        }
    }

    /**
     * Places each region as a positive box starting at {@code origin} (ignores schematic world Position;
     * multi-region relative offsets use region Position minus the first region's Position).
     */
    public static Result scan(
            BlockGetter level, Blueprint blueprint, BlockPos origin, boolean coverTerrain, int yaw) {
        return scan(level, blueprint, origin, coverTerrain, false, yaw, 0);
    }

    public static Result scan(
            BlockGetter level,
            Blueprint blueprint,
            BlockPos origin,
            boolean coverTerrain,
            boolean ignoreOverlaps,
            int yaw) {
        return scan(level, blueprint, origin, coverTerrain, ignoreOverlaps, yaw, 0);
    }

    public static Result scan(
            BlockGetter level,
            Blueprint blueprint,
            BlockPos origin,
            boolean coverTerrain,
            boolean ignoreOverlaps,
            int yaw,
            int startLayer) {
        int conflicts = 0;
        List<BlockPos> samples = new ArrayList<>();
        Map<Block, Integer> blocking = new LinkedHashMap<>();
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        int clampedStart = StartLayerPolicy.clamp(startLayer, footprint.sizeY());
        for (BlueprintRegion region : blueprint.regions().values()) {
            Vec3i size = region.size();
            for (int y = 0; y < size.getY(); y++) {
                int schematicY = BlueprintPlacement.schematicY(blueprint, region, y);
                if (!StartLayerPolicy.includesLocalY(schematicY, clampedStart)) {
                    continue;
                }
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        RawBlockState raw = region.blockAt(x, y, z);
                        BlockState planned = BlockStateResolve.resolve(raw);
                        if (planned.isAir()) {
                            continue;
                        }
                        BlockPos world = BlueprintPlacement.worldPos(
                                origin, blueprint, region, footprint, x, y, z, yaw);
                        BlockState existing = level.getBlockState(world);
                        if (PlacementRules.isConflict(existing, planned, coverTerrain, ignoreOverlaps)) {
                            conflicts++;
                            blocking.merge(existing.getBlock(), 1, Integer::sum);
                            if (samples.size() < MAX_SAMPLE_POSITIONS) {
                                samples.add(world.immutable());
                            }
                        }
                    }
                }
            }
        }
        return new Result(conflicts, samples, blocking);
    }
}
