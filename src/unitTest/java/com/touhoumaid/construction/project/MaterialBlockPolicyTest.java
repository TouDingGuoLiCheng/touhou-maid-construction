package com.touhoumaid.construction.project;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialBlockPolicyTest {
    @Test
    void caveVinesPlantMapsToGlowBerries() {
        assertEquals("minecraft:glow_berries", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:cave_vines_plant"));
    }

    @Test
    void tallGrassMapsToWheatSeeds() {
        assertEquals("minecraft:wheat_seeds", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:tall_grass"));
    }

    @Test
    void normalBlockHasNoAlias() {
        assertEquals("", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:grass_block"));
    }

    @Test
    void glowLichenIsAutoIgnored() {
        assertTrue(MaterialBlockPolicy.isAutoIgnored("minecraft:glow_lichen"));
    }

    @Test
    void fireIsAutoIgnored() {
        assertTrue(MaterialBlockPolicy.isAutoIgnored("minecraft:fire"));
    }

    @Test
    void grassBlockIsNotAutoIgnored() {
        assertFalse(MaterialBlockPolicy.isAutoIgnored("minecraft:grass_block"));
    }

    @Test
    void dirtAndGrassBlockAreBuildEquivalent() {
        assertTrue(MaterialBlockPolicy.areBuildSurfaceEquivalent(
                "minecraft:dirt", "minecraft:grass_block"));
        assertTrue(MaterialBlockPolicy.areBuildSurfaceEquivalent(
                "minecraft:grass_block", "minecraft:dirt"));
        assertTrue(MaterialBlockPolicy.areBuildSurfaceEquivalent(
                "minecraft:dirt", "minecraft:dirt"));
        assertFalse(MaterialBlockPolicy.areBuildSurfaceEquivalent(
                "minecraft:dirt", "minecraft:stone"));
        assertFalse(MaterialBlockPolicy.areBuildSurfaceEquivalent(
                "minecraft:coarse_dirt", "minecraft:grass_block"));
    }

    @Test
    void dirtAndGrassExposeEachOtherAsVaultMatches() {
        assertTrue(MaterialBlockPolicy.equivalentVaultMatchIds("minecraft:dirt")
                .contains("minecraft:grass_block"));
        assertTrue(MaterialBlockPolicy.equivalentVaultMatchIds("minecraft:grass_block")
                .contains("minecraft:dirt"));
        assertTrue(MaterialBlockPolicy.equivalentVaultMatchIds("minecraft:stone").isEmpty());
    }

    @Test
    void waterAndLavaHaveNoVaultAlias() {
        assertEquals("", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:water"));
        assertEquals("", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:flowing_water"));
        assertEquals("", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:lava"));
        assertEquals("", MaterialBlockPolicy.vaultItemIdForBlockId("minecraft:flowing_lava"));
    }

    @Test
    void waterAndLavaAreAutoFreeNotIgnored() {
        assertTrue(MaterialBlockPolicy.isAutoFree("minecraft:water"));
        assertTrue(MaterialBlockPolicy.isAutoFree("minecraft:flowing_water"));
        assertTrue(MaterialBlockPolicy.isAutoFree("minecraft:lava"));
        assertTrue(MaterialBlockPolicy.isAutoFree("minecraft:flowing_lava"));
        assertFalse(MaterialBlockPolicy.isAutoIgnored("minecraft:water"));
        assertFalse(MaterialBlockPolicy.isAutoIgnored("minecraft:lava"));
        assertFalse(MaterialBlockPolicy.isAutoFree("minecraft:stone"));
        assertFalse(MaterialBlockPolicy.needsVaultMaterial("minecraft:water"));
        assertTrue(MaterialBlockPolicy.needsVaultMaterial("minecraft:stone"));
        assertFalse(MaterialBlockPolicy.needsVaultMaterial("minecraft:fire"));
    }
}
