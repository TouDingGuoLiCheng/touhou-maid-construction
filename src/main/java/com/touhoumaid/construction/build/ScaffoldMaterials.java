package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Scaffolding stock: terminal vault first, then maid backpack.
 * Distinct from building-material shortage — missing scaffold must not trigger direct-place.
 */
public final class ScaffoldMaterials {
    public enum Result {
        FROM_VAULT,
        FROM_MAID,
        MISSING
    }

    private ScaffoldMaterials() {
    }

    public static boolean hasAny(MaterialSource vault, EntityMaid maid) {
        return vault.hasMaterial(ScaffoldLedger.BLOCK_ID) || countInMaid(maid) > 0;
    }

    public static Result tryConsumeOne(MaterialSource vault, EntityMaid maid) {
        if (vault.tryConsume(ScaffoldLedger.BLOCK_ID)) {
            vault.markDirty();
            return Result.FROM_VAULT;
        }
        if (tryConsumeFromMaid(maid)) {
            return Result.FROM_MAID;
        }
        return Result.MISSING;
    }

    public static int countInMaid(EntityMaid maid) {
        if (maid == null) {
            return 0;
        }
        IItemHandler inv = maid.getAvailableInv(false);
        int total = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(Items.SCAFFOLDING)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean tryConsumeFromMaid(EntityMaid maid) {
        if (maid == null) {
            return false;
        }
        IItemHandler inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.is(Items.SCAFFOLDING) || stack.isEmpty()) {
                continue;
            }
            ItemStack extracted = inv.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Return scaffolding items into the terminal vault; leftover count is returned. */
    public static int depositToVault(MaterialSource vault, int count) {
        if (count <= 0) {
            return 0;
        }
        ItemStack stack = new ItemStack(Items.SCAFFOLDING, count);
        int left = MaterialVaultHelpers.deposit(vault, stack);
        vault.markDirty();
        return left;
    }

    /** Insert leftover scaffolding into maid inv (overflow dump helper). */
    public static ItemStack giveToMaid(EntityMaid maid, ItemStack stack) {
        if (maid == null || stack.isEmpty()) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), stack, false);
    }
}
