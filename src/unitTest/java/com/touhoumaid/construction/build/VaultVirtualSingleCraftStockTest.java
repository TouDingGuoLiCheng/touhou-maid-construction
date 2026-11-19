package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultVirtualSingleCraftStockTest {

    @Test
    void bonesBecomeBoneMealThenBoneBlocksWithinThreeHops() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone", 9);
        Map<String, Integer> augmented = VaultVirtualSingleCraftStock.augment(
                stock, boneChainRecipes(), 3);
        // 9 bones → 27 meal → 3 blocks (9 meal each); meal consumed into blocks
        assertEquals(3, augmented.getOrDefault("minecraft:bone_block", 0));
        assertEquals(0, augmented.getOrDefault("minecraft:bone_meal", 0));
        assertEquals(0, augmented.getOrDefault("minecraft:bone", 0));
        // original map untouched
        assertEquals(9, stock.get("minecraft:bone"));
    }

    @Test
    void targetedAugmentDoesNotWasteBoneMealOnUnrelatedWhiteDye() {
        // Dye recipe id sorts before bone_block — unrestricted augment burns all meal into dye
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone_meal", 1000);
        List<VaultCraftRecipeView> recipes = List.of(
                new VaultCraftRecipeView(
                        "minecraft:aaa_white_dye_from_bone_meal",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone_meal"), 1)),
                        "minecraft:white_dye",
                        1),
                new VaultCraftRecipeView(
                        "minecraft:bone_block",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone_meal"), 9)),
                        "minecraft:bone_block",
                        1));
        Map<String, Integer> unrestricted = VaultVirtualSingleCraftStock.augment(stock, recipes, 3);
        assertEquals(0, unrestricted.getOrDefault("minecraft:bone_meal", 0));
        assertEquals(1000, unrestricted.getOrDefault("minecraft:white_dye", 0));
        assertEquals(0, unrestricted.getOrDefault("minecraft:bone_block", 0));

        Map<String, Integer> targeted = VaultVirtualSingleCraftStock.augment(
                stock, recipes, 3, Set.of("minecraft:bone_block"));
        assertEquals(111, targeted.getOrDefault("minecraft:bone_block", 0));
        assertEquals(1, targeted.getOrDefault("minecraft:bone_meal", 0));
        assertEquals(0, targeted.getOrDefault("minecraft:white_dye", 0));
    }

    @Test
    void targetedAugmentDoesNotSpendShortageItemsOnOtherShortages() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_planks", 20);
        List<VaultCraftRecipeView> recipes = List.of(new VaultCraftRecipeView(
                "spruce_stairs",
                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 6)),
                "minecraft:spruce_stairs",
                4));
        Map<String, Integer> targeted = VaultVirtualSingleCraftStock.augment(
                stock, recipes, 3, Set.of("minecraft:spruce_planks", "minecraft:spruce_stairs"));
        assertEquals(20, targeted.getOrDefault("minecraft:spruce_planks", 0));
        assertEquals(0, targeted.getOrDefault("minecraft:spruce_stairs", 0));
    }

    @Test
    void targetedAugmentStillAllowsBoneToMealToBlockChain() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone", 9);
        Map<String, Integer> targeted = VaultVirtualSingleCraftStock.augment(
                stock, boneChainRecipes(), 3, Set.of("minecraft:bone_block"));
        assertEquals(3, targeted.getOrDefault("minecraft:bone_block", 0));
        assertEquals(0, targeted.getOrDefault("minecraft:bone_meal", 0));
        assertEquals(0, targeted.getOrDefault("minecraft:bone", 0));
    }

    @Test
    void stopsAtMaxHops() {
        Map<String, Integer> stock = Map.of("minecraft:bone", 9);
        Map<String, Integer> oneHop = VaultVirtualSingleCraftStock.augment(
                stock, boneChainRecipes(), 1);
        assertEquals(27, oneHop.getOrDefault("minecraft:bone_meal", 0));
        assertFalse(oneHop.containsKey("minecraft:bone_block"));
        assertEquals(0, oneHop.getOrDefault("minecraft:bone", 0));
    }

    @Test
    void ignoresMultiIngredientRecipes() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_planks", 6);
        stock.put("minecraft:stick", 1);
        Map<String, Integer> augmented = VaultVirtualSingleCraftStock.augment(
                stock,
                List.of(new VaultCraftRecipeView(
                        "spruce_sign",
                        VaultCraftRecipes.mergeIngredients(List.of(
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                                new VaultCraftRecipeView.Ingredient(List.of("minecraft:stick"), 1))),
                        "minecraft:spruce_sign",
                        3)),
                3);
        assertFalse(augmented.containsKey("minecraft:spruce_sign"));
        assertEquals(6, augmented.get("minecraft:spruce_planks"));
        assertEquals(1, augmented.get("minecraft:stick"));
    }

    @Test
    void emptyOrNullStockReturnsEmptyCopy() {
        assertTrue(VaultVirtualSingleCraftStock.augment(null, boneChainRecipes(), 3).isEmpty());
        assertTrue(VaultVirtualSingleCraftStock.augment(Map.of(), boneChainRecipes(), 3).isEmpty());
    }

    private static List<VaultCraftRecipeView> boneChainRecipes() {
        return List.of(
                new VaultCraftRecipeView(
                        "bone_meal",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone"), 1)),
                        "minecraft:bone_meal",
                        3),
                new VaultCraftRecipeView(
                        "bone_block",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone_meal"), 9)),
                        "minecraft:bone_block",
                        1));
    }
}
