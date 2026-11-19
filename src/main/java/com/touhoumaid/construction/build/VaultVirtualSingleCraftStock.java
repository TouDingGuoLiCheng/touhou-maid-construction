package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Purchase-only virtual stock: simulate single-ingredient crafts on a vault count copy
 * (consume inputs, add outputs) without depositing into the real vault.
 * Each hop only considers recipes whose inputs intersect current stock keys (BMC4-safe).
 * When target result ids are provided, only crafts that help reach those targets (closure
 * over up to {@code maxHops} reverse single-ingredient steps) — avoids burning bone meal
 * into white dye etc. before bone blocks.
 */
public final class VaultVirtualSingleCraftStock {
    public static final int DEFAULT_MAX_HOPS = 3;

    private VaultVirtualSingleCraftStock() {
    }

    /**
     * Unrestricted augment (all single-ingredient recipes). Prefer
     * {@link #augment(Map, Iterable, int, Collection)} with shortage targets for purchase lists.
     */
    public static Map<String, Integer> augment(
            Map<String, Integer> stock,
            Iterable<VaultCraftRecipeView> recipes,
            int maxHops) {
        return augment(stock, recipes, maxHops, null);
    }

    /**
     * @param stock           real vault counts (not mutated); null treated as empty
     * @param recipes         candidate recipes; only those with exactly one merged ingredient apply
     * @param maxHops         how many expand passes (≤0 returns a copy of stock)
     * @param targetResultIds if non-null and non-empty, only craft toward these result item ids
     *                        (plus single-ingredient precursors within {@code maxHops}).
     *                        Those target ids themselves are never spent as ingredients, so a
     *                        shortage the player just bought still counts toward that line.
     */
    public static Map<String, Integer> augment(
            Map<String, Integer> stock,
            Iterable<VaultCraftRecipeView> recipes,
            int maxHops,
            Collection<String> targetResultIds) {
        Map<String, Integer> working = copy(stock);
        if (recipes == null || maxHops <= 0) {
            return working;
        }
        List<VaultCraftRecipeView> single = new ArrayList<>();
        for (VaultCraftRecipeView recipe : recipes) {
            if (isSingleIngredient(recipe)) {
                single.add(recipe);
            }
        }
        single.sort(Comparator.comparing(VaultCraftRecipeView::id));
        Set<String> allowedResults = usefulResultIds(single, targetResultIds, maxHops);
        Set<String> keepOnHand = directShortageIds(targetResultIds);
        for (int hop = 0; hop < maxHops; hop++) {
            boolean progressed = false;
            for (VaultCraftRecipeView recipe : single) {
                if (allowedResults != null && !allowedResults.contains(recipe.resultItemId())) {
                    continue;
                }
                VaultCraftRecipeView.Ingredient ingredient = recipe.ingredients().get(0);
                // Skip when no listed input is present — avoids scanning huge tag lists every hop.
                if (!anyPresent(working, ingredient.itemIds(), keepOnHand)) {
                    continue;
                }
                int available = countMatching(working, ingredient.itemIds(), keepOnHand);
                int perCraft = ingredient.count();
                if (perCraft <= 0 || recipe.resultCount() <= 0 || available < perCraft) {
                    continue;
                }
                int crafts = available / perCraft;
                if (crafts <= 0) {
                    continue;
                }
                takeMatching(working, ingredient.itemIds(), crafts * perCraft, keepOnHand);
                add(working, recipe.resultItemId(), crafts * recipe.resultCount());
                progressed = true;
            }
            if (!progressed) {
                break;
            }
        }
        return working;
    }

    /**
     * @return null when unrestricted; otherwise result ids that may be virtually crafted
     */
    static Set<String> usefulResultIds(
            List<VaultCraftRecipeView> singleRecipes,
            Collection<String> targetResultIds,
            int maxHops) {
        if (targetResultIds == null || targetResultIds.isEmpty() || maxHops <= 0) {
            return null;
        }
        Set<String> useful = new HashSet<>();
        for (String id : targetResultIds) {
            if (id != null && !id.isEmpty()) {
                useful.add(id);
            }
        }
        if (useful.isEmpty()) {
            return null;
        }
        // Reverse closure: results that feed into useful targets within maxHops.
        for (int hop = 0; hop < maxHops; hop++) {
            boolean grew = false;
            for (VaultCraftRecipeView recipe : singleRecipes) {
                if (!useful.contains(recipe.resultItemId())) {
                    continue;
                }
                for (String inputId : recipe.ingredients().get(0).itemIds()) {
                    if (inputId != null && !inputId.isEmpty() && useful.add(inputId)) {
                        grew = true;
                    }
                }
            }
            if (!grew) {
                break;
            }
        }
        return useful;
    }

    /** Original shortage purchase ids. Null means unrestricted (may spend anything). */
    private static Set<String> directShortageIds(Collection<String> targetResultIds) {
        if (targetResultIds == null || targetResultIds.isEmpty()) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        for (String id : targetResultIds) {
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    private static boolean anyPresent(Map<String, Integer> stock, List<String> itemIds, Set<String> exclude) {
        for (String id : itemIds) {
            if (exclude != null && exclude.contains(id)) {
                continue;
            }
            if (stock.getOrDefault(id, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    static boolean isSingleIngredient(VaultCraftRecipeView recipe) {
        return recipe != null
                && recipe.resultCount() > 0
                && recipe.resultItemId() != null
                && !recipe.resultItemId().isEmpty()
                && recipe.ingredients() != null
                && recipe.ingredients().size() == 1
                && recipe.ingredients().get(0).count() > 0
                && !recipe.ingredients().get(0).itemIds().isEmpty();
    }

    private static int countMatching(Map<String, Integer> stock, List<String> itemIds, Set<String> exclude) {
        int total = 0;
        for (String id : itemIds) {
            if (exclude != null && exclude.contains(id)) {
                continue;
            }
            total += stock.getOrDefault(id, 0);
        }
        return total;
    }

    private static void takeMatching(
            Map<String, Integer> stock, List<String> itemIds, int need, Set<String> exclude) {
        int left = need;
        for (String id : itemIds) {
            if (left <= 0) {
                break;
            }
            if (exclude != null && exclude.contains(id)) {
                continue;
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
        if (id == null || id.isEmpty() || amount <= 0) {
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
