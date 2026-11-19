package com.touhoumaid.construction.chat;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.project.MaterialDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Chat bubbles shown to the owner during construction. */
public final class ConstructionChatBubbles {
    private static final long COOLDOWN_TICKS = 200L;
    private static final int BUBBLE_EXIST_TICKS = 150;
    private static final ResourceLocation TYPE_2_BG = ResourceLocation.fromNamespaceAndPath(
            "touhou_little_maid", "textures/entity/chat_bubble/type2.png");

    private static final String[] REQUEST_MATERIAL_KEYS = {
            "chat.touhou_maid_construction.request_material.1",
            "chat.touhou_maid_construction.request_material.2",
            "chat.touhou_maid_construction.request_material.3"
    };

    private static final String REQUEST_SCAFFOLD_KEY =
            "chat.touhou_maid_construction.request_scaffold";

    private long lastMaterialRequestTick = ChatBubbleCooldown.NEVER;
    private String lastRequestedBlockId = "";
    private long lastScaffoldRequestTick = ChatBubbleCooldown.NEVER;

    /** Ask the owner for the one block type currently missing from the vault. */
    public void requestMaterial(EntityMaid maid, String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        long now = maid.level().getGameTime();
        if (blockId.equals(lastRequestedBlockId)
                && ChatBubbleCooldown.isOnCooldown(now, lastMaterialRequestTick, COOLDOWN_TICKS)) {
            return;
        }
        lastRequestedBlockId = blockId;
        lastMaterialRequestTick = now;
        int index = maid.getRandom().nextInt(REQUEST_MATERIAL_KEYS.length);
        Component blockName = MaterialDisplay.nameForBlockId(blockId);
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.create(
                        BUBBLE_EXIST_TICKS,
                        Component.translatable(REQUEST_MATERIAL_KEYS[index], blockName),
                        TYPE_2_BG,
                        0));
    }

    /** Ask the owner for scaffolding so high places can be reached. */
    public void requestScaffold(EntityMaid maid) {
        long now = maid.level().getGameTime();
        if (ChatBubbleCooldown.isOnCooldown(now, lastScaffoldRequestTick, COOLDOWN_TICKS)) {
            return;
        }
        lastScaffoldRequestTick = now;
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.create(
                        BUBBLE_EXIST_TICKS,
                        Component.translatable(REQUEST_SCAFFOLD_KEY),
                        TYPE_2_BG,
                        0));
    }
}
