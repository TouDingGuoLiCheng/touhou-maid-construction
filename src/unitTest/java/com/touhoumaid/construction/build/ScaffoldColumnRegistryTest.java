package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldColumnRegistryTest {
    @Test
    void tracksLandingAndMaxStandPerColumn() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(1, 65, 2, 63);
        registry.recordBlock(1, 70, 2, 63);

        ScaffoldColumnRegistry.Column column = registry.at(1, 2).orElseThrow();
        assertEquals(63, column.baseY());
        assertEquals(70, column.maxBlockY());
        assertEquals(71, column.maxStandY());
    }

    @Test
    void rescansColumnAfterRemoval() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(1, 65, 2, 63);
        registry.recordBlock(1, 66, 2, 63);
        registry.rescanColumn(1, 2, Set.of(ScaffoldLayout.packCell(1, 65, 2)));

        ScaffoldColumnRegistry.Column column = registry.at(1, 2).orElseThrow();
        assertEquals(65, column.maxBlockY());
        assertEquals(66, column.maxStandY());
    }

    @Test
    void dropsColumnWhenLedgerHasNoBlocksAtXz() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(1, 65, 2, 63);
        registry.rescanColumn(1, 2, Set.of());
        assertTrue(registry.at(1, 2).isEmpty());
    }
}
