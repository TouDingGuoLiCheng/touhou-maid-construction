package com.touhoumaid.construction.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LitematicBitArrayTest {
    @Test
    void bitsForPaletteSize_matchesLitematicaMinimumTwo() {
        assertEquals(2, LitematicBitArray.bitsForPaletteSize(1));
        assertEquals(2, LitematicBitArray.bitsForPaletteSize(2));
        assertEquals(2, LitematicBitArray.bitsForPaletteSize(4));
        assertEquals(8, LitematicBitArray.bitsForPaletteSize(186));
    }

    @Test
    void get_readsPackedOnesAcrossLongs_likeHutFixture() {
        // From 黑顶砂岩小屋.litematic: bits=8, first longs are 0x0101010101010101 => every byte is palette index 1
        long packed = 72340172838076673L; // 0x0101010101010101
        long[] data = new long[4];
        for (int i = 0; i < data.length; i++) {
            data[i] = packed;
        }
        LitematicBitArray array = new LitematicBitArray(data, 8, 32);
        for (int i = 0; i < 32; i++) {
            assertEquals(1, array.get(i), "index " + i);
        }
    }

    @Test
    void get_handlesCrossLongBoundary() {
        // 3 bits per entry; index 21 starts at bit 63 of long0 (spans into long1)
        // Want value 5 = 0b101: low bit from long0 = 1, high 2 bits from long1 = 0b10
        long[] data = new long[] {1L << 63, 0b10L};
        LitematicBitArray array = new LitematicBitArray(data, 3, 22);
        assertEquals(5, array.get(21));
    }
}
