package com.touhoumaid.construction.build;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link VaultCraftRecipes} from the live recipe manager.
 * Kept separate so unit tests can use {@link VaultCraftRecipes#mergeIngredients} without MC recipe stubs.
 */
final class VaultCraftRecipeIndexer {
    private VaultCraftRecipeIndexer() {
    }

    static VaultCraftRecipes build(RecipeManager recipes, RegistryAccess access) {
        Map<String, List<VaultCraftRecipeView>> byResult = new HashMap<>();
        Map<String, VaultCraftRecipeView> byId = new HashMap<>();
        for (CraftingRecipe recipe : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            add(byResult, byId, toView(recipe, access));
        }
        for (StonecutterRecipe recipe : recipes.getAllRecipesFor(RecipeType.STONECUTTING)) {
            add(byResult, byId, toViewSingleInput(recipe, access));
        }
        for (Recipe<?> recipe : recipes.getAllRecipesFor(RecipeType.SMELTING)) {
            add(byResult, byId, toViewSingleInput(recipe, access));
        }
        for (Recipe<?> recipe : recipes.getAllRecipesFor(RecipeType.BLASTING)) {
            add(byResult, byId, toViewSingleInput(recipe, access));
        }
        // Sawmill / Corail woodcutting etc.: single-input cutters beyond vanilla stonecutter.
        for (Recipe<?> recipe : recipes.getRecipes()) {
            if (recipe instanceof CraftingRecipe || recipe instanceof StonecutterRecipe) {
                continue;
            }
            if (isExtraSingleInputCutter(recipe)) {
                add(byResult, byId, toViewSingleInput(recipe, access));
            }
        }
        return VaultCraftRecipes.of(byResult, byId);
    }

    private static void add(
            Map<String, List<VaultCraftRecipeView>> byResult,
            Map<String, VaultCraftRecipeView> byId,
            VaultCraftRecipeView view) {
        if (view == null || view.id().isEmpty() || view.resultItemId().isEmpty() || view.resultCount() <= 0) {
            return;
        }
        byId.put(view.id(), view);
        byResult.computeIfAbsent(view.resultItemId(), k -> new ArrayList<>()).add(view);
    }

    private static VaultCraftRecipeView toView(CraftingRecipe recipe, RegistryAccess access) {
        ItemStack result = recipe.getResultItem(access);
        if (result.isEmpty()) {
            return null;
        }
        List<VaultCraftRecipeView.Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<String> ids = itemIds(ingredient);
            if (ids.isEmpty()) {
                return null;
            }
            ingredients.add(new VaultCraftRecipeView.Ingredient(ids, 1));
        }
        ingredients = VaultCraftRecipes.mergeIngredients(ingredients);
        if (ingredients.isEmpty()) {
            return null;
        }
        ResourceLocation id = recipe.getId();
        return new VaultCraftRecipeView(
                id == null ? "" : id.toString(),
                ingredients,
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                result.getCount());
    }

    private static VaultCraftRecipeView toViewSingleInput(Recipe<?> recipe, RegistryAccess access) {
        ItemStack result = recipe.getResultItem(access);
        if (result.isEmpty()) {
            return null;
        }
        List<Ingredient> list = recipe.getIngredients();
        Ingredient input = null;
        for (Ingredient ingredient : list) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            if (input != null) {
                return null;
            }
            input = ingredient;
        }
        if (input == null) {
            return null;
        }
        List<String> ids = itemIds(input);
        if (ids.isEmpty()) {
            return null;
        }
        ResourceLocation id = recipe.getId();
        return new VaultCraftRecipeView(
                id == null ? "" : id.toString(),
                List.of(new VaultCraftRecipeView.Ingredient(ids, singleInputCount(recipe))),
                BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                Math.max(1, result.getCount()));
    }

    private static boolean isExtraSingleInputCutter(Recipe<?> recipe) {
        if (recipe == null) {
            return false;
        }
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (typeId == null) {
            return false;
        }
        String path = typeId.getPath();
        return path.contains("woodcutting")
                || path.contains("woodcutter")
                || path.contains("sawmill")
                || path.contains("sawing");
    }

    private static int singleInputCount(Recipe<?> recipe) {
        try {
            var method = recipe.getClass().getMethod("getInputCount");
            Object value = method.invoke(recipe);
            if (value instanceof Integer count && count > 0) {
                return count;
            }
        } catch (ReflectiveOperationException ignored) {
            // Most cutters have no such method.
        }
        return 1;
    }

    private static List<String> itemIds(Ingredient ingredient) {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }
}
