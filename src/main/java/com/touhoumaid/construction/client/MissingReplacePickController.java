package com.touhoumaid.construction.client;

import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.network.TerminalActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client mode: right-click a world block to set it as the replacement for a missing schematic id.
 */
@OnlyIn(Dist.CLIENT)
public final class MissingReplacePickController {
    private static boolean active;
    private static BlockPos terminalPos = BlockPos.ZERO;
    private static boolean itemMode;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static String missingBlockId = "";

    private MissingReplacePickController() {
    }

    public static boolean isActive() {
        return active;
    }

    public static String missingBlockId() {
        return missingBlockId;
    }

    public static void deactivateQuiet() {
        active = false;
        missingBlockId = "";
    }

    public static void begin(
            BlockPos terminalPos,
            boolean itemMode,
            InteractionHand hand,
            String missingBlockId) {
        SitePickController.yieldToOtherClientMode();
        active = true;
        MissingReplacePickController.terminalPos = terminalPos == null ? BlockPos.ZERO : terminalPos;
        MissingReplacePickController.itemMode = itemMode;
        MissingReplacePickController.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        MissingReplacePickController.missingBlockId = missingBlockId == null ? "" : missingBlockId;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.touhou_maid_construction.missing_replace_pick_hint"),
                    true);
        }
    }

    public static void cancel() {
        active = false;
        missingBlockId = "";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.touhou_maid_construction.missing_replace_pick_cancel"),
                    true);
        }
    }

    public static boolean tryPick() {
        if (!active || missingBlockId.isEmpty()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR) {
            return false;
        }
        String replacementId = id.toString();
        if (itemMode) {
            ModNetwork.CHANNEL.sendToServer(
                    TerminalActionPacket.setMissingReplaceItem(hand, missingBlockId, replacementId));
        } else {
            ModNetwork.CHANNEL.sendToServer(
                    TerminalActionPacket.setMissingReplace(terminalPos, missingBlockId, replacementId));
        }
        active = false;
        missingBlockId = "";
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable(
                            "message.touhou_maid_construction.missing_replace_picked", replacementId),
                    true);
        }
        return true;
    }

    public static String hudLine() {
        if (!active) {
            return "";
        }
        return Component.translatable(
                "gui.touhou_maid_construction.missing_replace_pick_hud", missingBlockId).getString();
    }
}
