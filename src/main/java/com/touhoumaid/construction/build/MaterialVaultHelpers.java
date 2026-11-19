package com.touhoumaid.construction.build;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Deposit helpers that understand both block and item terminal material sources. */
public final class MaterialVaultHelpers {
    private MaterialVaultHelpers() {
    }

    /** @return count that did not fit */
    public static int deposit(MaterialSource source, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int before = stack.getCount();
        MaterialSource terminal = StockIndexedMaterialSource.unwrapTerminal(source);
        int left;
        if (terminal instanceof BlockTerminalMaterialSource blockSource) {
            left = MaterialVault.deposit(blockSource.container(), stack.copy());
        } else if (terminal instanceof ItemTerminalMaterialSource itemSource) {
            NonNullList<ItemStack> items = itemSource.loadVaultForDeposit();
            SimpleContainer temp = new SimpleContainer(items.size());
            for (int i = 0; i < items.size(); i++) {
                temp.setItem(i, items.get(i));
            }
            left = MaterialVault.deposit(temp, stack.copy());
            for (int i = 0; i < items.size(); i++) {
                items.set(i, temp.getItem(i));
            }
            itemSource.saveVaultAfterDeposit(items);
        } else {
            return stack.getCount();
        }
        int deposited = before - left;
        MaterialStockIndex index = StockIndexedMaterialSource.indexOf(source);
        if (index != null && deposited > 0) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            index.applyDeposit(itemId, deposited);
        }
        return left;
    }

    public static Container asView(MaterialSource source) {
        MaterialSource terminal = StockIndexedMaterialSource.unwrapTerminal(source);
        if (terminal instanceof BlockTerminalMaterialSource blockSource) {
            return blockSource.container();
        }
        if (terminal instanceof ItemTerminalMaterialSource itemSource) {
            NonNullList<ItemStack> items = itemSource.loadVaultForDeposit();
            SimpleContainer view = new SimpleContainer(ConstructionTerminalBE.SLOT_COUNT);
            for (int i = 0; i < items.size(); i++) {
                view.setItem(i, items.get(i));
            }
            return view;
        }
        return new SimpleContainer(0);
    }
}
