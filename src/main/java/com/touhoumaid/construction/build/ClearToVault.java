package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Destroy a world block without vanilla drops and deposit its item form into the
 * construction vault. Leftovers spawn at the dig site when the vault is full.
 */
public final class ClearToVault {
    private ClearToVault() {
    }

    /**
     * @return false only when the block could not be removed
     */
    public static boolean harvest(ServerLevel level, MaterialSource materials, BlockPos pos) {
        if (level == null || materials == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        ItemStack drop = dropFor(state);
        if (!level.destroyBlock(pos, false)) {
            return false;
        }
        if (drop.isEmpty()) {
            return true;
        }
        int left = MaterialVaultHelpers.deposit(materials, drop);
        materials.markDirty();
        if (left > 0) {
            ItemStack leftover = drop.copy();
            leftover.setCount(left);
            level.addFreshEntity(new ItemEntity(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    leftover));
        }
        return true;
    }

    static ItemStack dropFor(BlockState state) {
        Item item = state.getBlock().asItem();
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
