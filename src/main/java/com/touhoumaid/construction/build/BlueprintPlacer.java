package com.touhoumaid.construction.build;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.placement.BlockStateResolve;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side world mutation for construction tasks. */
public final class BlueprintPlacer {
    private BlueprintPlacer() {
    }

    public static boolean placeBlock(ServerLevel level, BuildTask task, int yaw, MaterialSource materials) {
        return placeBlock(level, task, yaw, materials, false);
    }

    /**
     * @param exactBlueprint when false, pure water/lava are refused and waterlogged blocks are placed dry
     */
    public static boolean placeBlock(
            ServerLevel level, BuildTask task, int yaw, MaterialSource materials, boolean exactBlueprint) {
        BlockState state = BlockStateResolve.resolveRotated(task.raw(), yaw);
        if (state.isAir()) {
            return false;
        }
        if (!exactBlueprint && FluidEndPlacePolicy.isEndPlacedFluid(task.raw().name())) {
            return false;
        }
        if (!exactBlueprint
                && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && Boolean.TRUE.equals(state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))) {
            state = state.setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
        }
        BlockPos pos = task.worldPos();
        if (!BlueprintPlacementClear.clearMismatch(level, materials, pos, state)) {
            return false;
        }
        if (level.getBlockState(pos).equals(state)) {
            applyTileEntity(level, pos, task.tileEntityNbt());
            return true;
        }
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        applyTileEntity(level, pos, task.tileEntityNbt());
        return true;
    }

    private static void applyTileEntity(ServerLevel level, BlockPos pos, CompoundTag source) {
        if (source == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        CompoundTag tag = source.copy();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        be.load(tag);
        be.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    /** Spawn schematic entities once all blocks for the job are placed. */
    public static void spawnEntities(ServerLevel level, Blueprint blueprint, BlockPos origin, int yaw) {
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        for (BlueprintRegion region : blueprint.regions().values()) {
            spawnRegionEntities(level, blueprint, region, footprint, origin, yaw, region.entities());
        }
    }

    private static void spawnRegionEntities(
            ServerLevel level,
            Blueprint blueprint,
            BlueprintRegion region,
            BlueprintPlacement.Footprint footprint,
            BlockPos origin,
            int yaw,
            ListTag entities) {
        for (Tag tag : entities) {
            if (!(tag instanceof CompoundTag compound)) {
                continue;
            }
            if (!compound.contains("Entity", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag entityTag = compound.getCompound("Entity");
            String id = entityTag.getString("id");
            EntityType<?> type = EntityType.byString(id).orElse(null);
            if (type == null) {
                continue;
            }
            Entity entity = type.create(level);
            if (entity == null) {
                continue;
            }
            CompoundTag spawnTag = entityTag.copy();
            if (compound.contains("Pos", Tag.TAG_LIST)) {
                net.minecraft.nbt.ListTag posList = compound.getList("Pos", Tag.TAG_DOUBLE);
                if (posList.size() >= 3) {
                    BlockPos world = BlueprintPlacement.worldPos(
                            origin, blueprint, region, footprint,
                            net.minecraft.util.Mth.floor(posList.getDouble(0)),
                            net.minecraft.util.Mth.floor(posList.getDouble(1)),
                            net.minecraft.util.Mth.floor(posList.getDouble(2)),
                            yaw);
                    entity.setPos(world.getX() + 0.5D, world.getY() + (posList.getDouble(1) - Math.floor(posList.getDouble(1))), world.getZ() + 0.5D);
                }
            } else if (compound.contains("BlockPos", Tag.TAG_INT_ARRAY)) {
                int[] bp = compound.getIntArray("BlockPos");
                if (bp.length >= 3) {
                    BlockPos world = BlueprintPlacement.worldPos(
                            origin, blueprint, region, footprint, bp[0], bp[1], bp[2], yaw);
                    entity.setPos(world.getX() + 0.5D, world.getY(), world.getZ() + 0.5D);
                }
            }
            entity.setYRot(entity.getYRot() + yaw * 90.0F);
            entity.load(spawnTag);
            level.addFreshEntity(entity);
        }
    }
}
