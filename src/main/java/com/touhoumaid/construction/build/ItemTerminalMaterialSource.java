package com.touhoumaid.construction.build;

import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.item.ItemTerminalData;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class ItemTerminalMaterialSource implements MaterialSource {
    private final ServerPlayer player;
    private final InteractionHand hand;
    private final String projectId;

    public ItemTerminalMaterialSource(ServerPlayer player, InteractionHand hand, String projectId) {
        this.player = player;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.projectId = projectId == null ? "" : projectId;
    }

    @Override
    public boolean hasMaterial(String blockId) {
        NonNullList<ItemStack> items = loadVault();
        return MaterialVault.hasMaterial(items, blockId);
    }

    @Override
    public boolean tryConsume(String blockId) {
        NonNullList<ItemStack> items = loadVault();
        boolean ok = MaterialVault.tryConsume(items, blockId);
        if (ok) {
            saveVault(items);
        }
        return ok;
    }

    @Override
    public void markDirty() {
        saveVault(loadVault());
    }

    private Optional<ItemStack> resolveTerminalStack() {
        return ItemTerminalData.findTerminalStack(player, hand, projectId);
    }

    private NonNullList<ItemStack> loadVault() {
        Optional<ItemStack> stack = resolveTerminalStack();
        if (stack.isEmpty()) {
            return NonNullList.withSize(
                    com.touhoumaid.construction.block.entity.ConstructionTerminalBE.SLOT_COUNT,
                    ItemStack.EMPTY);
        }
        return ItemTerminalData.loadVaultItems(stack.get(), player.getServer());
    }

    /** Exposed for scaffold reclaim deposits. */
    public NonNullList<ItemStack> loadVaultForDeposit() {
        return loadVault();
    }

    public void saveVaultAfterDeposit(NonNullList<ItemStack> items) {
        saveVault(items);
    }

    private void saveVault(NonNullList<ItemStack> items) {
        resolveTerminalStack().ifPresent(stack -> ItemTerminalData.saveVaultItems(stack, items, player.getServer()));
    }

    /** Snapshot for {@link MaterialDebugLog}. */
    net.minecraft.world.Container debugContainer() {
        net.minecraft.world.SimpleContainer view =
                new net.minecraft.world.SimpleContainer(
                        com.touhoumaid.construction.block.entity.ConstructionTerminalBE.SLOT_COUNT);
        NonNullList<ItemStack> items = loadVault();
        for (int i = 0; i < items.size(); i++) {
            view.setItem(i, items.get(i));
        }
        return view;
    }

    String debugDescribe() {
        Optional<ItemStack> stack = resolveTerminalStack();
        boolean inBoundHand = player.getItemInHand(hand).getItem() instanceof ConstructionTerminalItem;
        return "item player=" + player.getUUID()
                + " preferredHand=" + hand
                + " project=" + projectId
                + " inPreferredHand=" + inBoundHand
                + " resolved=" + (stack.isPresent() ? "yes" : "no");
    }
}
