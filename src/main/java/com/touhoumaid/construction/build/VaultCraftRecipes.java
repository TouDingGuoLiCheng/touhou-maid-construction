package com.touhoumaid.construction.build;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexed crafting / cutter recipes by result item id (shared per RecipeManager).
 * Pure merge helpers stay here so unit tests need no Minecraft recipe classes.
 */
public final class VaultCraftRecipes {
    private static RecipeManager cachedManager;
    private static VaultCraftRecipes cachedIndex;

    private final Map<String, List<VaultCraftRecipeView>> byResultItemId;
    private final Map<String, VaultCraftRecipeView> byRecipeId;

    private VaultCraftRecipes(
            Map<String, List<VaultCraftRecipeView>> byResultItemId,
            Map<String, VaultCraftRecipeView> byRecipeId) {
        this.byResultItemId = byResultItemId;
        this.byRecipeId = byRecipeId;
    }

    static VaultCraftRecipes of(
            Map<String, List<VaultCraftRecipeView>> byResultItemId,
            Map<String, VaultCraftRecipeView> byRecipeId) {
        return new VaultCraftRecipes(byResultItemId, byRecipeId);
    }

    public static VaultCraftRecipes from(RecipeManager recipes, RegistryAccess access) {
        if (recipes == null || access == null) {
            return new VaultCraftRecipes(Map.of(), Map.of());
        }
        if (cachedIndex != null && cachedManager == recipes) {
            return cachedIndex;
        }
        VaultCraftRecipes index = VaultCraftRecipeIndexer.build(recipes, access);
        cachedManager = recipes;
        cachedIndex = index;
        return index;
    }

    public List<VaultCraftRecipeView> recipesForResult(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return List.of();
        }
        return byResultItemId.getOrDefault(itemId, List.of());
    }

    public Optional<VaultCraftRecipeView> byId(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byRecipeId.get(recipeId));
    }

    /** All indexed recipes (for purchase virtual single-craft stock expansion). */
    public List<VaultCraftRecipeView> allRecipes() {
        return List.copyOf(byRecipeId.values());
    }

    /**
     * Shaped crafting lists one ingredient per grid slot. Merge identical item-id
     * sets so planners request total counts (e.g. 6 planks for a sign) instead of six
     * independent "need 1" checks that under-craft from logs/wood.
     */
    public static List<VaultCraftRecipeView.Ingredient> mergeIngredients(
            List<VaultCraftRecipeView.Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        Map<List<String>, Integer> merged = new LinkedHashMap<>();
        for (VaultCraftRecipeView.Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.itemIds().isEmpty() || ingredient.count() <= 0) {
                continue;
            }
            merged.merge(ingredient.itemIds(), ingredient.count(), Integer::sum);
        }
        List<VaultCraftRecipeView.Ingredient> out = new ArrayList<>(merged.size());
        for (Map.Entry<List<String>, Integer> e : merged.entrySet()) {
            out.add(new VaultCraftRecipeView.Ingredient(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }
}
