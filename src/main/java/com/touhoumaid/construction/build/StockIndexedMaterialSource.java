package com.touhoumaid.construction.build;

import com.touhoumaid.construction.project.MaterialBlockPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MaterialSource wrapper that answers {@link #hasMaterial} from an incremental
 * {@link MaterialStockIndex} instead of scanning the full vault every call.
 */
public final class StockIndexedMaterialSource implements MaterialSource, MaterialSourceWrapper {
    private final MaterialSource inner;
    private final MaterialStockIndex index = new MaterialStockIndex();
    private boolean forceRebuild;

    private StockIndexedMaterialSource(MaterialSource inner) {
        this.inner = inner;
    }

    public static MaterialSource wrap(MaterialSource inner) {
        if (inner == null || inner instanceof StockIndexedMaterialSource) {
            return inner;
        }
        return new StockIndexedMaterialSource(inner);
    }

    public static MaterialStockIndex indexOf(MaterialSource source) {
        MaterialSource cursor = source;
        while (cursor instanceof MaterialSourceWrapper wrapper) {
            if (cursor instanceof StockIndexedMaterialSource indexed) {
                return indexed.index;
            }
            cursor = wrapper.inner();
        }
        return null;
    }

    public static void requestRebuild(MaterialSource source) {
        MaterialSource cursor = source;
        while (cursor instanceof MaterialSourceWrapper wrapper) {
            if (cursor instanceof StockIndexedMaterialSource indexed) {
                indexed.forceRebuild = true;
                return;
            }
            cursor = wrapper.inner();
        }
    }

    public static MaterialSource unwrapTerminal(MaterialSource source) {
        MaterialSource cursor = source;
        while (cursor instanceof MaterialSourceWrapper wrapper) {
            cursor = wrapper.inner();
        }
        return cursor;
    }

    public MaterialSource inner() {
        return inner;
    }

    public MaterialStockIndex index() {
        return index;
    }

    @Override
    public boolean hasMaterial(String blockId) {
        if (MaterialBlockPolicy.isAutoFree(blockId)) {
            return true;
        }
        ensureReady();
        List<String> matches = MaterialVault.matchItemIds(blockId);
        if (index.hasForBlockId(blockId, matches)) {
            return true;
        }
        if (index.isBlockUnavailable(blockId)) {
            return false;
        }
        if (!anyStock(matches) && !mayAutoCraft()) {
            index.markBlockUnavailable(blockId);
            return false;
        }
        boolean ok = inner.hasMaterial(blockId);
        if (ok) {
            // Craft may change many slots — rebuild lazily on the next ensureReady, not mid-scan.
            forceRebuild = true;
        } else if (StockIndexUnavailablePolicy.shouldMarkUnavailable(mayAutoCraft())) {
            index.markBlockUnavailable(blockId);
        }
        return ok;
    }

    private boolean anyStock(List<String> matches) {
        for (String id : matches) {
            if (index.countOf(id) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean mayAutoCraft() {
        return inner instanceof AutoCraftingMaterialSource auto && auto.isEnabled();
    }

    @Override
    public boolean tryConsume(String blockId) {
        if (MaterialBlockPolicy.isAutoFree(blockId)) {
            return true;
        }
        ensureReady();
        List<String> matches = MaterialVault.matchItemIds(blockId);
        String predicted = null;
        for (String id : matches) {
            if (index.countOf(id) > 0) {
                predicted = id;
                break;
            }
        }
        boolean ok = inner.tryConsume(blockId);
        if (ok) {
            if (predicted != null) {
                index.applyConsume(predicted, 1);
            } else {
                syncMatchCounts(matches);
            }
        } else {
            if (StockIndexUnavailablePolicy.shouldMarkUnavailable(mayAutoCraft())) {
                index.markBlockUnavailable(blockId);
            }
            syncMatchCounts(matches);
        }
        return ok;
    }

    @Override
    public void markDirty() {
        forceRebuild = true;
        inner.markDirty();
    }

    private void ensureReady() {
        if (index.isReady() && !forceRebuild) {
            return;
        }
        rebuildFromVault();
        forceRebuild = false;
    }

    void rebuildFromVault() {
        Container view = MaterialVaultHelpers.asView(unwrapTerminal(inner));
        Map<String, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < view.getContainerSize(); slot++) {
            ItemStack stack = view.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        index.rebuildFromCounts(counts);
    }

    private void syncMatchCounts(List<String> matchItemIds) {
        Container view = MaterialVaultHelpers.asView(unwrapTerminal(inner));
        for (String itemId : matchItemIds) {
            int actual = 0;
            for (int slot = 0; slot < view.getContainerSize(); slot++) {
                ItemStack stack = view.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                    actual += stack.getCount();
                }
            }
            int have = index.countOf(itemId);
            if (actual > have) {
                index.applyDeposit(itemId, actual - have);
            } else if (actual < have) {
                index.applyConsume(itemId, have - actual);
            }
        }
    }
}
