package com.touhoumaid.construction.build;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Virtually crafts missing vault items (crafting table + stonecutter) into the container. */
public final class VaultAutoCraft {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_NO_PLAN = new HashSet<>();

    private VaultAutoCraft() {
    }

    public enum EnsureResult {
        ALREADY_HAS,
        CRAFTED,
        NO_RECIPE,
        NO_PLAN,
        APPLY_FAILED,
        VAULT_FULL,
        FAILED
    }

    /**
     * Ensures the vault can supply {@code need} units matching {@code blockId}.
     * Multi-step crafts run on an abstract stock map then pack back into the vault so
     * intermediate planks do not need a free slot mid-recipe. Leftover non-target outputs
     * from this craft may be discarded when the vault cannot hold them.
     */
    public static boolean tryEnsure(Container vault, String blockId, VaultCraftRecipes recipes, int need) {
        EnsureResult result = tryEnsureDetailed(vault, blockId, recipes, need);
        return result == EnsureResult.CRAFTED || result == EnsureResult.ALREADY_HAS;
    }

    /** Like {@link #tryEnsure} but returns why crafting did not produce stock. */
    public static EnsureResult tryEnsureDetailed(
            Container vault, String blockId, VaultCraftRecipes recipes, int need) {
        if (vault == null || recipes == null || need <= 0) {
            return EnsureResult.FAILED;
        }
        if (MaterialVault.countForBlock(vault, blockId) >= need) {
            return EnsureResult.ALREADY_HAS;
        }
        List<String> targets = MaterialVault.matchItemIds(blockId);
        if (targets.isEmpty()) {
            return EnsureResult.NO_RECIPE;
        }
        for (String targetItemId : targets) {
            if (countItem(vault, targetItemId) >= need) {
                return EnsureResult.ALREADY_HAS;
            }
        }
        boolean anyRecipe = false;
        for (String targetItemId : targets) {
            if (!recipes.recipesForResult(targetItemId).isEmpty()) {
                anyRecipe = true;
                break;
            }
        }
        if (!anyRecipe) {
            return EnsureResult.NO_RECIPE;
        }
        compact(vault);
        Map<String, Integer> before = stockOf(vault);
        EnsureResult last = EnsureResult.NO_PLAN;
        for (String targetItemId : targets) {
            if (before.getOrDefault(targetItemId, 0) >= need) {
                return EnsureResult.ALREADY_HAS;
            }
            if (recipes.recipesForResult(targetItemId).isEmpty()) {
                continue;
            }
            Optional<List<VaultAutoCraftPlanner.CraftStep>> plan = VaultAutoCraftPlanner.plan(
                    before,
                    targetItemId,
                    need,
                    recipes::recipesForResult,
                    VaultAutoCraftPlanner.DEFAULT_MAX_DEPTH);
            if (plan.isEmpty()) {
                last = EnsureResult.NO_PLAN;
                logNoPlanOnce(blockId, targetItemId, before, recipes);
                continue;
            }
            Map<String, Integer> after = VaultAutoCraftApply.copy(before);
            if (!VaultAutoCraftApply.applyStepsToStock(after, plan.get(), recipes::byId)) {
                last = EnsureResult.APPLY_FAILED;
                continue;
            }
            if (countMatching(after, targets) < need) {
                last = EnsureResult.APPLY_FAILED;
                continue;
            }
            Map<String, Integer> toCommit = VaultAutoCraftApply.trimNewNonTargetsToFit(
                    before, after, targets, vault.getContainerSize(), 64);
            if (toCommit == null || countMatching(toCommit, targets) < need) {
                last = EnsureResult.VAULT_FULL;
                continue;
            }
            if (!commitStock(vault, toCommit)) {
                last = EnsureResult.VAULT_FULL;
                continue;
            }
            return MaterialVault.countForBlock(vault, blockId) >= need
                    ? EnsureResult.CRAFTED
                    : EnsureResult.FAILED;
        }
        return last;
    }

    /** Merge stacks toward lower slots to free empty slots for craft outputs. */
    public static void compact(Container vault) {
        if (vault == null) {
            return;
        }
        commitStock(vault, stockOf(vault));
    }

    /**
     * Replace vault contents with {@code stock}. Fails (and leaves vault unchanged) when the
     * packed item stacks cannot fit.
     */
    static boolean commitStock(Container vault, Map<String, Integer> stock) {
        if (vault == null) {
            return false;
        }
        List<ItemStack> packed = packStacks(stock);
        if (packed == null) {
            return false;
        }
        if (packed.size() > vault.getContainerSize()) {
            return false;
        }
        for (int i = 0; i < vault.getContainerSize(); i++) {
            vault.setItem(i, i < packed.size() ? packed.get(i) : ItemStack.EMPTY);
        }
        return true;
    }

