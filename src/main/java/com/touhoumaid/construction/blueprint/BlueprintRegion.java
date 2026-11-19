package com.touhoumaid.construction.blueprint;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Collections;
import java.util.List;

/**
 * One litematic region: palette + packed states + tile entities + entities.
 * Local index order matches Litematica: {@code index = x + z * sizeX + y * sizeX * sizeZ}.
 */
public final class BlueprintRegion {
    private final String name;
    private final Vec3i position;
    private final Vec3i signedSize;
    private final Vec3i size;
    private final List<RawBlockState> palette;
    private final LitematicBitArray blockStates;
    private final ListTag tileEntities;
    private final ListTag entities;
    private final ListTag pendingBlockTicks;
    private final ListTag pendingFluidTicks;

    public BlueprintRegion(
            String name,
            Vec3i position,
            Vec3i signedSize,
            List<RawBlockState> palette,
            LitematicBitArray blockStates,
            ListTag tileEntities,
            ListTag entities,
            ListTag pendingBlockTicks,
            ListTag pendingFluidTicks) {
        this.name = name;
        this.position = position;
        this.signedSize = signedSize;
        this.size = new Vec3i(Math.abs(signedSize.getX()), Math.abs(signedSize.getY()), Math.abs(signedSize.getZ()));
        this.palette = List.copyOf(palette);
        this.blockStates = blockStates;
        this.tileEntities = tileEntities == null ? new ListTag() : tileEntities.copy();
        this.entities = entities == null ? new ListTag() : entities.copy();
        this.pendingBlockTicks = pendingBlockTicks == null ? new ListTag() : pendingBlockTicks.copy();
        this.pendingFluidTicks = pendingFluidTicks == null ? new ListTag() : pendingFluidTicks.copy();
    }

    public String name() {
        return name;
    }

    public Vec3i position() {
        return position;
    }

    /** Signed Size from file (may be negative for mirroring). */
    public Vec3i signedSize() {
        return signedSize;
    }

    /** Absolute width/height/depth. */
    public Vec3i size() {
        return size;
    }

    public int volume() {
        return size.getX() * size.getY() * size.getZ();
    }

    public List<RawBlockState> palette() {
        return palette;
    }

    public LitematicBitArray blockStates() {
        return blockStates;
    }

    public ListTag tileEntities() {
        return tileEntities;
    }

    public ListTag entities() {
        return entities;
    }

    public ListTag pendingBlockTicks() {
        return pendingBlockTicks;
    }

    public ListTag pendingFluidTicks() {
        return pendingFluidTicks;
    }

    public int index(int x, int y, int z) {
        return x + z * size.getX() + y * size.getX() * size.getZ();
    }

    public RawBlockState blockAt(int x, int y, int z) {
        int paletteIndex = blockStates.get(index(x, y, z));
        return palette.get(paletteIndex);
    }

    public int countNonAir() {
        int airIndex = -1;
        for (int i = 0; i < palette.size(); i++) {
            if ("minecraft:air".equals(palette.get(i).name())) {
                airIndex = i;
                break;
            }
        }
        int count = 0;
        int volume = volume();
        for (int i = 0; i < volume; i++) {
            int idx = blockStates.get(i);
            if (idx != airIndex) {
                count++;
            }
        }
        return count;
    }

    public List<RawBlockState> paletteView() {
        return Collections.unmodifiableList(palette);
    }
}
