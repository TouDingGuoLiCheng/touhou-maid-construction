package com.touhoumaid.construction.build;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.blueprint.RawBlockState;
import com.touhoumaid.construction.placement.BlockStateResolve;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import com.touhoumaid.construction.project.MissingBlockReplacePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Expands a loaded blueprint into ordered {@link BuildTask} instances with layer indices. */
public final class BlueprintTaskBuilder {
    private BlueprintTaskBuilder() {
    }

    public static List<BuildTask> build(Blueprint blueprint, BlockPos origin, int yaw) {
        return build(blueprint, origin, yaw, 0, Map.of());
    }

    public static List<BuildTask> build(Blueprint blueprint, BlockPos origin, int yaw, int startLayer) {
        return build(blueprint, origin, yaw, startLayer, Map.of());
    }

    public static List<BuildTask> build(
            Blueprint blueprint,
            BlockPos origin,
            int yaw,
            int startLayer,
            Map<String, String> missingReplacements) {
        Map<String, String> replacements = missingReplacements == null ? Map.of() : missingReplacements;
        List<BuildTask> tasks = new ArrayList<>();
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        int clampedStart = StartLayerPolicy.clamp(startLayer, footprint.sizeY());
        for (BlueprintRegion region : blueprint.regions().values()) {
            Map<BlockPos, CompoundTag> tileByLocal = indexTileEntities(region.tileEntities());
            Vec3i size = region.size();
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        int schematicY = BlueprintPlacement.schematicY(blueprint, region, y);
                        if (!StartLayerPolicy.includesLocalY(schematicY, clampedStart)) {
                            continue;
                        }
                        RawBlockState raw = MissingBlockReplacePolicy.remap(
                                region.blockAt(x, y, z), replacements);
                        if (isSkippable(raw)) {
                            continue;
                        }
                        BlockPos local = new BlockPos(x, y, z);
                        BlockPos world = BlueprintPlacement.worldPos(
                                origin, blueprint, region, footprint, x, y, z, yaw);
                        tasks.add(new BuildTask(
                                region.name(),
                                local,
                                world,
                                raw,
                                tileByLocal.get(local),
                                0,
                                BuildPlacementCheck.attachPriority(raw.name())));
                    }
                }
            }
        }
        assignLayers(tasks);
        return tasks;
    }

    private static void assignLayers(List<BuildTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        int minWorldY = tasks.stream().mapToInt(t -> t.worldPos().getY()).min().orElse(0);
        List<BuildTask> layered = new ArrayList<>(tasks.size());
        for (BuildTask task : tasks) {
            int layer = task.worldPos().getY() - minWorldY;
            layered.add(new BuildTask(
                    task.regionName(),
                    task.localPos(),
                    task.worldPos(),
                    task.raw(),
                    task.tileEntityNbt(),
                    layer,
                    task.attachPriority()));
        }
        tasks.clear();
        tasks.addAll(layered);
    }

    private static boolean isSkippable(RawBlockState raw) {
        if ("minecraft:air".equals(raw.name())) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw.name());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return true;
        }
        BlockState state = BlockStateResolve.resolve(raw);
        return state.isAir();
    }

    private static Map<BlockPos, CompoundTag> indexTileEntities(ListTag list) {
        Map<BlockPos, CompoundTag> map = new HashMap<>();
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag compound)) {
                continue;
            }
            if (!compound.contains("Pos", Tag.TAG_INT_ARRAY)) {
                continue;
            }
            int[] pos = compound.getIntArray("Pos");
            if (pos.length >= 3) {
                map.put(new BlockPos(pos[0], pos[1], pos[2]), compound);
            }
        }
        return map;
    }
}
