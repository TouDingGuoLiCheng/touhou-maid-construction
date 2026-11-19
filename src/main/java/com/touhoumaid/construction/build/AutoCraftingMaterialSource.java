package com.touhoumaid.construction.build;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Before reporting missing materials, optionally crafts them into the terminal vault.
 * <p>
 * Craft attempts are memoized against the current vault signature so claim scans
 * do not re-solve the same missing ids across ticks until the vault actually changes.
 */
public final class AutoCraftingMaterialSource implements MaterialSource, MaterialSourceWrapper {
    private final MaterialSource inner;
    private final AtomicBoolean enabled;
    private final Supplier<VaultCraftRecipes> recipes;
    private final Supplier<Integer> serverTick;
    private final VaultMutator mutator;
    private VaultCraftRecipes cachedRecipes;
    private int memoTick = Integer.MIN_VALUE;
    private long cachedVaultSignature = Long.MIN_VALUE;
    private final Set<String> failedForSignature = new HashSet<>();
    private int budgetTick = Integer.MIN_VALUE;
    private int ensuresThisTick;

    private AutoCraftingMaterialSource(
            MaterialSource inner,
            AtomicBoolean enabled,
            Supplier<VaultCraftRecipes> recipes,
            Supplier<Integer> serverTick,
            VaultMutator mutator) {
        this.inner = inner;
        this.enabled = enabled;
        this.recipes = recipes;
        this.serverTick = serverTick;
        this.mutator = mutator;
    }

    public static MaterialSource wrap(
            MaterialSource inner,
            AtomicBoolean enabled,
            ServerLevel level) {
        if (inner == null || level == null || enabled == null) {
            return inner;
        }
        VaultMutator mutator = mutatorFor(inner);
        if (mutator == null) {
            return inner;
        }
        Supplier<VaultCraftRecipes> recipes = () -> VaultCraftRecipes.from(
                level.getServer().getRecipeManager(),
                level.registryAccess());
        Supplier<Integer> tick = () -> level.getServer().getTickCount();
        return new AutoCraftingMaterialSource(inner, enabled, recipes, tick, mutator);
    }

    public static void setEnabled(MaterialSource source, boolean value) {
        if (source instanceof StockIndexedMaterialSource indexed) {
            setEnabled(indexed.inner(), value);
            return;
        }
        if (source instanceof AutoCraftingMaterialSource auto) {
            auto.enabled.set(value);
            if (!value) {
                auto.failedForSignature.clear();
            }
        }
    }

    @Override
    public boolean hasMaterial(String blockId) {
        if (inner.hasMaterial(blockId)) {
            return true;
        }
        if (!enabled.get() || blockId == null || blockId.isEmpty()) {
            return false;
        }
        long perfStart = BuildPerfLog.start();
        if (isKnownUncraftable(blockId)) {
            BuildPerfLog.autoCraft(blockId, true, false, false, "cached", perfStart);
            return false;
        }
        if (!noteEnsureBudget()) {
            BuildPerfLog.autoCraft(blockId, true, false, false, "budget", perfStart);
            return false;
        }
        VaultAutoCraft.EnsureResult detailed = tryEnsureDetailed(blockId);
        boolean present = inner.hasMaterial(blockId);
        if (!present) {
            rememberUncraftable(blockId);
        }
        boolean crafted = detailed == VaultAutoCraft.EnsureResult.CRAFTED || present;
        BuildPerfLog.autoCraft(blockId, false, true, crafted, detailed.name(), perfStart);
        return present;
    }

    @Override
    public boolean tryConsume(String blockId) {
        if (!inner.hasMaterial(blockId) && enabled.get()) {
            long perfStart = BuildPerfLog.start();
            if (isKnownUncraftable(blockId)) {
                BuildPerfLog.autoCraft(blockId, true, false, false, "cached", perfStart);
            } else if (!noteEnsureBudget()) {
                BuildPerfLog.autoCraft(blockId, true, false, false, "budget", perfStart);
            } else {
                VaultAutoCraft.EnsureResult detailed = tryEnsureDetailed(blockId);
                boolean present = inner.hasMaterial(blockId);
                if (!present) {
                    rememberUncraftable(blockId);
                }
                BuildPerfLog.autoCraft(
                        blockId,
                        false,
                        true,
                        detailed == VaultAutoCraft.EnsureResult.CRAFTED || present,
                        detailed.name(),
                        perfStart);
            }
        }
        return inner.tryConsume(blockId);
    }

    @Override
    public void markDirty() {
        invalidateMemo();
        inner.markDirty();
    }

    boolean isEnabled() {
        return enabled.get();
    }

    public MaterialSource inner() {
        return inner;
    }

