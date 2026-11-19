package com.touhoumaid.construction.blueprint;

import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads gzip-compressed {@code .litematic} NBT into {@link Blueprint}.
 */
public final class LitematicReader {
    private LitematicReader() {
    }

    public static Blueprint read(Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            CompoundTag root = NbtIo.readCompressed(in);
            return fromRoot(path.getFileName().toString(), root);
        }
    }

    public static Blueprint fromRoot(String sourceName, CompoundTag root) {
        int schematicVersion = root.getInt("Version");
        int dataVersion = root.getInt("MinecraftDataVersion");

        CompoundTag meta = root.getCompound("Metadata");
        String name = meta.getString("Name");
        String author = meta.getString("Author");
        String description = meta.getString("Description");
        long timeCreated = meta.getLong("TimeCreated");
        long timeModified = meta.getLong("TimeModified");
        int totalBlocks = meta.getInt("TotalBlocks");
        int totalVolume = meta.getInt("TotalVolume");
        Vec3i enclosing = readVec3i(meta.getCompound("EnclosingSize"));

        CompoundTag regionsTag = root.getCompound("Regions");
        Map<String, BlueprintRegion> regions = new LinkedHashMap<>();
        for (String regionName : regionsTag.getAllKeys()) {
            regions.put(regionName, readRegion(regionName, regionsTag.getCompound(regionName)));
        }

        return new Blueprint(
                sourceName,
                schematicVersion,
                dataVersion,
                name,
                author,
                description,
                timeCreated,
                timeModified,
                totalBlocks,
                totalVolume,
                enclosing,
                regions);
    }

    private static BlueprintRegion readRegion(String regionName, CompoundTag region) {
        Vec3i position = readVec3i(region.getCompound("Position"));
        Vec3i signedSize = readVec3i(region.getCompound("Size"));
        Vec3i absSize = new Vec3i(
                Math.abs(signedSize.getX()),
                Math.abs(signedSize.getY()),
                Math.abs(signedSize.getZ()));

        ListTag paletteTag = region.getList("BlockStatePalette", Tag.TAG_COMPOUND);
        List<RawBlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(readPaletteEntry(paletteTag.getCompound(i)));
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("Region '" + regionName + "' has empty BlockStatePalette");
        }

        long[] packed = region.getLongArray("BlockStates");
        int bits = LitematicBitArray.bitsForPaletteSize(palette.size());
        int volume = absSize.getX() * absSize.getY() * absSize.getZ();
        LitematicBitArray bitArray = new LitematicBitArray(packed, bits, volume);

        ListTag tileEntities = region.contains("TileEntities", Tag.TAG_LIST)
                ? region.getList("TileEntities", Tag.TAG_COMPOUND)
                : new ListTag();
        ListTag entities = region.contains("Entities", Tag.TAG_LIST)
                ? region.getList("Entities", Tag.TAG_COMPOUND)
                : new ListTag();
        ListTag pendingBlockTicks = region.contains("PendingBlockTicks", Tag.TAG_LIST)
                ? region.getList("PendingBlockTicks", Tag.TAG_COMPOUND)
                : new ListTag();
        ListTag pendingFluidTicks = region.contains("PendingFluidTicks", Tag.TAG_LIST)
                ? region.getList("PendingFluidTicks", Tag.TAG_COMPOUND)
                : new ListTag();

        return new BlueprintRegion(
                regionName,
                position,
                signedSize,
                palette,
                bitArray,
                tileEntities,
                entities,
                pendingBlockTicks,
                pendingFluidTicks);
    }

    private static RawBlockState readPaletteEntry(CompoundTag entry) {
        String name = entry.getString("Name");
        MapProps props = new MapProps();
        if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = entry.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                props.put(key, properties.getString(key));
            }
        }
        return new RawBlockState(name, props);
    }

    private static Vec3i readVec3i(CompoundTag tag) {
        return new Vec3i(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    /** Tiny mutable map builder without exposing mutability in RawBlockState ctor site. */
    private static final class MapProps extends java.util.LinkedHashMap<String, String> {
    }
}
