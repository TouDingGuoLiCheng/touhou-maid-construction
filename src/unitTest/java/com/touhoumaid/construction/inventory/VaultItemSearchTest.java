package com.touhoumaid.construction.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultItemSearchTest {
    @Test
    void normalizeQueryTrimsAndLowercases() {
        assertEquals("stone", VaultItemSearch.normalizeQuery("  StOnE "));
        assertEquals("", VaultItemSearch.normalizeQuery("   "));
        assertEquals("", VaultItemSearch.normalizeQuery(null));
    }

    @Test
    void emptyQueryDoesNotMatch() {
        assertFalse(VaultItemSearch.matches("Stone", "minecraft:stone", ""));
        assertFalse(VaultItemSearch.matches("Stone", "minecraft:stone", "  "));
        assertFalse(VaultItemSearch.matches("Stone", "minecraft:stone", null));
    }

    @Test
    void matchesDisplayNameSubstringIgnoringCase() {
        assertTrue(VaultItemSearch.matches("圆石", "minecraft:cobblestone", "圆"));
        assertTrue(VaultItemSearch.matches("Oak Planks", "minecraft:oak_planks", "plank"));
        assertFalse(VaultItemSearch.matches("Oak Planks", "minecraft:oak_planks", "stone"));
    }

    @Test
    void matchesItemIdSubstringIgnoringCase() {
        assertTrue(VaultItemSearch.matches("石头", "minecraft:stone", "minecraft:stone"));
        assertTrue(VaultItemSearch.matches("石头", "minecraft:stone", "STONE"));
        assertTrue(VaultItemSearch.matches("石头", "minecraft:stone", "minec"));
        assertFalse(VaultItemSearch.matches("石头", "minecraft:stone", "diamond"));
    }

    @Test
    void emptyNameAndIdDoNotMatchNonEmptyQuery() {
        assertFalse(VaultItemSearch.matches("", "", "stone"));
        assertFalse(VaultItemSearch.matches(null, null, "stone"));
    }
}