    private boolean noteEnsureBudget() {
        int tick = serverTick.get();
        if (AutoCraftBudgetPolicy.shouldReset(budgetTick, tick)) {
            budgetTick = tick;
            ensuresThisTick = 0;
        }
        if (!AutoCraftBudgetPolicy.canEnsure(ensuresThisTick)) {
            return false;
        }
        ensuresThisTick++;
        return true;
    }

    private boolean isKnownUncraftable(String blockId) {
        return mutator.peek(vault -> {
            refreshMemo(vault);
            return failedForSignature.contains(blockId);
        });
    }

    private void rememberUncraftable(String blockId) {
        mutator.peek(vault -> {
            refreshMemo(vault);
            failedForSignature.add(blockId);
            return false;
        });
    }

    private VaultAutoCraft.EnsureResult tryEnsureDetailed(String blockId) {
        VaultCraftRecipes recipesIndex = recipeIndex();
        if (recipesIndex == null) {
            return VaultAutoCraft.EnsureResult.FAILED;
        }
        VaultAutoCraft.EnsureResult[] result = {VaultAutoCraft.EnsureResult.FAILED};
        mutator.mutate(vault -> {
            refreshMemo(vault);
            if (failedForSignature.contains(blockId)) {
                result[0] = VaultAutoCraft.EnsureResult.FAILED;
                return false;
            }
            int before = MaterialVault.countForBlock(vault, blockId);
            result[0] = VaultAutoCraft.tryEnsureDetailed(vault, blockId, recipesIndex, 1);
            int after = MaterialVault.countForBlock(vault, blockId);
            if (after > before) {
                invalidateMemo();
                refreshMemo(vault);
                inner.markDirty();
                return true;
            }
            return false;
        });
        return result[0];
    }

    private VaultCraftRecipes recipeIndex() {
        VaultCraftRecipes index = cachedRecipes;
        if (index == null) {
            index = recipes.get();
            cachedRecipes = index;
        }
        return index;
    }

    private void refreshMemo(Container vault) {
        int tick = serverTick.get();
        long signature = vaultSignature(vault);
        if (AutoCraftMemoPolicy.shouldClearFailures(
                memoTick, tick, cachedVaultSignature, signature)) {
            cachedVaultSignature = signature;
            failedForSignature.clear();
        }
        memoTick = tick;
    }

    private void invalidateMemo() {
        memoTick = Integer.MIN_VALUE;
        cachedVaultSignature = Long.MIN_VALUE;
        failedForSignature.clear();
    }

    static long vaultSignature(Container vault) {
        long sig = 0x9E3779B97F4A7C15L;
        if (vault == null) {
            return sig;
        }
        for (int i = 0; i < vault.getContainerSize(); i++) {
            ItemStack stack = vault.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            int item = BuiltInRegistries.ITEM.getId(stack.getItem());
            sig = sig * 31L + item;
            sig = sig * 31L + stack.getCount();
        }
        return sig;
    }

    private static VaultMutator mutatorFor(MaterialSource inner) {
        if (inner instanceof BlockTerminalMaterialSource block) {
            return new VaultMutator() {
                @Override
                public boolean mutate(Function<Container, Boolean> op) {
                    return Boolean.TRUE.equals(op.apply(block.container()));
                }

                @Override
                public boolean peek(Function<Container, Boolean> op) {
                    return Boolean.TRUE.equals(op.apply(block.container()));
                }
            };
        }
        if (inner instanceof ItemTerminalMaterialSource item) {
            return new VaultMutator() {
                @Override
                public boolean mutate(Function<Container, Boolean> op) {
                    NonNullList<ItemStack> items = item.loadVaultForDeposit();
                    SimpleContainer vault = new SimpleContainer(items.size());
                    for (int i = 0; i < items.size(); i++) {
                        vault.setItem(i, items.get(i));
                    }
                    boolean ok = Boolean.TRUE.equals(op.apply(vault));
                    if (ok) {
                        for (int i = 0; i < items.size(); i++) {
                            items.set(i, vault.getItem(i));
                        }
                        item.saveVaultAfterDeposit(items);
                    }
                    return ok;
                }

                @Override
                public boolean peek(Function<Container, Boolean> op) {
                    NonNullList<ItemStack> items = item.loadVaultForDeposit();
                    SimpleContainer vault = new SimpleContainer(items.size());
                    for (int i = 0; i < items.size(); i++) {
                        vault.setItem(i, items.get(i));
                    }
                    return Boolean.TRUE.equals(op.apply(vault));
                }
            };
        }
        return null;
    }

    private interface VaultMutator {
        boolean mutate(Function<Container, Boolean> op);

        boolean peek(Function<Container, Boolean> op);
    }
}