    private static List<ItemStack> packStacks(Map<String, Integer> stock) {
        if (stock == null || stock.isEmpty()) {
            return List.of();
        }
        List<ItemStack> packed = new ArrayList<>();
        for (Map.Entry<String, Integer> e : stock.entrySet()) {
            int count = e.getValue() == null ? 0 : e.getValue();
            if (count <= 0 || e.getKey() == null || e.getKey().isEmpty()) {
                continue;
            }
            ResourceLocation loc = ResourceLocation.tryParse(e.getKey());
            if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
                return null;
            }
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item == Items.AIR) {
                return null;
            }
            int max = Math.max(1, item.getMaxStackSize());
            int left = count;
            while (left > 0) {
                int n = Math.min(max, left);
                packed.add(new ItemStack(item, n));
                left -= n;
            }
        }
        return packed;
    }

    private static int countMatching(Map<String, Integer> stock, List<String> itemIds) {
        int total = 0;
        for (String id : itemIds) {
            total += stock.getOrDefault(id, 0);
        }
        return total;
    }

    private static int countItem(Container vault, String itemId) {
        int total = 0;
        for (int slot = 0; slot < vault.getContainerSize(); slot++) {
            ItemStack stack = vault.getItem(slot);
            if (!stack.isEmpty()
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static Map<String, Integer> stockOf(Container vault) {
        Map<String, Integer> stock = new HashMap<>();
        for (int slot = 0; slot < vault.getContainerSize(); slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            stock.merge(id, stack.getCount(), Integer::sum);
        }
        return stock;
    }

    /** One-shot diagnosis for NO_PLAN (e.g. spruce_slab with logs in vault). */
    private static void logNoPlanOnce(
            String blockId,
            String targetItemId,
            Map<String, Integer> stock,
            VaultCraftRecipes recipes) {
        if (!ConstructionDebug.enabled() || blockId == null || !LOGGED_NO_PLAN.add(blockId)) {
            return;
        }
        String plankId = guessPlankId(targetItemId);
        int targetRecipes = recipes.recipesForResult(targetItemId).size();
        int plankRecipes = plankId.isEmpty() ? -1 : recipes.recipesForResult(plankId).size();
        String plankSample = "";
        if (!plankId.isEmpty()) {
            List<VaultCraftRecipeView> planks = recipes.recipesForResult(plankId);
            if (!planks.isEmpty()) {
                VaultCraftRecipeView first = planks.get(0);
                plankSample = " plankRecipe0=" + first.id()
                        + " ings=" + summarizeIngredients(first);
            }
        }
        List<VaultCraftRecipeView> targets = recipes.recipesForResult(targetItemId);
        String targetSample = "";
        if (!targets.isEmpty()) {
            VaultCraftRecipeView first = targets.get(0);
            targetSample = " targetRecipe0=" + first.id()
                    + " ings=" + summarizeIngredients(first);
        }
        LOGGER.warn(
                "[TMC-Craft] NO_PLAN blockId={} target={} stockTarget={} spruce_log={} spruce_wood={} "
                        + "spruce_planks={} targetRecipes={} plankId={} plankRecipes={}{}{}",
                blockId,
                targetItemId,
                stock.getOrDefault(targetItemId, 0),
                stock.getOrDefault("minecraft:spruce_log", 0),
                stock.getOrDefault("minecraft:spruce_wood", 0),
                stock.getOrDefault("minecraft:spruce_planks", 0),
                targetRecipes,
                plankId,
                plankRecipes,
                targetSample,
                plankSample);
    }

    private static String guessPlankId(String targetItemId) {
        if (targetItemId == null || targetItemId.isEmpty()) {
            return "";
        }
        if (targetItemId.endsWith("_slab")
                || targetItemId.endsWith("_stairs")
                || targetItemId.endsWith("_fence")
                || targetItemId.endsWith("_sign")
                || targetItemId.endsWith("_door")
                || targetItemId.endsWith("_trapdoor")
                || targetItemId.endsWith("_button")
                || targetItemId.endsWith("_pressure_plate")
                || targetItemId.endsWith("_boat")
                || targetItemId.endsWith("_hanging_sign")) {
            int slash = targetItemId.indexOf(':');
            String path = slash >= 0 ? targetItemId.substring(slash + 1) : targetItemId;
            int under = path.indexOf('_');
            if (under <= 0) {
                return "";
            }
            String wood = path.substring(0, under);
            String ns = slash >= 0 ? targetItemId.substring(0, slash) : "minecraft";
            return ns + ":" + wood + "_planks";
        }
        return "";
    }

    private static String summarizeIngredients(VaultCraftRecipeView recipe) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < recipe.ingredients().size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            VaultCraftRecipeView.Ingredient ing = recipe.ingredients().get(i);
            sb.append(ing.count()).append('x');
            List<String> ids = ing.itemIds();
            if (ids.size() <= 3) {
                sb.append(ids);
            } else {
                sb.append(ids.subList(0, 3)).append("...+").append(ids.size() - 3);
            }
        }
        return sb.append(']').toString();
    }
}
