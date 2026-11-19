package com.touhoumaid.construction.blueprint;

/**
 * Litematica / schematic long-array bit packing (cross-long aware).
 * Bits per entry = max(2, ceil(log2(paletteSize))).
 */
public final class LitematicBitArray {
    private final long[] data;
    private final int bitsPerEntry;
    private final long mask;
    private final int size;

    public LitematicBitArray(long[] data, int bitsPerEntry, int size) {
        if (bitsPerEntry < 1 || bitsPerEntry > 32) {
            throw new IllegalArgumentException("bitsPerEntry out of range: " + bitsPerEntry);
        }
        this.data = data;
        this.bitsPerEntry = bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1L;
        this.size = size;
    }

    public static int bitsForPaletteSize(int paletteSize) {
        if (paletteSize <= 0) {
            throw new IllegalArgumentException("paletteSize must be > 0");
        }
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(2, bits);
    }

    public int size() {
        return size;
    }

    public int bitsPerEntry() {
        return bitsPerEntry;
    }

    public int get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + " size=" + size);
        }
        long startBit = (long) index * bitsPerEntry;
        int startLong = (int) (startBit >>> 6);
        int bitOffset = (int) (startBit & 63L);
        if (bitOffset + bitsPerEntry <= 64) {
            return (int) ((data[startLong] >>> bitOffset) & mask);
        }
        int endBits = bitOffset + bitsPerEntry - 64;
        long low = data[startLong] >>> bitOffset;
        long high = data[startLong + 1] & ((1L << endBits) - 1L);
        return (int) ((low | (high << (64 - bitOffset))) & mask);
    }
}
