package com.touhoumaid.construction.agenttest;

import com.touhoumaid.construction.blueprint.LitematicBitArray;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Writes minimal gzip {@code .litematic} fixtures for agent tests. */
public final class AgentTestLitematicWriter {
    private AgentTestLitematicWriter() {
    }

    public static void writeStoneCube(Path target, int sizeX, int sizeY, int sizeZ) throws IOException {
        int volume = sizeX * sizeY * sizeZ;
        int[] indices = new int[volume];
        Arrays.fill(indices, 1);
        writeRegion(target, "agent_test", sizeX, sizeY, sizeZ, indices);
    }

    private static void writeRegion(Path target, String regionName, int sizeX, int sizeY, int sizeZ, int[] indices)
            throws IOException {
        if (indices.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("indices length mismatch");
        }

        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:stone"));

        int bits = LitematicBitArray.bitsForPaletteSize(palette.size());
        long[] packed = pack(indices, bits);

        CompoundTag region = new CompoundTag();
        region.put("Position", vec3i(0, 0, 0));
        region.put("Size", vec3i(sizeX, sizeY, sizeZ));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", packed);
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());

        CompoundTag regions = new CompoundTag();
        regions.put(regionName, region);

        CompoundTag meta = new CompoundTag();
        meta.putString("Name", regionName);
        meta.putString("Author", "agent-test");
        meta.putString("Description", "mc-test-harness fixture");
        meta.putLong("TimeCreated", 0L);
        meta.putLong("TimeModified", 0L);
        meta.putInt("TotalBlocks", indices.length);
        meta.putInt("TotalVolume", indices.length);
        meta.put("EnclosingSize", vec3i(sizeX, sizeY, sizeZ));
        meta.putInt("RegionCount", 1);

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 4);
        root.putInt("MinecraftDataVersion", 3465);
        root.put("Metadata", meta);
        root.put("Regions", regions);

        Files.createDirectories(target.getParent());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            NbtIo.writeCompressed(root, out);
        }
    }

    private static CompoundTag paletteEntry(String blockId) {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", blockId);
        return entry;
    }

    private static CompoundTag vec3i(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    /** Litematica index order: {@code x + z * sizeX + y * sizeX * sizeZ}. */
    private static long[] pack(int[] indices, int bitsPerEntry) {
        long mask = (1L << bitsPerEntry) - 1L;
        int longCount = (indices.length * bitsPerEntry + 63) / 64;
        long[] data = new long[Math.max(1, longCount)];
        for (int index = 0; index < indices.length; index++) {
            long value = indices[index] & mask;
            long startBit = (long) index * bitsPerEntry;
            int startLong = (int) (startBit >>> 6);
            int bitOffset = (int) (startBit & 63L);
            data[startLong] |= value << bitOffset;
            if (bitOffset + bitsPerEntry > 64) {
                int endBits = bitOffset + bitsPerEntry - 64;
                data[startLong + 1] |= value >>> (bitsPerEntry - endBits);
            }
        }
        return data;
    }
}
