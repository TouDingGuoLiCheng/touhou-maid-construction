package com.touhoumaid.construction.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;

/**
 * Lightweight wireframe preview for anchored / building modes.
 * Uses a private immediate buffer so the shared level buffer is never flushed mid-frame.
 */
public final class GhostOutlineRenderer {
    private GhostOutlineRenderer() {
    }

    public static void render(PoseStack pose, Blueprint blueprint, BlockPos origin, int yaw) {
        render(pose, blueprint, origin, yaw, 0);
    }

    public static void render(PoseStack pose, Blueprint blueprint, BlockPos origin, int yaw, int startLayer) {
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        BlueprintPlacement.Bounds bounds = BlueprintPlacement.placedBounds(origin, footprint, yaw, startLayer);
        MultiBufferSource.BufferSource buffers =
                MultiBufferSource.immediate(new BufferBuilder(256));
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2.0F);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        try {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(
                    pose,
                    lines,
                    bounds.minX(),
                    bounds.minY(),
                    bounds.minZ(),
                    bounds.maxX(),
                    bounds.maxY(),
                    bounds.maxZ(),
                    0.45F,
                    0.72F,
                    0.95F,
                    0.9F);
            buffers.endBatch();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.lineWidth(1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
