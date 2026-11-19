package com.touhoumaid.construction.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.network.VaultScrollPacket;
import com.touhoumaid.construction.network.VaultSearchPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Compact material vault ({@link ConstructionTerminalBE#VISIBLE_ROWS} rows) with scroll wheel;
 * player inventory remains at a standard chest-like position below.
 */
public class ConstructionTerminalScreen extends AbstractContainerScreen<ConstructionTerminalMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final int TEXTURE_ROWS = 6;
    private static final int REPEAT_ROW_V = 17 + (TEXTURE_ROWS - 1) * 18;
    private static final int SCROLLBAR_W = 6;
    private static final int SCROLLBAR_MARGIN = 4;
    private static final int SEARCH_W = 80;
    private static final int SEARCH_H = 12;

    private EditBox searchBox;

    public ConstructionTerminalScreen(
            ConstructionTerminalMenu menu,
            Inventory playerInv,
            Component title) {
        super(menu, playerInv, title);
        int rows = ConstructionTerminalBE.VISIBLE_ROWS;
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int searchX = this.leftPos + this.imageWidth - SCROLLBAR_MARGIN - SEARCH_W;
        int searchY = this.topPos + 4;
        searchBox = new EditBox(
                this.font,
                searchX,
                searchY,
                SEARCH_W,
                SEARCH_H,
                Component.translatable("gui.touhou_maid_construction.vault_search"));
        searchBox.setHint(Component.translatable("gui.touhou_maid_construction.vault_search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(true);
        searchBox.setResponder(text -> ModNetwork.CHANNEL.sendToServer(new VaultSearchPacket(text)));
        this.addRenderableWidget(searchBox);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        int rows = ConstructionTerminalBE.VISIBLE_ROWS;
        int headerRows = Math.min(rows, TEXTURE_ROWS);
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, headerRows * 18 + 17);
        for (int row = TEXTURE_ROWS; row < rows; row++) {
            graphics.blit(TEXTURE, x, y + 17 + row * 18, 0, REPEAT_ROW_V, this.imageWidth, 18);
        }
        graphics.blit(TEXTURE, x, y + rows * 18 + 17, 0, 126, this.imageWidth, 96);
        renderVaultScrollbar(graphics, x, y);
    }

    private void renderVaultScrollbar(GuiGraphics graphics, int guiX, int guiY) {
        int maxPage = menu.maxVaultScrollPage();
        if (maxPage <= 0) {
            return;
        }
        int trackX = guiX + this.imageWidth - SCROLLBAR_MARGIN - SCROLLBAR_W;
        int trackY = guiY + 18;
        int trackH = ConstructionTerminalBE.VISIBLE_ROWS * 18 - 2;
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, 0xFF3C3C3C);
        int pages = Math.max(1, menu.displayedPageCount());
        int thumbH = Math.max(8, trackH / pages);
        int thumbTravel = trackH - thumbH;
        int thumbY = trackY + (thumbTravel * menu.vaultScrollPage() / maxPage);
        graphics.fill(trackX + 1, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH, 0xFFAAAAAA);
    }

    /** Draw page hint in the title slot only — avoid stacking on the default container title. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component header = Component.translatable(
                "gui.touhou_maid_construction.vault_scroll_row",
                menu.vaultScrollPage() + 1,
                menu.displayedPageCount());
        graphics.drawString(this.font, header, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0x404040,
                false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean focused = searchBox != null && searchBox.isFocused();
        boolean escape = keyCode == InputConstants.KEY_ESCAPE;
        // Focused EditBox.canConsumeInput() is always true — must not swallow ESC.
        if (VaultSearchKeyPolicy.shouldPassEscapeToScreen(focused, escape)) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (focused) {
            boolean inventoryKey =
                    this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode);
            if (VaultSearchKeyPolicy.shouldSwallowInventoryKey(focused, inventoryKey)) {
                return true;
            }
            boolean handled = searchBox.keyPressed(keyCode, scanCode, modifiers);
            if (VaultSearchKeyPolicy.shouldConsumeFocusedSearchKey(focused, escape, handled) || handled) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.maxVaultScrollPage() > 0
                && isHovering(
                        0,
                        0,
                        this.imageWidth,
                        18 + ConstructionTerminalBE.VISIBLE_ROWS * 18,
                        mouseX,
                        mouseY)) {
            int step = -(int) Math.signum(delta);
            int target = menu.vaultScrollPage() + step;
            menu.setVaultPage(target);
            ModNetwork.CHANNEL.sendToServer(new VaultScrollPacket(menu.vaultScrollPage()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
