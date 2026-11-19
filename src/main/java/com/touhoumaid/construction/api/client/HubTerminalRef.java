package com.touhoumaid.construction.api.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Identifies which construction terminal the Hub is bound to (block or held item).
 */
@OnlyIn(Dist.CLIENT)
public record HubTerminalRef(BlockPos pos, boolean itemMode, InteractionHand hand) {
    public HubTerminalRef {
        if (pos == null) {
            pos = BlockPos.ZERO;
        }
        if (hand == null) {
            hand = InteractionHand.MAIN_HAND;
        }
    }
}
