package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Plans recursive vault crafts against abstract item counts (no world blocks).
 */
public final class VaultAutoCraftPlanner {
    public static final int DEFAULT_MAX_DEPTH = 8;

    /** One plan() call. Stops tag explosions from pinning the server thread. */
    static final int MAX_VISITS = 512;

    private VaultAutoCraftPlanner() {
    }

    public record CraftStep(String recipeId, int times) {
        public CraftStep {
            recipeId = recipeId == null ? "" : recipeId;
            times = Math.max(0, times);
        }
    }

    /**
     * @param stock            mutable working stock (copied internally for search roots)
     * @param recipesByResult  result item id → candidate recipes
     * @return ordered craft steps (dependencies first), or empty if impossible
     */
    public static Optional<List<CraftStep>> plan(
            Map<String, Integer> stock,
            String targetItemId,
            int needCount,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth) {
        if (targetItemId == null || targetItemId.isEmpty() || needCount <= 0 || recipesByResult == null) {
            return Optional.empty();
        }
        Map<String, Integer> working = copy(stock);
        List<CraftStep> steps = new ArrayList<>();
        Set<String> stack = new HashSet<>();
        SearchLimit limit = new SearchLimit(MAX_VISITS);
        if (!ensure(working, targetItemId, needCount, recipesByResult, maxDepth, 0, stack, steps, limit)) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(mergeSteps(steps)));
    }

    private static boolean ensure(
            Map<String, Integer> stock,
            String itemId,
            int need,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth,
            int depth,
            Set<String> stack,
            List<CraftStep> steps,
            SearchLimit limit) {
        int have = stock.getOrDefault(itemId, 0);
        if (have >= need) {
            return true;
        }
        if (!limit.take()) {
            return false;
        }
        int missing = need - have;
        if (depth >= maxDepth || !stack.add(itemId)) {
            return false;
        }
        try {
            List<VaultCraftRecipeView> recipes = recipesByResult.apply(itemId);
            if (recipes == null || recipes.isEmpty()) {
                return false;
            }
            List<VaultCraftRecipeView> ordered = new ArrayList<>(recipes);
            if (stock.isEmpty()) {
                ordered.sort(Comparator.comparing(VaultCraftRecipeView::id));
            } else {
                ordered.sort(Comparator
                        .comparingInt((VaultCraftRecipeView r) -> -readyIngredientSlots(stock, r))
                        .thenComparing(VaultCraftRecipeView::id));
            }

            for (VaultCraftRecipeView recipe : ordered) {
                if (recipe.resultCount() <= 0 || !itemId.equals(recipe.resultItemId())) {
                    continue;
                }
                int crafts = (missing + recipe.resultCount() - 1) / recipe.resultCount();
                if (crafts <= 0) {
                    continue;
                }
                Map<String, Integer> trial = copy(stock);
                List<CraftStep> trialSteps = new ArrayList<>();
                if (!gatherIngredients(
                        trial, recipe, crafts, recipesByResult, maxDepth, depth, stack, trialSteps, limit)) {
                    continue;
                }
                consumeIngredients(trial, recipe, crafts);
                add(trial, recipe.resultItemId(), crafts * recipe.resultCount());
                trialSteps.add(new CraftStep(recipe.id(), crafts));
                stock.clear();
                stock.putAll(trial);
                steps.addAll(trialSteps);
                return stock.getOrDefault(itemId, 0) >= need;
            }
            return false;
        } finally {
            stack.remove(itemId);
        }
    }

    private static boolean gatherIngredients(
            Map<String, Integer> stock,
            VaultCraftRecipeView recipe,
            int crafts,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth,
            int depth,
            Set<String> stack,
            List<CraftStep> steps,
            SearchLimit limit) {
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            int need = ingredient.count() * crafts;
            if (need <= 0) {
                continue;
            }
            int available = countMatching(stock, ingredient.itemIds());
            if (available >= need) {
                continue;
            }
            int stillNeed = need - available;
            if (!ensureAny(
                    stock, ingredient.itemIds(), stillNeed, recipesByResult, maxDepth, depth + 1, stack, steps, limit)) {
                return false;
            }
            if (countMatching(stock, ingredient.itemIds()) < need) {
                return false;
            }
        }
        return true;
    }

    private static boolean ensureAny(
            Map<String, Integer> stock,
            List<String> itemIds,
            int need,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth,
            int depth,
            Set<String> stack,
            List<CraftStep> steps,
            SearchLimit limit) {
        int have = countMatching(stock, itemIds);
        if (have >= need) {
            return true;
        }
        if (!limit.alive()) {
            return false;
        }
        int missing = need - have;
        if (itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        if (itemIds.size() == 1) {
            return tryEnsureId(
                    stock, itemIds.get(0), missing, recipesByResult, maxDepth, depth, stack, steps, limit);
        }
        if (stock.isEmpty()) {
            return false;
        }
        List<String> ordered = new ArrayList<>(itemIds);
        ordered.sort(Comparator.naturalOrder());
        if (tryEnsureGrouped(
                stock, ordered, missing, recipesByResult, maxDepth, depth, stack, steps, limit, true, true)
                || tryEnsureGrouped(
                stock, ordered, missing, recipesByResult, maxDepth, depth, stack, steps, limit, true, false)
                || tryEnsureGrouped(
                stock, ordered, missing, recipesByResult, maxDepth, depth, stack, steps, limit, false, true)
                || tryEnsureGrouped(
                stock, ordered, missing, recipesByResult, maxDepth, depth, stack, steps, limit, false, false)) {
            return countMatching(stock, itemIds) >= need;
        }
        return false;
    }

    /**
     * @param inStockOnly  true = already present in stock; false = absent
     * @param vanillaOnly  true = {@code minecraft:} ids; false = other namespaces
     */
    private static boolean tryEnsureGrouped(
            Map<String, Integer> stock,
            List<String> ordered,
            int missing,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth,
            int depth,
            Set<String> stack,
            List<CraftStep> steps,
            SearchLimit limit,
            boolean inStockOnly,
            boolean vanillaOnly) {
        for (String id : ordered) {
            if (id == null) {
                continue;
            }
            boolean inStock = stock.getOrDefault(id, 0) > 0;
            if (inStock != inStockOnly) {
                continue;
            }
            boolean vanilla = id.startsWith("minecraft:");
            if (vanilla != vanillaOnly) {
                continue;
            }
            if (!inStockOnly && !reachableFromStock(stock, id, recipesByResult)) {
                continue;
            }
            if (tryEnsureId(stock, id, missing, recipesByResult, maxDepth, depth, stack, steps, limit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryEnsureId(
            Map<String, Integer> stock,
            String id,
            int additionalNeed,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            int maxDepth,
            int depth,
            Set<String> stack,
            List<CraftStep> steps,
            SearchLimit limit) {
        Map<String, Integer> trial = copy(stock);
        List<CraftStep> trialSteps = new ArrayList<>();
        int target = trial.getOrDefault(id, 0) + additionalNeed;
        if (!ensure(trial, id, target, recipesByResult, maxDepth, depth, stack, trialSteps, limit)) {
            return false;
        }
        stock.clear();
        stock.putAll(trial);
        steps.addAll(trialSteps);
        return true;
    }

    private static boolean ingredientHitsStock(Map<String, Integer> stock, List<String> itemIds) {
        return countMatching(stock, itemIds) > 0;
    }

    private static boolean reachableFromStock(
            Map<String, Integer> stock,
            String itemId,
            Function<String, List<VaultCraftRecipeView>> recipesByResult) {
        if (stock.getOrDefault(itemId, 0) > 0) {
            return true;
        }
        List<VaultCraftRecipeView> recipes = recipesByResult.apply(itemId);
        if (recipes == null) {
            return false;
        }
        for (VaultCraftRecipeView recipe : recipes) {
            if (recipe.ingredients() == null || recipe.ingredients().isEmpty()) {
                continue;
            }
            boolean allHit = true;
            for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
                if (!ingredientHitsStock(stock, ingredient.itemIds())) {
                    allHit = false;
                    break;
                }
            }
            if (allHit) {
                return true;
            }
        }
        return false;
    }

    private static void consumeIngredients(Map<String, Integer> stock, VaultCraftRecipeView recipe, int crafts) {
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            int need = ingredient.count() * crafts;
            takeMatching(stock, ingredient.itemIds(), need);
        }
    }

    private static int readyIngredientSlots(Map<String, Integer> stock, VaultCraftRecipeView recipe) {
        int ready = 0;
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            if (countMatching(stock, ingredient.itemIds()) >= ingredient.count()) {
                ready++;
            }
        }
        return ready;
    }

    private static int countMatching(Map<String, Integer> stock, List<String> itemIds) {
        if (stock == null || stock.isEmpty() || itemIds == null || itemIds.isEmpty()) {
            return 0;
        }
        if (itemIds.size() > 32 && itemIds.size() > stock.size() * 4) {
            java.util.Set<String> allowed = new java.util.HashSet<>(itemIds);
            int total = 0;
            for (Map.Entry<String, Integer> entry : stock.entrySet()) {
                Integer have = entry.getValue();
                if (have != null && have > 0 && allowed.contains(entry.getKey())) {
                    total += have;
                }
            }
            return total;
        }
        int total = 0;
        for (String id : itemIds) {
            total += stock.getOrDefault(id, 0);
        }
        return total;
    }

    private static void takeMatching(Map<String, Integer> stock, List<String> itemIds, int need) {
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
    }

    private static void add(Map<String, Integer> stock, String id, int amount) {
        if (amount <= 0) {
            return;
        }
        put(stock, id, stock.getOrDefault(id, 0) + amount);
    }

    private static void put(Map<String, Integer> stock, String id, int amount) {
        if (amount <= 0) {
            stock.remove(id);
        } else {
            stock.put(id, amount);
        }
    }

    private static Map<String, Integer> copy(Map<String, Integer> stock) {
        return stock == null ? new HashMap<>() : new HashMap<>(stock);
    }

    private static List<CraftStep> mergeSteps(List<CraftStep> steps) {
        Map<String, Integer> merged = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (CraftStep step : steps) {
            if (step.times() <= 0 || step.recipeId().isEmpty()) {
                continue;
            }
            if (!merged.containsKey(step.recipeId())) {
                order.add(step.recipeId());
            }
            merged.merge(step.recipeId(), step.times(), Integer::sum);
        }
        List<CraftStep> out = new ArrayList<>(order.size());
        for (String id : order) {
            out.add(new CraftStep(id, merged.getOrDefault(id, 0)));
        }
        return out;
    }

    private static final class SearchLimit {
        private int left;

        SearchLimit(int max) {
            this.left = max;
        }

        boolean take() {
            if (left <= 0) {
                return false;
            }
            left--;
            return true;
        }

        boolean alive() {
            return left > 0;
        }
    }
}
