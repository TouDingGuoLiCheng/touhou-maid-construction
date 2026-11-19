package com.touhoumaid.construction.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Hub action button with warm log-wood styling. */
@OnlyIn(Dist.CLIENT)
public class HubWoodButton extends Button {
    private static final int WOOD = 0xFF9A7348;
    private static final int WOOD_HOVER = 0xFFB8926A;
    private static final int WOOD_BORDER = 0xFF6B4F2A;
    private static final int WOOD_DISABLED = 0xFF7A6A58;
    private static final int LABEL = 0xFFFFF6EA;
    private static final int LABEL_DISABLED = 0xFFCCC4B8;

    public HubWoodButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static HubWoodButton create(int x, int y, int width, int height, Component message, OnPress onPress) {
        return new HubWoodButton(x, y, width, height, message, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!visible) {
            return;
        }
        int bg = !active ? WOOD_DISABLED : (isHoveredOrFocused() ? WOOD_HOVER : WOOD);
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        g.fill(getX(), getY(), getX() + width, getY() + 1, WOOD_BORDER);
        g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, WOOD_BORDER);
        g.fill(getX(), getY(), getX() + 1, getY() + height, WOOD_BORDER);
        g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, WOOD_BORDER);
        int textColor = active ? LABEL : LABEL_DISABLED;
        g.drawCenteredString(
                net.minecraft.client.Minecraft.getInstance().font,
                getMessage(),
                getX() + width / 2,
                getY() + (height - 8) / 2,
                textColor);
    }
}
