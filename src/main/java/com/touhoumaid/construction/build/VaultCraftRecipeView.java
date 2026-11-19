package com.touhoumaid.construction.build;

import java.util.List;

/**
 * One craftable recipe for vault auto-craft planning (crafting table or stonecutter).
 */
public final class VaultCraftRecipeView {
    private final String id;
    private final List<Ingredient> ingredients;
    private final String resultItemId;
    private final int resultCount;

    public VaultCraftRecipeView(
            String id,
            List<Ingredient> ingredients,
            String resultItemId,
            int resultCount) {
        this.id = id == null ? "" : id;
        this.ingredients = List.copyOf(ingredients == null ? List.of() : ingredients);
        this.resultItemId = resultItemId == null ? "" : resultItemId;
        this.resultCount = Math.max(0, resultCount);
    }

    public String id() {
        return id;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    public String resultItemId() {
        return resultItemId;
    }

    public int resultCount() {
        return resultCount;
    }

    /** One ingredient slot: any listed item id may satisfy {@code count} units. */
    public record Ingredient(List<String> itemIds, int count) {
        public Ingredient {
            itemIds = List.copyOf(itemIds == null ? List.of() : itemIds);
            count = Math.max(0, count);
        }
    }
}
