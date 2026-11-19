package com.touhoumaid.construction.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Applies planned craft steps to an abstract item-count map (no container slots).
 * Avoids mid-recipe deposit failures when the vault has no empty slot for intermediates.
 */
public final class VaultAutoCraftApply {
    private VaultAutoCraftApply() {
    }

    public static boolean applyStepsToStock(
            Map<String, Integer> stock,
            List<VaultAutoCraftPlanner.CraftStep> steps,
            Function<String, Optional<VaultCraftRecipeView>> recipeById) {
        if (stock == null || steps == null || recipeById == null) {
            return false;
        }
        for (VaultAutoCraftPlanner.CraftStep step : steps) {
            if (step == null || step.times() <= 0) {
                return false;
            }
            Optional<VaultCraftRecipeView> recipe = recipeById.apply(step.recipeId());
            if (recipe.isEmpty()) {
                return false;
            }
            for (int i = 0; i < step.times(); i++) {
                if (!craftOnce(stock, recipe.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean craftOnce(Map<String, Integer> stock, VaultCraftRecipeView recipe) {
        if (stock == null || recipe == null || recipe.resultItemId().isEmpty() || recipe.resultCount() <= 0) {
            return false;
        }
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            if (countMatching(stock, ingredient.itemIds()) < ingredient.count()) {
                return false;
            }
        }
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            if (!takeMatching(stock, ingredient.itemIds(), ingredient.count())) {
                return false;
            }
        }
        add(stock, recipe.resultItemId(), recipe.resultCount());
        return true;
    }

    /** Stacks needed assuming every item stacks to {@code maxStackSize} (upper bound when unknown). */
    public static int stacksNeeded(Map<String, Integer> stock, int maxStackSize) {
        int max = Math.max(1, maxStackSize);
        int stacks = 0;
        if (stock == null) {
            return 0;
        }
        for (int count : stock.values()) {
            if (count <= 0) {
                continue;
            }
            stacks += (count + max - 1) / max;
        }
        return stacks;
    }

    private static int countMatching(Map<String, Integer> stock, List<String> itemIds) {
        int total = 0;
        for (String id : itemIds) {
            total += stock.getOrDefault(id, 0);
        }
        return total;
    }

    private static boolean takeMatching(Map<String, Integer> stock, List<String> itemIds, int need) {
        int left = need;
        for (String id : itemIds) {
            if (left <= 0) {
                break;
            }
            int have = stock.getOrDefault(id, 0);
            int take = Math.min(have, left);
            if (take > 0) {
                put(stock, id, have - take);
                left -= take;
            }
        }
        return left <= 0;
    }

    private static void add(Map<String, Integer> stock, String id, int amount) {
        if (amount <= 0 || id == null || id.isEmpty()) {
            return;
        }
        stock.merge(id, amount, Integer::sum);
    }

    private static void put(Map<String, Integer> stock, String id, int amount) {
        if (amount > 0) {
            stock.put(id, amount);
        } else {
            stock.remove(id);
        }
    }

    public static Map<String, Integer> copy(Map<String, Integer> stock) {
        return stock == null ? new HashMap<>() : new HashMap<>(stock);
    }

    /**
     * Drop newly created non-target leftovers until the map packs into {@code slots}
     * (assuming {@code maxStackSize} per item). Never reduces target counts or
     * pre-existing items below {@code before}.
     */
    public static Map<String, Integer> trimNewNonTargetsToFit(
            Map<String, Integer> before,
            Map<String, Integer> after,
            List<String> targets,
            int slots,
            int maxStackSize) {
        if (after == null) {
            return null;
        }
        Map<String, Integer> trimmed = copy(after);
        Set<String> targetSet = new HashSet<>(targets == null ? List.of() : targets);
        int guard = 0;
        while (stacksNeeded(trimmed, maxStackSize) > slots && guard++ < 10_000) {
            String victim = null;
            int victimExtra = 0;
            for (Map.Entry<String, Integer> e : trimmed.entrySet()) {
                if (targetSet.contains(e.getKey())) {
                    continue;
                }
                int baseline = before == null ? 0 : before.getOrDefault(e.getKey(), 0);
                int extra = e.getValue() - baseline;
                if (extra > victimExtra) {
                    victimExtra = extra;
                    victim = e.getKey();
                }
            }
            if (victim == null || victimExtra <= 0) {
                return null;
            }
            int next = trimmed.get(victim) - 1;
            if (next > 0) {
                trimmed.put(victim, next);
            } else {
                trimmed.remove(victim);
            }
        }
        return stacksNeeded(trimmed, maxStackSize) <= slots ? trimmed : null;
    }
}
