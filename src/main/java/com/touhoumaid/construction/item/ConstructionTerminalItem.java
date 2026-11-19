package com.touhoumaid.construction.item;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import com.touhoumaid.construction.network.TerminalActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Portable construction terminal. The hub opens on right-click; this item can never place the block.
 */
public class ConstructionTerminalItem extends BlockItem {
    public ConstructionTerminalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            TerminalActionPacket.openItemHub(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Placement is removed: the item never becomes a block, so a misclick cannot wipe the vault. */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
            TerminalActionPacket.openItemHub(serverPlayer, context.getHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    public static void openItemMenu(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        NetworkHooks.openScreen(player, new ItemProvider(stack, hand), buf -> {
            buf.writeBoolean(true);
            buf.writeEnum(hand);
        });
    }

    private record ItemProvider(ItemStack stack, InteractionHand hand) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("block.touhou_maid_construction.construction_terminal");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
            ItemStack live = player.getItemInHand(hand);
            if (!(live.getItem() instanceof ConstructionTerminalItem)) {
                live = stack;
            }
            return ConstructionTerminalMenu.forItem(id, playerInv, live);
        }
    }

    public static CompoundTag ensureInventoryTag(ItemStack stack) {
        return ItemTerminalData.ensureTag(stack);
    }

    public static int slotCount() {
        return ConstructionTerminalBE.SLOT_COUNT;
    }

    @Override
    public CompoundTag getShareTag(ItemStack stack) {
        return ItemTerminalData.stripItemsFromShareTag(super.getShareTag(stack));
    }
}
