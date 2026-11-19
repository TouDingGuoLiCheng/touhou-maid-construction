package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildCellSatisfiedPolicyTest {
    @Test
    void sameBlockTypeCountsAsPlacedEvenWhenPropertiesDiffer() {
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_log", "minecraft:oak_log"));
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_stairs", "minecraft:oak_stairs"));
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_leaves", "minecraft:oak_leaves"));
    }

    @Test
    void waterloggedSchematicStillSatisfiedByDrySameBlock() {
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_stairs", "minecraft:oak_stairs"));
    }

    @Test
    void dirtAndGrassStillCountAsEachOther() {
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:dirt", "minecraft:grass_block"));
        assertTrue(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:grass_block", "minecraft:dirt"));
    }

    @Test
    void differentBlockOrAirIsNotSatisfied() {
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_planks", "minecraft:stone"));
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:oak_log", "minecraft:air"));
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:stone", "minecraft:cave_air"));
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied(
                "minecraft:air", "minecraft:stone"));
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied(null, "minecraft:stone"));
        assertFalse(BuildCellSatisfiedPolicy.alreadySatisfied("minecraft:stone", ""));
    }

    @Test
    void claimSkipsCellsTheWorldAlreadySatisfies() {
        assertFalse(BuildCellSatisfiedPolicy.shouldClaim(true));
        assertTrue(BuildCellSatisfiedPolicy.shouldClaim(false));
    }
}
