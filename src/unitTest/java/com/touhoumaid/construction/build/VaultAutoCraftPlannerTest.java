package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultAutoCraftPlannerTest {

    @Test
    void logToPlanksOverflowWhenNeedOne() {
        Map<String, Integer> stock = Map.of("minecraft:oak_log", 1);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if ("minecraft:oak_planks".equals(result)) {
                return List.of(planksFromLog());
            }
            return List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:oak_planks", 1, recipes, 8);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().get(0).times());
    }

    @Test
    void recursesLogToStairs() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:oak_log", 2);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> switch (result) {
            case "minecraft:oak_planks" -> List.of(planksFromLog());
            case "minecraft:oak_stairs" -> List.of(stairsFromPlanks());
            default -> List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:oak_stairs", 1, recipes, 8);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("planks")));
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("stairs")));
    }

    @Test
    void prefersDirectRecipeOverRecursive() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:oak_planks", 6);
        stock.put("minecraft:oak_log", 64);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if ("minecraft:oak_stairs".equals(result)) {
                return List.of(
                        new VaultCraftRecipeView(
                                "stairs_from_log_fake",
                                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:oak_log"), 1)),
                                "minecraft:oak_stairs",
                                1),
                        stairsFromPlanks());
            }
            if ("minecraft:oak_planks".equals(result)) {
                return List.of(planksFromLog());
            }
            return List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:oak_stairs", 1, recipes, 8);
        assertTrue(plan.isPresent());
        assertEquals(1, plan.get().size());
        assertEquals("stairs", plan.get().get(0).recipeId());
    }

    @Test
    void cycleFails() {
        Map<String, Integer> stock = Map.of();
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if ("a".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "a_from_b",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("b"), 1)),
                        "a",
                        1));
            }
            if ("b".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "b_from_a",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("a"), 1)),
                        "b",
                        1));
            }
            return List.of();
        };
        assertFalse(VaultAutoCraftPlanner.plan(stock, "a", 1, recipes, 8).isPresent());
    }

    @Test
    void depthCapFails() {
        Map<String, Integer> stock = Map.of();
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            int n = Integer.parseInt(result.substring(1));
            String need = "i" + (n + 1);
            return List.of(new VaultCraftRecipeView(
                    "r" + n,
                    List.of(new VaultCraftRecipeView.Ingredient(List.of(need), 1)),
                    result,
                    1));
        };
        assertFalse(VaultAutoCraftPlanner.plan(stock, "i0", 1, recipes, 2).isPresent());
    }

    /**
     * Shaped recipes list the same item once per grid slot. Unmerged views under-craft
     * (one log → 4 planks) then cannot pay 6 planks for a sign.
     */
    @Test
    void spruceWoodToSignNeedsMergedPlankCount() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_wood", 8);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> switch (result) {
            case "minecraft:spruce_planks" -> List.of(sprucePlanksFromWood());
            case "minecraft:stick" -> List.of(sticksFromSprucePlanks());
            case "minecraft:spruce_sign" -> List.of(spruceSignMerged());
            default -> List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:spruce_sign", 1, recipes, 8);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("spruce_planks")));
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("spruce_sign")));
    }

    @Test
    void spruceWoodToSlabViaMergedPlanks() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_wood", 2);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> switch (result) {
            case "minecraft:spruce_planks" -> List.of(sprucePlanksFromWood());
            case "minecraft:spruce_slab" -> List.of(spruceSlabMerged());
            default -> List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:spruce_slab", 1, recipes, 8);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("spruce_planks")));
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("spruce_slab")));
        int plankCrafts = plan.get().stream()
                .filter(s -> s.recipeId().equals("spruce_planks"))
                .mapToInt(VaultAutoCraftPlanner.CraftStep::times)
                .sum();
        assertEquals(1, plankCrafts);
    }

    @Test
    void unmergedSignSlotsUnderCraftFromOneWood() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_wood", 8);
        Function<String, List<VaultCraftRecipeView>> recipes = result -> switch (result) {
            case "minecraft:spruce_planks" -> List.of(sprucePlanksFromWood());
            case "minecraft:stick" -> List.of(sticksFromSprucePlanks());
            case "minecraft:spruce_sign" -> List.of(spruceSignUnmergedSlots());
            default -> List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:spruce_sign", 1, recipes, 8);
        // Planner may still return a plan, but plank crafts stay at 1× (4 planks) — not enough to apply.
        if (plan.isPresent()) {
            int plankCrafts = plan.get().stream()
                    .filter(s -> s.recipeId().equals("spruce_planks"))
                    .mapToInt(VaultAutoCraftPlanner.CraftStep::times)
                    .sum();
            assertTrue(plankCrafts < 2, "unmerged slots must under-request planks");
        }
    }

    @Test
    void mergeIngredientsSumsSameItemSlots() {
        List<VaultCraftRecipeView.Ingredient> merged = VaultCraftRecipes.mergeIngredients(List.of(
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                new VaultCraftRecipeView.Ingredient(List.of("minecraft:stick"), 1)));
        assertEquals(2, merged.size());
        assertEquals(6, merged.get(0).count());
        assertEquals(List.of("minecraft:spruce_planks"), merged.get(0).itemIds());
        assertEquals(1, merged.get(1).count());
        assertEquals(List.of("minecraft:stick"), merged.get(1).itemIds());
    }

    @Test
    void tagUsesOakLogAndDoesNotExpandEarlierModPlanks() {
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
            if ("minecraft:oak_planks".equals(result)) {
                return List.of(planksFromLog());
            }
            if ("minecraft:oak_stairs".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "stairs_tag",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(
                                "aether:skyroot_planks",
                                "biomesoplenty:fir_planks",
                                "minecraft:oak_planks"), 6)),
                        "minecraft:oak_stairs",
                        4));
            }
            return List.of();
        };
        Map<String, Integer> stock = Map.of("minecraft:oak_log", 2);
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(stock, "minecraft:oak_stairs", 4, recipes, 8);
        assertTrue(plan.isPresent());
        assertTrue(plan.get().stream().anyMatch(s -> s.recipeId().equals("planks")));
        assertEquals(0, modLookups.get(), "unreachable mod planks must not be expanded");
    }

    @Test
    void emptyStockTagDoesNotQueryModPlankRecipes() {
        AtomicInteger modLookups = new AtomicInteger();
        Function<String, List<VaultCraftRecipeView>> recipes = result -> {
            if (result.startsWith("aether:")) {
                modLookups.incrementAndGet();
                return List.of(new VaultCraftRecipeView(
                        "mod_planks",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of("aether:skyroot_log"), 1)),
                        result,
                        4));
            }
            if ("minecraft:oak_planks".equals(result)) {
                return List.of(planksFromLog());
            }
            if ("minecraft:oak_stairs".equals(result)) {
                return List.of(new VaultCraftRecipeView(
                        "stairs_tag",
                        List.of(new VaultCraftRecipeView.Ingredient(List.of(
                                "aether:skyroot_planks",
                                "minecraft:oak_planks"), 6)),
                        "minecraft:oak_stairs",
                        4));
            }
            return List.of();
        };
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(Map.of(), "minecraft:oak_stairs", 4, recipes, 8);
        assertFalse(plan.isPresent());
        assertEquals(0, modLookups.get());
    }

    @Test
    void wideUnreachableTreeStopsWithinBudget() {
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
        Optional<List<VaultAutoCraftPlanner.CraftStep>> plan =
                VaultAutoCraftPlanner.plan(Map.of(), "root", 1, recipes, 8);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertFalse(plan.isPresent());
        assertTrue(lookups.get() < 2000, "lookups=" + lookups.get());
        assertTrue(elapsedMs < 500L, "budget did not stop the search: " + elapsedMs + "ms");
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

    private static VaultCraftRecipeView sprucePlanksFromWood() {
        return new VaultCraftRecipeView(
                "spruce_planks",
                List.of(new VaultCraftRecipeView.Ingredient(
                        List.of("minecraft:spruce_log", "minecraft:spruce_wood"), 1)),
                "minecraft:spruce_planks",
                4);
    }

    private static VaultCraftRecipeView sticksFromSprucePlanks() {
        return new VaultCraftRecipeView(
                "sticks",
                List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 2)),
                "minecraft:stick",
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

    private static VaultCraftRecipeView spruceSignUnmergedSlots() {
        return new VaultCraftRecipeView(
                "spruce_sign",
                List.of(
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 1),
                        new VaultCraftRecipeView.Ingredient(List.of("minecraft:stick"), 1)),
                "minecraft:spruce_sign",
                3);
    }
}
