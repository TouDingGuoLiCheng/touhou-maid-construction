package com.touhoumaid.construction.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionSupplyApiTest {
    @Test
    void shortageAmountIsRequiredMinusHaveFlooredAtZero() {
        assertEquals(3, ConstructionSupplyApi.shortageAmount(5, 2));
        assertEquals(0, ConstructionSupplyApi.shortageAmount(5, 5));
        assertEquals(0, ConstructionSupplyApi.shortageAmount(2, 9));
        assertEquals(0, ConstructionSupplyApi.shortageAmount(0, 0));
    }

    @Test
    void shortageLineDefaultsNulls() {
        ShortageLine line = new ShortageLine(null, null, -4);
        assertEquals("", line.blockId());
        assertEquals("", line.purchaseItemId());
        assertEquals(0, line.need());
    }
}
