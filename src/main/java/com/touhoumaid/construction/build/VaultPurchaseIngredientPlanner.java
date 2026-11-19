package com.touhoumaid.construction.build;

import com.touhoumaid.construction.api.PurchaseIngredientLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Expands a missing crafted item into mall-purchasable leaf ingredients.
 * Vault stock is crafted through first; purchasable leaves cover only the remaining gap.
 */
public final class VaultPurchaseIngredientPlanner {
    public static final int DEFAULT_PURCHASE_MAX_DEPTH = 5;
    /** Per shortage line. Stops tag explosions from pinning the server thread; over budget fails closed. */
    static final int MAX_VISITS = 512;

    private VaultPurchaseIngredientPlanner() {
    }

    /**
     * @param stock            vault item counts (not mutated)
     * @param isPurchasable    true when the mall can sell this item id
     * @return leaf buy lines, or empty if any required leaf cannot be bought/crafted within depth
     */
    public static Optional<List<PurchaseIngredientLine>> plan(
            Map<String, Integer> stock,
            String targetItemId,
            int needCount,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth) {
        if (targetItemId == null || targetItemId.isEmpty() || needCount <= 0
                || recipesByResult == null || isPurchasable == null || maxDepth < 0) {
            return Optional.empty();
        }
        Map<String, Integer> working = copy(stock);
        Map<String, Integer> buys = new HashMap<>();
        Set<String> stack = new HashSet<>();
        SearchLimit limit = new SearchLimit(MAX_VISITS);
        if (!ensure(
                working, buys, targetItemId, needCount, recipesByResult, isPurchasable, maxDepth, 0, stack, true, limit)) {
            return Optional.empty();
        }
        List<PurchaseIngredientLine> lines = new ArrayList<>();
        buys.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0 && e.getKey() != null && !e.getKey().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> lines.add(new PurchaseIngredientLine(e.getKey(), e.getValue())));
        return Optional.of(List.copyOf(lines));
    }

    private static boolean ensure(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            String itemId,
            int need,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            boolean allowPurchase,
            SearchLimit limit) {
        if (stock.getOrDefault(itemId, 0) >= need) {
            return true;
        }
        if (!limit.take()) {
            return false;
        }
        boolean framed = depth < maxDepth && stack.add(itemId);
        try {
            // Stock-only craft before buying, but skip it when the vault copy is empty so a
            // purchasable id is not priced by sorting every recipe tag first.
            if (framed && !stock.isEmpty()) {
                craftToward(
                        stock, buys, itemId, need, recipesByResult, isPurchasable, maxDepth, depth, stack, false, limit);
                if (stock.getOrDefault(itemId, 0) >= need) {
                    return true;
                }
            }
            if (allowPurchase && isPurchasable.test(itemId)) {
                buyRemainder(stock, buys, itemId, need);
                return true;
            }
            if (framed && allowPurchase) {
                craftToward(
                        stock, buys, itemId, need, recipesByResult, isPurchasable, maxDepth, depth, stack, true, limit);
                if (stock.getOrDefault(itemId, 0) >= need) {
                    return true;
                }
            }
        } finally {
            if (framed) {
                stack.remove(itemId);
            }
        }
        int missing = need - stock.getOrDefault(itemId, 0);
        if (missing <= 0) {
            return true;
        }
        if (allowPurchase && isPurchasable.test(itemId)) {
            buyRemainder(stock, buys, itemId, need);
            return true;
        }
        return false;
    }

    private static void buyRemainder(Map<String, Integer> stock, Map<String, Integer> buys, String itemId, int need) {
        int missingBuy = need - stock.getOrDefault(itemId, 0);
        if (missingBuy <= 0) {
            return;
        }
        buys.merge(itemId, missingBuy, Integer::sum);
        add(stock, itemId, missingBuy);
    }

    private static void craftToward(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            String itemId,
            int need,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            boolean allowPurchase,
            SearchLimit limit) {
        if (!allowPurchase && stock.isEmpty()) {
            return;
        }
        List<VaultCraftRecipeView> recipes = recipesByResult.apply(itemId);
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
        boolean progress = true;
        while (progress && stock.getOrDefault(itemId, 0) < need) {
            progress = false;
            int missing = need - stock.getOrDefault(itemId, 0);
            List<VaultCraftRecipeView> ordered = new ArrayList<>(recipes);
            if (!allowPurchase || stock.isEmpty()) {
                ordered.sort(vanillaRecipeFirst());
            } else {
                ordered.sort(Comparator
                        .comparingInt((VaultCraftRecipeView r) -> -readyIngredientSlots(stock, r))
                        .thenComparing(vanillaRecipeFirst()));
            }
            for (VaultCraftRecipeView recipe : ordered) {
                if (recipe.resultCount() <= 0 || !itemId.equals(recipe.resultItemId())) {
                    continue;
                }
                int craftsWanted = (missing + recipe.resultCount() - 1) / recipe.resultCount();
                if (craftsWanted <= 0) {
                    continue;
                }
                // Stock-only: craft at most what current counts already cover (no recursive probes).
                // Purchase-allowed: attempt full wanted once; gather may buy/craft ingredients.
                int crafts = allowPurchase
                        ? craftsWanted
                        : maxCraftsFromCurrentStock(stock, recipe, craftsWanted);
                if (crafts <= 0) {
                    continue;
                }
                Map<String, Integer> trialStock = copy(stock);
                Map<String, Integer> trialBuys = new HashMap<>(buys);
                if (!gatherIngredients(
                        trialStock,
                        trialBuys,
                        recipe,
                        crafts,
                        recipesByResult,
                        isPurchasable,
                        maxDepth,
                        depth,
                        stack,
                        allowPurchase,
                        limit)) {
                    continue;
                }
                consumeIngredients(trialStock, recipe, crafts);
                add(trialStock, recipe.resultItemId(), crafts * recipe.resultCount());
                stock.clear();
                stock.putAll(trialStock);
                buys.clear();
                buys.putAll(trialBuys);
                progress = true;
                break;
            }
        }
    }

    /** Largest craft count affordable from {@code stock} alone (no recursive ensure/buy). */
    private static int maxCraftsFromCurrentStock(
            Map<String, Integer> stock, VaultCraftRecipeView recipe, int craftsWanted) {
        int max = craftsWanted;
        for (VaultCraftRecipeView.Ingredient ingredient : recipe.ingredients()) {
            if (ingredient.count() <= 0) {
                return 0;
            }
            int available = countMatching(stock, ingredient.itemIds());
            max = Math.min(max, available / ingredient.count());
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    private static boolean gatherIngredients(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            VaultCraftRecipeView recipe,
            int crafts,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            boolean allowPurchase,
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
                    stock,
                    buys,
                    ingredient.itemIds(),
                    stillNeed,
                    recipesByResult,
                    isPurchasable,
                    maxDepth,
                    depth + 1,
                    stack,
                    allowPurchase,
                    limit)) {
                return false;
            }
            if (countMatching(stock, ingredient.itemIds()) < need) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param additionalNeed how many more matching units are required (caller already subtracted stock)
     */
    private static boolean ensureAny(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            List<String> itemIds,
            int additionalNeed,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            boolean allowPurchase,
            SearchLimit limit) {
        if (additionalNeed <= 0) {
            return true;
        }
        if (allowPurchase) {
            String chosen = preferredPurchasable(itemIds, isPurchasable);
            if (chosen != null) {
                return tryEnsureId(
                        stock, buys, chosen, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth, stack,
                        true, limit);
            }
        }
        if (!limit.alive()) {
            return false;
        }
        if (!allowPurchase) {
            if (itemIds.size() == 1) {
                return tryEnsureId(
                        stock, buys, itemIds.get(0), additionalNeed, recipesByResult, isPurchasable, maxDepth,
                        depth, stack, false, limit);
            }
            if (stock.isEmpty()) {
                return false;
            }
            List<String> ordered = orderedVanillaFirst(itemIds);
            return tryEnsureGrouped(
                    stock, buys, ordered, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth,
                    stack, limit, true, true)
                    || tryEnsureGrouped(
                    stock, buys, ordered, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth,
                    stack, limit, true, false)
                    || tryEnsureGrouped(
                    stock, buys, ordered, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth,
                    stack, limit, false, true)
                    || tryEnsureGrouped(
                    stock, buys, ordered, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth,
                    stack, limit, false, false);
        }
        for (String id : orderedVanillaFirst(itemIds)) {
            if (!limit.alive()) {
                return false;
            }
            if (tryEnsureId(
                    stock, buys, id, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth, stack,
                    true, limit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryEnsureGrouped(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            List<String> ordered,
            int additionalNeed,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            SearchLimit limit,
            boolean inStockOnly,
            boolean vanillaOnly) {
        for (String id : ordered) {
            if (id == null || !limit.alive()) {
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
            if (tryEnsureId(
                    stock, buys, id, additionalNeed, recipesByResult, isPurchasable, maxDepth, depth, stack,
                    false, limit)) {
                return true;
            }
        }
        return false;
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
                if (countMatching(stock, ingredient.itemIds()) <= 0) {
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

    /**
     * Among mall-buyable ids, prefer a {@code minecraft:} id. The planner has no unit price,
     * so non-vanilla fallbacks are the first purchasable id in sorted order.
     */
    private static Comparator<VaultCraftRecipeView> vanillaRecipeFirst() {
        return Comparator
                .comparing((VaultCraftRecipeView r) -> r.id() == null || !r.id().startsWith("minecraft:"))
                .thenComparing(r -> r.id() == null ? "" : r.id());
    }

    private static Comparator<String> vanillaIdFirst() {
        return Comparator
                .comparing((String id) -> id == null || !id.startsWith("minecraft:"))
                .thenComparing(id -> id == null ? "" : id);
    }

    private static List<String> orderedVanillaFirst(List<String> itemIds) {
        List<String> ordered = new ArrayList<>(itemIds);
        ordered.sort(vanillaIdFirst());
        return ordered;
    }

    private static String preferredPurchasable(List<String> itemIds, Predicate<String> isPurchasable) {
        if (itemIds == null || itemIds.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>(itemIds);
        ordered.sort(Comparator.naturalOrder());
        String vanilla = null;
        for (String id : ordered) {
            if (id != null && id.startsWith("minecraft:") && isPurchasable.test(id)) {
                vanilla = id;
                break;
            }
        }
        if (vanilla != null) {
            return vanilla;
        }
        for (String id : ordered) {
            if (id != null && !id.startsWith("minecraft:") && isPurchasable.test(id)) {
                return id;
            }
        }
        return null;
    }

    private static boolean tryEnsureId(
            Map<String, Integer> stock,
            Map<String, Integer> buys,
            String id,
            int additionalNeed,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth,
            int depth,
            Set<String> stack,
            boolean allowPurchase,
            SearchLimit limit) {
        Map<String, Integer> trialStock = copy(stock);
        Map<String, Integer> trialBuys = new HashMap<>(buys);
        int target = trialStock.getOrDefault(id, 0) + additionalNeed;
        if (!ensure(
                trialStock,
                trialBuys,
                id,
                target,
                recipesByResult,
                isPurchasable,
                maxDepth,
                depth,
                stack,
                allowPurchase,
                limit)) {
            return false;
        }
        stock.clear();
        stock.putAll(trialStock);
        buys.clear();
        buys.putAll(trialBuys);
        return true;
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
}
