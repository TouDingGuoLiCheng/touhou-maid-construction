package com.touhoumaid.construction.inventory;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.build.MaterialVault;
import com.touhoumaid.construction.build.VaultItemAcceptPolicy;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Material vault: {@link ConstructionTerminalBE#VISIBLE_ROWS} rows visible via a page-sized
 * mirror container; the full {@link ConstructionTerminalBE#SLOT_COUNT}-slot vault stays behind
 * that window. Player inventory stays at a fixed position below.
 */
public class ConstructionTerminalMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_GAP = 14;
    private static final int HOTBAR_OFFSET = 58;

    private final Container vault;
    private final SimpleContainer pageView;
    private final Player player;
    private final ItemStack boundItem;
    private final boolean itemMode;
    private final VaultScrollState scrollState = new VaultScrollState();
    private final ContainerData scrollData;
    private boolean syncingPage;
    private String vaultQuery = "";
    /** Null when not searching; otherwise real vault indices of matches. */
    private int[] matchIndices;
    private int clientSearchActive;
    private int clientPageCount = ConstructionTerminalBE.PAGES;

    public static ConstructionTerminalMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        boolean itemMode = buf.readBoolean();
        if (itemMode) {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            ItemStack live = inv.player.getItemInHand(hand);
            if (!(live.getItem() instanceof ConstructionTerminalItem)) {
                live = ItemStack.EMPTY;
            }
            return forItem(id, inv, live);
        }
        BlockPos pos = buf.readBlockPos();
        BlockEntity be = inv.player.level().getBlockEntity(pos);
        if (be instanceof ConstructionTerminalBE terminal) {
            return forBlock(id, inv, terminal);
        }
        return forBlock(id, inv, new SimpleContainer(ConstructionTerminalBE.SLOT_COUNT));
    }

    public static ConstructionTerminalMenu forBlock(int id, Inventory playerInv, Container terminal) {
        return new ConstructionTerminalMenu(id, playerInv, terminal, ItemStack.EMPTY, false);
    }

    public static ConstructionTerminalMenu forItem(int id, Inventory playerInv, ItemStack terminalItem) {
        SimpleContainer container = createItemContainer(terminalItem, playerInv.player);
        return new ConstructionTerminalMenu(id, playerInv, container, terminalItem, true);
    }

    private ConstructionTerminalMenu(
            int id,
            Inventory playerInv,
            Container vault,
            ItemStack boundItem,
            boolean itemMode) {
        super(ModMenus.CONSTRUCTION_TERMINAL.get(), id);
        this.vault = vault;
        this.player = playerInv.player;
        this.boundItem = boundItem;
        this.itemMode = itemMode;
        checkContainerSize(vault, ConstructionTerminalBE.SLOT_COUNT);

        this.pageView = new SimpleContainer(ConstructionTerminalBE.VISIBLE_SLOT_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!syncingPage && !player.level().isClientSide) {
                    pushPageViewIntoVault();
                }
            }

            @Override
            public boolean canPlaceItem(int index, ItemStack stack) {
                return VaultItemAcceptPolicy.mayAcceptIntoVault(stack);
            }
        };

        // Read/write the field directly — never call vaultScrollRow()/setVaultScrollRow()
        // from here (that previously caused StackOverflowError on open).
        scrollData = new ContainerData() {
            @Override
            public int get(int index) {
                if (index == 1) {
                    return matchIndices != null ? 1 : 0;
                }
                if (index == 2) {
                    return serverPageCount();
                }
                return scrollState.page();
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    clientSearchActive = value;
                    return;
                }
                if (index == 2) {
                    clientPageCount = Math.max(1, value);
                    return;
                }
                scrollState.setPage(value, maxVaultScrollPage());
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        addDataSlots(scrollData);

        for (int row = 0; row < ConstructionTerminalBE.VISIBLE_ROWS; row++) {
            for (int col = 0; col < ConstructionTerminalBE.COLS; col++) {
                int local = col + row * ConstructionTerminalBE.COLS;
                this.addSlot(new Slot(pageView, local, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return visibleToVaultIndex(local) >= 0
                                && VaultItemAcceptPolicy.mayAcceptIntoVault(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return visibleToVaultIndex(local) >= 0 && super.mayPickup(player);
                    }
                });
            }
        }

        int playerInvY = 18 + ConstructionTerminalBE.VISIBLE_ROWS * 18 + PLAYER_INV_GAP;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, playerInvY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, playerInvY + HOTBAR_OFFSET) {
                @Override
                public boolean mayPickup(Player player) {
                    ItemStack stack = this.getItem();
                    if (ConstructionTerminalMenu.this.itemMode
                            && stack == ConstructionTerminalMenu.this.boundItem) {
                        return false;
                    }
                    return super.mayPickup(player);
                }
            });
        }

        if (!player.level().isClientSide) {
            pullVaultPageIntoView();
        }
    }

    /** Backing vault row offset for visible slots (page × visible rows). */
    public int vaultScrollRow() {
        return scrollState.page() * ConstructionTerminalBE.VISIBLE_ROWS;
    }

    public int vaultScrollPage() {
        return scrollState.page();
    }

    public int maxVaultScrollPage() {
        return Math.max(0, displayedPageCount() - 1);
    }

    public int displayedPageCount() {
        if (player.level().isClientSide) {
            return clientSearchActive != 0 ? clientPageCount : ConstructionTerminalBE.PAGES;
        }
        return serverPageCount();
    }

    public boolean isVaultSearching() {
        if (player.level().isClientSide) {
            return clientSearchActive != 0;
        }
        return matchIndices != null;
    }

    public void setVaultQuery(String query) {
        vaultQuery = query == null ? "" : query;
        if (player.level().isClientSide) {
            return;
        }
        rebuildMatchIndices();
        int clamped = VaultPageMirror.clampPage(scrollState.page(), maxVaultScrollPage());
        scrollState.setPage(clamped, maxVaultScrollPage());
        pullVaultPageIntoView();
    }

    private int serverPageCount() {
        if (matchIndices == null) {
            return ConstructionTerminalBE.PAGES;
        }
        return VaultSearchWindow.filterPageCount(
                matchIndices.length, ConstructionTerminalBE.VISIBLE_SLOT_COUNT);
    }

    private void rebuildMatchIndices() {
        if (VaultItemSearch.normalizeQuery(vaultQuery).isEmpty()) {
            matchIndices = null;
            return;
        }
        int n = 0;
        int[] buf = new int[ConstructionTerminalBE.SLOT_COUNT];
        for (int i = 0; i < ConstructionTerminalBE.SLOT_COUNT; i++) {
            if (VaultItemSearch.matches(vault.getItem(i), vaultQuery)) {
                buf[n++] = i;
            }
        }
        matchIndices = java.util.Arrays.copyOf(buf, n);
    }

    private int visibleToVaultIndex(int local) {
        if (matchIndices == null) {
            return VaultPageMirror.vaultIndex(
                    scrollState.page(),
                    local,
                    ConstructionTerminalBE.COLS,
                    ConstructionTerminalBE.VISIBLE_ROWS);
        }
        return VaultSearchWindow.mappedVaultIndex(
                matchIndices,
                scrollState.page(),
                local,
                ConstructionTerminalBE.VISIBLE_SLOT_COUNT);
    }

    public void scrollVault(int deltaPages) {
        setVaultPage(scrollState.page() + deltaPages);
    }

    /**
     * Jump to an absolute page. Server refreshes the 27-slot mirror from the real vault;
     * client only updates the page indicator and clears the mirror until slot packets arrive.
     */
    public void setVaultPage(int page) {
        int clamped = VaultPageMirror.clampPage(page, maxVaultScrollPage());
        if (clamped == scrollState.page()) {
            // Server re-ack: refresh mirror so a missed slot sync can repaint.
            if (!player.level().isClientSide) {
                pullVaultPageIntoView();
            }
            return;
        }
        scrollState.setPage(clamped, maxVaultScrollPage());
        if (player.level().isClientSide) {
            clearPageView();
        } else {
            pullVaultPageIntoView();
        }
    }

    private void clearPageView() {
        syncingPage = true;
        try {
            for (int i = 0; i < ConstructionTerminalBE.VISIBLE_SLOT_COUNT; i++) {
                pageView.setItem(i, ItemStack.EMPTY);
            }
        } finally {
            syncingPage = false;
        }
    }

    private void pullVaultPageIntoView() {
        syncingPage = true;
        try {
            for (int local = 0; local < ConstructionTerminalBE.VISIBLE_SLOT_COUNT; local++) {
                int vaultIndex = visibleToVaultIndex(local);
                if (vaultIndex < 0) {
                    pageView.setItem(local, ItemStack.EMPTY);
                    continue;
                }
                ItemStack stack = vault.getItem(vaultIndex);
                pageView.setItem(local, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        } finally {
            syncingPage = false;
        }
    }

    private void pushPageViewIntoVault() {
        for (int local = 0; local < ConstructionTerminalBE.VISIBLE_SLOT_COUNT; local++) {
            int vaultIndex = visibleToVaultIndex(local);
            if (vaultIndex < 0) {
                continue;
            }
            ItemStack stack = pageView.getItem(local);
            vault.setItem(vaultIndex, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        vault.setChanged();
        if (matchIndices != null) {
            rebuildMatchIndices();
            scrollState.setPage(
                    VaultPageMirror.clampPage(scrollState.page(), maxVaultScrollPage()),
                    maxVaultScrollPage());
            pullVaultPageIntoView();
        }
    }

    private static SimpleContainer createItemContainer(ItemStack terminalItem, Player player) {
        boolean[] loading = {true};
        SimpleContainer container = new SimpleContainer(ConstructionTerminalBE.SLOT_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!loading[0]) {
                    writeToItem(player, terminalItem, this);
                }
            }

            @Override
            public boolean canPlaceItem(int index, ItemStack stack) {
                return VaultItemAcceptPolicy.mayAcceptIntoVault(stack);
            }
        };
        MinecraftServer server = player instanceof ServerPlayer serverPlayer ? serverPlayer.getServer() : null;
        NonNullList<ItemStack> list = ItemTerminalData.loadVaultItems(terminalItem, server);
        for (int i = 0; i < list.size(); i++) {
            container.setItem(i, list.get(i));
        }
        loading[0] = false;
        return container;
    }

    private static void writeToItem(Player player, ItemStack terminalItem, Container container) {
        if (terminalItem.isEmpty() || !(terminalItem.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        NonNullList<ItemStack> list = NonNullList.withSize(ConstructionTerminalBE.SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < ConstructionTerminalBE.SLOT_COUNT; i++) {
            list.set(i, container.getItem(i).copy());
        }
        // #region agent log
        {
            com.google.gson.JsonObject dbg = com.touhoumaid.construction.build.VaultFillDebugLog.data();
            dbg.addProperty("stackHash", System.identityHashCode(terminalItem));
            com.touhoumaid.construction.build.VaultFillDebugLog.vaultSnapshot(dbg, "menuWrite", list);
            com.touhoumaid.construction.build.VaultFillDebugLog.log(
                    "H5", "ConstructionTerminalMenu.writeToItem", "menu_write_vault", dbg);
        }
        // #endregion
        MinecraftServer server = player instanceof ServerPlayer serverPlayer ? serverPlayer.getServer() : null;
        ItemTerminalData.saveVaultItems(terminalItem, list, server);
    }

    @Override
    public boolean stillValid(Player player) {
        if (itemMode) {
            return !boundItem.isEmpty()
                    && boundItem.getItem() instanceof ConstructionTerminalItem
                    && (player.getMainHandItem() == boundItem || player.getOffhandItem() == boundItem);
        }
        return vault.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int terminalEnd = ConstructionTerminalBE.VISIBLE_SLOT_COUNT;
            if (index < terminalEnd) {
                if (!this.moveItemStackTo(stack, terminalEnd, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Deposit into the full 2700-slot vault. moveItemStackTo(0..visible) only
                // touches the current GUI page, so a full page looked like "vault full"
                // even when later pages were empty.
                int before = stack.getCount();
                int left = MaterialVault.deposit(vault, stack.copy());
                if (left >= before) {
                    return ItemStack.EMPTY;
                }
                stack.setCount(left);
                vault.setChanged();
                if (!player.level().isClientSide) {
                    if (matchIndices != null) {
                        rebuildMatchIndices();
                        scrollState.setPage(
                                VaultPageMirror.clampPage(scrollState.page(), maxVaultScrollPage()),
                                maxVaultScrollPage());
                    }
                    pullVaultPageIntoView();
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (itemMode && !boundItem.isEmpty()) {
            writeToItem(player, boundItem, vault);
        }
    }
}
