package com.touhoumaid.construction.placement;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * Maps schematic local cells to world positions with optional 90° yaw.
 */
public final class BlueprintPlacement {
    private BlueprintPlacement() {
    }

    public record Footprint(int sizeX, int sizeY, int sizeZ) {
    }

    public static Footprint unrotatedFootprint(Blueprint blueprint) {
        Vec3i base = null;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (BlueprintRegion region : blueprint.regions().values()) {
            if (base == null) {
                base = region.position();
            }
            int relX = region.position().getX() - base.getX();
            int relY = region.position().getY() - base.getY();
            int relZ = region.position().getZ() - base.getZ();
            maxX = Math.max(maxX, relX + region.size().getX());
            maxY = Math.max(maxY, relY + region.size().getY());
            maxZ = Math.max(maxZ, relZ + region.size().getZ());
        }
        return new Footprint(maxX, maxY, maxZ);
    }

    public static BlockPos worldPos(
            BlockPos origin,
            Blueprint blueprint,
            BlueprintRegion region,
            Footprint footprint,
            int localX,
            int localY,
            int localZ,
            int yaw) {
        Vec3i first = firstRegionPos(blueprint);
        int gx = region.position().getX() - first.getX() + localX;
        int gy = schematicY(blueprint, region, localY);
        int gz = region.position().getZ() - first.getZ() + localZ;
        return origin.offset(
                SchematicYaw.offsetX(gx, gz, footprint.sizeX(), footprint.sizeZ(), yaw),
                gy,
                SchematicYaw.offsetZ(gx, gz, footprint.sizeX(), footprint.sizeZ(), yaw));
    }

    /** Blueprint-relative Y from the lowest region bottom (matches start-layer indexing). */
    public static int schematicY(Blueprint blueprint, BlueprintRegion region, int localY) {
        Vec3i first = firstRegionPos(blueprint);
        return region.position().getY() - first.getY() + localY;
    }

    public static Bounds placedBounds(BlockPos origin, Footprint footprint, int yaw) {
        return placedBounds(origin, footprint, yaw, 0);
    }

    public static Bounds placedBounds(BlockPos origin, Footprint footprint, int yaw, int startLayer) {
        int sizeX = SchematicYaw.placedSizeX(footprint.sizeX(), footprint.sizeZ(), yaw);
        int sizeZ = SchematicYaw.placedSizeZ(footprint.sizeX(), footprint.sizeZ(), yaw);
        int height = Math.max(1, footprint.sizeY());
        int clamped = Math.max(0, Math.min(startLayer, height - 1));
        return new Bounds(
                origin.getX(),
                origin.getY() + clamped,
                origin.getZ(),
                origin.getX() + sizeX,
                origin.getY() + footprint.sizeY(),
                origin.getZ() + sizeZ);
    }

    public static Vec3i firstRegionPos(Blueprint blueprint) {
        for (BlueprintRegion region : blueprint.regions().values()) {
            return region.position();
        }
        return Vec3i.ZERO;
    }

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
