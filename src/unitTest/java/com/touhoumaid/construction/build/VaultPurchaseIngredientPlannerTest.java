package com.touhoumaid.construction.build;

import com.touhoumaid.construction.api.PurchaseIngredientLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultPurchaseIngredientPlannerTest {

    @Test
    void buysPlanksWhenStairsUnpurchasable() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:oak_planks".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:oak_stairs", 4, recipesStairs(), mall, 5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:oak_planks", plan.get().get(0).itemId());
        assertEquals(6, plan.get().get(0).need());
    }

    @Test
    void recursesToLogWhenPlanksUnpurchasable() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:oak_log".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:oak_stairs", 4, recipesStairs(), mall, 5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:oak_log", plan.get().get(0).itemId());
        // 4 stairs → 6 planks → ceil(6/4)=2 logs
        assertEquals(2, plan.get().get(0).need());
    }

    @Test
    void subtractsVaultStockFromBuyNeed() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:oak_planks", 2);
        Predicate<String> mall = id -> "minecraft:oak_planks".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:oak_stairs", 4, recipesStairs(), mall, 5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals(4, plan.get().get(0).need());
    }

    @Test
    void failsWhenDepthExceeded() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:oak_log".equals(id);
        // depth 0 stairs, depth 1 planks craft, depth 2 would buy log — maxDepth 1 blocks log layer
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:oak_stairs", 4, recipesStairs(), mall, 1);
        assertFalse(plan.isPresent());
    }

    @Test
    void failsWhenAnyIngredientUnpurchasable() {
        Map<String, Integer> stock = Map.of();
        Set<String> mall = new HashSet<>();
        mall.add("minecraft:spruce_planks");
        // stick not purchasable and no recipe → whole plan fails
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:spruce_sign", 3, recipesSign(), mall::contains, 5);
        assertFalse(plan.isPresent());
    }

    @Test
    void buysMultipleLeavesForSign() {
        Map<String, Integer> stock = Map.of();
        Set<String> mall = Set.of("minecraft:spruce_planks", "minecraft:stick");
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:spruce_sign", 3, recipesSign(), mall::contains, 5);
        assertTrue(plan.isPresent());
        assertEquals(2, plan.get().size());
        assertEquals("minecraft:spruce_planks", plan.get().get(0).itemId());
        assertEquals(6, plan.get().get(0).need());
        assertEquals("minecraft:stick", plan.get().get(1).itemId());
        assertEquals(1, plan.get().get(1).need());
    }

    @Test
    void emptyBuysWhenVaultAlreadyHasIngredients() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:oak_planks", 6);
        Predicate<String> mall = id -> "minecraft:oak_planks".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:oak_stairs", 4, recipesStairs(), mall, 5);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().isEmpty());
    }

    @Test
    void prefersVaultBonesOverBuyingPurchasableBoneMeal() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone", 9);
        Predicate<String> mall = id -> "minecraft:bone_meal".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:bone_block", 1, recipesBoneBlock(), mall, 5);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().isEmpty(), "9 bones → 27 meal covers 9 meal for 1 block; no buy");
    }

    @Test
    void buysOnlyBoneMealShortageAfterUsingVaultBones() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone", 2); // 6 meal
        Predicate<String> mall = id -> "minecraft:bone_meal".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:bone_block", 1, recipesBoneBlock(), mall, 5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:bone_meal", plan.get().get(0).itemId());
        assertEquals(3, plan.get().get(0).need()); // need 9, craft 6 from bones
    }

    @Test
    void largeBoneBlockNeedFinishesQuicklyWithPartialBones() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:bone", 100); // 300 meal → 33 blocks
        Predicate<String> mall = id -> "minecraft:bone_meal".equals(id);
        long startNs = System.nanoTime();
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:bone_block", 5000, recipesBoneBlock(), mall, 5);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:bone_meal", plan.get().get(0).itemId());
        // need 45000 meal, craft 300 from bones → buy 44700
        assertEquals(44700, plan.get().get(0).need());
        assertTrue(elapsedMs < 500L, "planner too slow for large need: " + elapsedMs + "ms");
    }

    @Test
    void deepRecipeTreeWithPurchasableLeafStaysFast() {
        // slab → planks → log; only log buyable; large need must not hang
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:spruce_log".equals(id);
        long startNs = System.nanoTime();
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:spruce_slab", 50_000, recipesSpruceSlab(), mall, 5);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:spruce_log", plan.get().get(0).itemId());
        assertTrue(elapsedMs < 500L, "deep expand too slow: " + elapsedMs + "ms");
    }

    @Test
    void spruceSlabBuysLogWhenPlanksUnpurchasable() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:spruce_log".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:spruce_slab", 6, recipesSpruceSlab(), mall, 5);
        assertTrue(plan.isPresent(), "should expand slab → planks → log");
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:spruce_log", plan.get().get(0).itemId());
        // 6 slabs via 3 planks→6 slabs needs 3 planks; 1 log→4 planks → buy 1 log
        assertEquals(1, plan.get().get(0).need());
    }

    @Test
    void andesiteSlabBuysAndesiteDirectly() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:andesite".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:andesite_slab", 6, recipesAndesiteSlab(), mall, 5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:andesite", plan.get().get(0).itemId());
        // stonecutter 1→2: need 3 andesite for 6 slabs; crafting 3→6 also needs 3
        assertEquals(3, plan.get().get(0).need());
    }

    @Test
    void earlierModChestRecipeMustNotBlockVanillaPlankBuy() {
        List<String> cursed = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            cursed.add("aaa:wood_" + i + "_planks");
        }
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if (result != null && result.startsWith("aaa:") && result.endsWith("_planks")) {
                String log = result.substring(0, result.length() - "_planks".length()) + "_log";
                return List.of(new VaultCraftRecipeView(
                        "aaa_planks_" + result,
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(log), 1)),
                        result,
                        4));
            }
            if ("minecraft:chest".equals(result)) {
                return List.of(
                        new VaultCraftRecipeView(
                                "aaa:alt_chest",
                                List.of(new VaultCraftRecipeView.Ingredient(List.copyOf(cursed), 8)),
                                "minecraft:chest",
                                1),
                        new VaultCraftRecipeView(
                                "minecraft:chest",
                                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:oak_planks"), 8)),
                                "minecraft:chest",
                                1));
            }
            return List.of();
        };
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:cobblestone", 64);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:chest", 15, recipes, id -> "minecraft:oak_planks".equals(id), 5);
        assertTrue(plan.isPresent(), "vanilla chest recipe must still buy oak planks");
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:oak_planks", plan.get().get(0).itemId());
        assertEquals(120, plan.get().get(0).need());
    }

    @Test
    void wideChestTagRecursesToVanillaLogWhenPlanksUnpurchasable() {
        List<String> planks = widePlankTag();
        Function<String, List<VaultCraftRecipeView>> recipes = wideChestRecipes(planks);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                Map.of(), "minecraft:chest", 1, recipes, id -> "minecraft:oak_log".equals(id), 5);
        assertTrue(plan.isPresent(), "must reach minecraft oak before walking 600 earlier mod woods");
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:oak_log", plan.get().get(0).itemId());
        assertEquals(2, plan.get().get(0).need());
    }

    @Test
    void tagBuysVanillaPlankAndDoesNotExpandEarlierModIds() {
        AtomicInteger modLookups = new AtomicInteger();
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if (result.startsWith("aether:") || result.startsWith("biomesoplenty:")) {
                modLookups.incrementAndGet();
                return List.of(new VaultCraftRecipeView(
                        "mod_planks_" + result,
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(result + "_log"), 1)),
                        result,
                        4));
            }
            if ("minecraft:oak_stairs".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "stairs",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(
                                "aether:skyroot_planks",
                                "biomesoplenty:fir_planks",
                                "minecraft:oak_planks"), 6)),
                        "minecraft:oak_stairs",
                        4));
            }
            return List.of();
        };
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                Map.of(),
                "minecraft:oak_stairs",
                4,
                recipes,
                id -> "minecraft:oak_planks".equals(id),
                5);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:oak_planks", plan.get().get(0).itemId());
        assertEquals(6, plan.get().get(0).need());
        assertEquals(0, modLookups.get(), "unpurchasable tag siblings must not be expanded");
    }

    @Test
    void prefersVanillaWhenModAlternativeIsAlsoPurchasable() {
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if (!"minecraft:oak_stairs".equals(result)) {
                return List.of();
            }
            return List.of(new VaultCraftRecipeView(
                    "stairs",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of(
                            "aether:skyroot_planks",
                            "minecraft:oak_planks"), 6)),
                    "minecraft:oak_stairs",
                    4));
        };
        Set<String> mall = Set.of("aether:skyroot_planks", "minecraft:oak_planks");
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                Map.of(), "minecraft:oak_stairs", 4, recipes, mall::contains, 5);
        assertTrue(plan.isPresent());
        assertEquals("minecraft:oak_planks", plan.get().get(0).itemId());
    }

    @Test
    void emptyStockBuysPurchasablePlanksWithoutRecipeLookup() {
        AtomicInteger plankLookups = new AtomicInteger();
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if ("minecraft:oak_planks".equals(result)) {
                plankLookups.incrementAndGet();
                return List.of(planksFromLog());
            }
            if ("minecraft:oak_stairs".equals(result)) {
                return List.of(stairsFromPlanks());
            }
            return List.of();
        };
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                Map.of(),
                "minecraft:oak_stairs",
                4,
                recipes,
                id -> "minecraft:oak_planks".equals(id),
                5);
        assertTrue(plan.isPresent());
        assertEquals("minecraft:oak_planks", plan.get().get(0).itemId());
        assertEquals(0, plankLookups.get(), "purchasable id must be bought before recipe scan when stock is empty");
    }

    @Test
    void wideUnpurchasableTreeStopsWithinBudget() {
        AtomicInteger lookups = new AtomicInteger();
        Function<String, List<VaultCraftRecipeView>> recipes = id -> {
            if (lookups.incrementAndGet() > 5000) {
                return List.of();
            }
            List<String> kids = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                kids.add(id + "/" + i);
            }
            return List.of(new VaultCraftRecipeView(
                    "r:" + id,
                    List.of(new VaultCraftRecipeView.Ingredient(kids, 1)),
                    id,
                    1));
        };
        long startNs = System.nanoTime();
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                Map.of(), "root", 1, recipes, id -> false, 5);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertFalse(plan.isPresent(), "over-budget search must fail closed as 无货");
        assertTrue(lookups.get() < 2000, "lookups=" + lookups.get());
        assertTrue(elapsedMs < 500L, "budget did not stop the search: " + elapsedMs + "ms");
    }

    @Test
    void smoothQuartzSlabBuysQuartzBlockViaSmelt() {
        Map<String, Integer> stock = Map.of();
        Predicate<String> mall = id -> "minecraft:quartz_block".equals(id);
        Optional<List<PurchaseIngredientLine>> plan = VaultPurchaseIngredientPlanner.plan(
                stock, "minecraft:smooth_quartz_slab", 6, recipesSmoothQuartzSlab(), mall, 5);
        assertTrue(plan.isPresent(), "should expand slab → smooth quartz → smelt quartz block");
        assertEquals(1, plan.get().size());
        assertEquals("minecraft:quartz_block", plan.get().get(0).itemId());
        // 6 slabs via stonecut 1→2 needs 3 smooth quartz; each from 1 quartz block
        assertEquals(3, plan.get().get(0).need());
    }

    private static List<String> widePlankTag() {
        List<String> planks = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            planks.add("aaa:wood_" + i + "_planks");
        }
        planks.add("minecraft:oak_planks");
        return planks;
    }

    private static Function<String, List<VaultCraftRecipeView>> wideChestRecipes(List<String> planks) {
        List<String> tag = List.copyOf(planks);
        return result -> {
            if (result != null && result.startsWith("aaa:") && result.endsWith("_planks")) {
                String log = result.substring(0, result.length() - "_planks".length()) + "_log";
                return List.of(new VaultCraftRecipeView(
                        "mod_planks_" + result,
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(log), 1)),
                        result,
                        4));
            }
            if ("minecraft:chest".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "chest",
                        List.of(new VaultCraftRecipeView.Ingredient(tag, 8)),
                        "minecraft:chest",
                        1));
            }
            if ("minecraft:oak_planks".equals(result)) {
                return List.of(planksFromLog());
            }
            return List.of();
        };
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesStairs() {
        return result -> switch (result) {
            case "minecraft:oak_planks" -> List.of(planksFromLog());
            case "minecraft:oak_stairs" -> List.of(stairsFromPlanks());
            default -> List.of();
        };
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesSign() {
        return result -> {
            if ("minecraft:spruce_sign".equals(result)) {
                return List.of(spruceSignMerged());
            }
            return List.of();
        };
    }

    private static VaultCraftRecipeView planksFromLog() {
        return new VaultCraftRecipeView(
                "planks",
                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:oak_log"), 1)),
                "minecraft:oak_planks",
                4);
    }

    private static VaultCraftRecipeView stairsFromPlanks() {
        return new VaultCraftRecipeView(
                "stairs",
                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:oak_planks"), 6)),
                "minecraft:oak_stairs",
                4);
    }

    private static VaultCraftRecipeView spruceSignMerged() {
        return new VaultCraftRecipeView(
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
                3);
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesSpruceSlab() {
        return result -> switch (result) {
            case "minecraft:spruce_planks" -> List.of(new VaultCraftRecipeView(
                    "spruce_planks",
                    List.of(new VaultCraftRecipeView.Ingredient(
                            List.of("minecraft:spruce_log", "minecraft:spruce_wood"), 1)),
                    "minecraft:spruce_planks",
                    4));
            case "minecraft:spruce_slab" -> List.of(
                    new VaultCraftRecipeView(
                            "spruce_slab_stonecut",
                            List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1)),
                            "minecraft:spruce_slab",
                            2),
                    spruceSlabMerged());
            default -> List.of();
        };
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesAndesiteSlab() {
        return result -> {
            if ("minecraft:andesite_slab".equals(result)) {
                return List.of(
                        new VaultCraftRecipeView(
                                "andesite_slab_stonecut",
                                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:andesite"), 1)),
                                "minecraft:andesite_slab",
                                2),
                        new VaultCraftRecipeView(
                                "andesite_slab_craft",
                                VaultCraftRecipes.mergeIngredients(List.of(
                                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:andesite"), 1),
                                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:andesite"), 1),
                                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:andesite"), 1))),
                                "minecraft:andesite_slab",
                                6));
            }
            return List.of();
        };
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesSmoothQuartzSlab() {
        return result -> switch (result) {
            case "minecraft:smooth_quartz" -> List.of(new VaultCraftRecipeView(
                    "smooth_quartz_smelt",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:quartz_block"), 1)),
                    "minecraft:smooth_quartz",
                    1));
            case "minecraft:smooth_quartz_slab" -> List.of(
                    new VaultCraftRecipeView(
                            "smooth_quartz_slab_stonecut",
                            List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:smooth_quartz"), 1)),
                            "minecraft:smooth_quartz_slab",
                            2),
                    new VaultCraftRecipeView(
                            "smooth_quartz_slab_craft",
                            VaultCraftRecipes.mergeIngredients(List.of(
                                    new VaultCraftRecipeView.Ingredient(List.of("minecraft:smooth_quartz"), 1),
                                    new VaultCraftRecipeView.Ingredient(List.of("minecraft:smooth_quartz"), 1),
                                    new VaultCraftRecipeView.Ingredient(List.of("minecraft:smooth_quartz"), 1))),
                            "minecraft:smooth_quartz_slab",
                            6));
            default -> List.of();
        };
    }

    private static VaultCraftRecipeView spruceSlabMerged() {
        return new VaultCraftRecipeView(
                "spruce_slab",
                VaultCraftRecipes.mergeIngredients(List.of(
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1))),
                "minecraft:spruce_slab",
                6);
    }

    private static Function<String, List<VaultCraftRecipeView>> recipesBoneBlock() {
        return result -> switch (result) {
            case "minecraft:bone_meal" -> List.of(new VaultCraftRecipeView(
                    "bone_meal",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone"), 1)),
                    "minecraft:bone_meal",
                    3));
            case "minecraft:bone_block" -> List.of(new VaultCraftRecipeView(
                    "bone_block",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:bone_meal"), 9)),
                    "minecraft:bone_block",
                    1));
            default -> List.of();
        };
    }
}
