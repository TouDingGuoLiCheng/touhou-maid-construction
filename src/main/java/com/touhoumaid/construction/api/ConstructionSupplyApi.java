package com.touhoumaid.construction.api;

import com.touhoumaid.construction.build.MaterialVault;
import com.touhoumaid.construction.build.VaultCraftRecipeView;
import com.touhoumaid.construction.build.VaultCraftRecipes;
import com.touhoumaid.construction.build.VaultPurchaseIngredientPlanner;
import com.touhoumaid.construction.build.VaultVirtualSingleCraftStock;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.MaterialBlockPolicy;
import com.touhoumaid.construction.project.MaterialDisplay;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.project.VaultCounts;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stable hooks for companion mods (e.g. Construction DuoDuo Supply).
 * Shortage math matches the Hub material page / creative fill formula.
 */
public final class ConstructionSupplyApi {
    private ConstructionSupplyApi() {
    }

    /** {@code max(0, required - have)} — extracted for unit tests. */
    public static int shortageAmount(int required, int have) {
        return Math.max(0, required - have);
    }

    /**
     * List vault shortages for a project material summary.
     * Skips ignored and auto-free blocks; uses {@link MaterialDisplay#vaultItemIdForBlockId} as purchase identity.
     */
    public static List<ShortageLine> listShortages(
            BuildProject project,
            List<SchematicFiles.MaterialLine> lines,
            Map<String, Integer> vaultCounts) {
        List<ShortageLine> out = new ArrayList<>();
        if (project == null || lines == null || vaultCounts == null) {
            return out;
        }
        for (SchematicFiles.MaterialLine line : lines) {
            if (line == null
                    || project.isIgnoredBlock(line.blockId())
                    || MaterialBlockPolicy.isAutoFree(line.blockId())) {
                continue;
            }
            int required = project.requiredMaterialCount(line.blockId(), line.count());
            int have = VaultCounts.haveForBlockId(vaultCounts, line.blockId());
            int need = shortageAmount(required, have);
            if (need <= 0) {
                continue;
            }
            String purchaseItemId = MaterialDisplay.vaultItemIdForBlockId(line.blockId());
            out.add(new ShortageLine(line.blockId(), purchaseItemId, need));
        }
        return out;
    }

    public static List<ShortageLine> listShortages(
            BuildProject project,
            List<SchematicFiles.MaterialLine> lines,
            Container vault) {
        if (vault == null) {
            return List.of();
        }
        return listShortages(project, lines, VaultCounts.fromContainer(vault));
    }

    /** Deposit into a vault; returns count that did not fit (same as {@link MaterialVault#deposit}). */
    public static int deposit(Container vault, ItemStack stack) {
        return MaterialVault.deposit(vault, stack);
    }

    /**
     * Purchase-list only: copy of vault counts plus up to {@link VaultVirtualSingleCraftStock#DEFAULT_MAX_HOPS}
     * hops of single-ingredient crafts. Does not write the real vault.
     * Unrestricted (all single-ingredient recipes) — prefer
     * {@link #augmentPurchaseVaultStock(ServerLevel, Map, java.util.Collection)} with shortage targets.
     */
    public static Map<String, Integer> augmentPurchaseVaultStock(
            ServerLevel level, Map<String, Integer> vaultStock) {
        return augmentPurchaseVaultStock(level, vaultStock, null);
    }

    /**
     * Same as {@link #augmentPurchaseVaultStock(ServerLevel, Map)} but only virtually crafts toward
     * {@code targetItemIds} (e.g. current schematic purchase identities), so bone meal is not burned
     * into unrelated dyes before bone blocks.
     */
    public static Map<String, Integer> augmentPurchaseVaultStock(
            ServerLevel level,
            Map<String, Integer> vaultStock,
            java.util.Collection<String> targetItemIds) {
        if (vaultStock == null) {
            return new HashMap<>();
        }
        if (level == null || level.getServer() == null) {
            return new HashMap<>(vaultStock);
        }
        RecipeManager recipes = level.getServer().getRecipeManager();
        RegistryAccess access = level.registryAccess();
        VaultCraftRecipes index = VaultCraftRecipes.from(recipes, access);
        return VaultVirtualSingleCraftStock.augment(
                vaultStock,
                index.allRecipes(),
                VaultVirtualSingleCraftStock.DEFAULT_MAX_HOPS,
                targetItemIds);
    }

    /**
     * Expand an unpurchasable finished item into mall-buyable leaf ingredients (crafting table + stonecutter).
     * All-or-nothing within {@code maxDepth}; vault stock is credited before buy amounts.
     * Empty list means vault already covers the craft (nothing to buy).
     */
    public static Optional<List<PurchaseIngredientLine>> listPurchaseIngredients(
            String targetItemId,
            int need,
            Map<String, Integer> vaultStock,
            Function<String, List<VaultCraftRecipeView>> recipesByResult,
            Predicate<String> isPurchasable,
            int maxDepth) {
        return VaultPurchaseIngredientPlanner.plan(
                vaultStock, targetItemId, need, recipesByResult, isPurchasable, maxDepth);
    }

    /** Same as {@link #listPurchaseIngredients} with server recipe index and default purchase depth 5. */
    public static Optional<List<PurchaseIngredientLine>> listPurchaseIngredients(
            ServerLevel level,
            String targetItemId,
            int need,
            Map<String, Integer> vaultStock,
            Predicate<String> isPurchasable) {
        if (level == null) {
            return Optional.empty();
        }
        RecipeManager recipes = level.getServer().getRecipeManager();
        RegistryAccess access = level.registryAccess();
        VaultCraftRecipes index = VaultCraftRecipes.from(recipes, access);
        return listPurchaseIngredients(
                targetItemId,
                need,
                vaultStock,
                index::recipesForResult,
                isPurchasable,
                VaultPurchaseIngredientPlanner.DEFAULT_PURCHASE_MAX_DEPTH);
    }
}
