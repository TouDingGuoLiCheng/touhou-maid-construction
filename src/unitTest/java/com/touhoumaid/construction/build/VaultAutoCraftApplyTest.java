package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultAutoCraftApplyTest {

    @Test
    void logToSlabOnStockMapWithoutSlotPressure() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("minecraft:spruce_log", 64);
        List<VaultAutoCraftPlanner.CraftStep> plan = List.of(
                new VaultAutoCraftPlanner.CraftStep("planks", 1),
                new VaultAutoCraftPlanner.CraftStep("slab", 1));
        assertTrue(VaultAutoCraftApply.applyStepsToStock(stock, plan, this::recipe));
        assertEquals(63, stock.get("minecraft:spruce_log").intValue());
        assertEquals(1, stock.get("minecraft:spruce_planks").intValue());
        assertEquals(6, stock.get("minecraft:spruce_slab").intValue());
    }

    @Test
    void stacksNeededCountsPartialStacks() {
        Map<String, Integer> stock = Map.of(
                "minecraft:spruce_log", 65,
                "minecraft:spruce_slab", 6);
        assertEquals(3, VaultAutoCraftApply.stacksNeeded(stock, 64));
    }

    @Test
    void trimDropsLeftoverPlanksToFitSlabs() {
        Map<String, Integer> before = Map.of("minecraft:spruce_log", 64);
        Map<String, Integer> after = new HashMap<>();
        after.put("minecraft:spruce_log", 63);
        after.put("minecraft:spruce_planks", 1);
        after.put("minecraft:spruce_slab", 6);
        // 3 stacks into 2 slots: drop leftover planks.
        Map<String, Integer> trimmed = VaultAutoCraftApply.trimNewNonTargetsToFit(
                before,
                after,
                List.of("minecraft:spruce_slab"),
                2,
                64);
        assertTrue(trimmed != null);
        assertEquals(6, trimmed.get("minecraft:spruce_slab").intValue());
        assertFalse(trimmed.containsKey("minecraft:spruce_planks"));
        assertEquals(63, trimmed.get("minecraft:spruce_log").intValue());
    }

    private Optional<VaultCraftRecipeView> recipe(String id) {
        return switch (id) {
            case "planks" -> Optional.of(new VaultCraftRecipeView(
                    "planks",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_log"), 1)),
                    "minecraft:spruce_planks",
                    4));
            case "slab" -> Optional.of(new VaultCraftRecipeView(
                    "slab",
                    List.of(new VaultCraftRecipeView.Ingredient(List.of("minecraft:spruce_planks"), 3)),
                    "minecraft:spruce_slab",
                    6));
            default -> Optional.empty();
        };
    }
}
