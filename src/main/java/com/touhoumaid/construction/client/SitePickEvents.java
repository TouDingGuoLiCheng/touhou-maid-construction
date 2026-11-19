package com.touhoumaid.construction.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.blueprint.Blueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Site-pick input/HUD + textured translucent ghost preview.
 */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, value = Dist.CLIENT)
public final class SitePickEvents {
    private SitePickEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GhostBlockRenderer.release();
        MissingReplacePickController.deactivateQuiet();
        SitePickController.clear();
    }

    /** Respawn / dimension change: the client-side anchor and highlight belong to the old level. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        GhostBlockRenderer.release();
        MissingReplacePickController.deactivateQuiet();
        SitePickController.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SitePickController.tickHighlightGuard();
            SitePickController.tick();
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!SitePickController.isPicking()) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            SitePickController.togglePickFreeze();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!SitePickController.isPicking() || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_LEFT) {
            SitePickController.rotateLeft();
        } else if (event.getKey() == GLFW.GLFW_KEY_RIGHT) {
            SitePickController.rotateRight();
        }
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (MissingReplacePickController.isActive()) {
            if (event.isUseItem()) {
                if (MissingReplacePickController.tryPick()) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            } else if (event.isAttack()) {
                MissingReplacePickController.cancel();
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        if (!SitePickController.isPicking()) {
            return;
        }
        if (event.isAttack()) {
            if (SitePickController.tryConfirm()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        } else if (event.isUseItem()) {
            SitePickController.cancel();
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }
        String line = switch (GhostPreviewPolicy.hud(
                MissingReplacePickController.isActive(),
                SitePickController.isPicking(),
                SitePickController.highlightMissingBlockId())) {
            case REPLACE_PICK -> MissingReplacePickController.hudLine();
            case SITE_PICK -> SitePickController.hudLine();
            case MISSING_HIGHLIGHT -> SitePickController.highlightHudLine();
            case NONE -> "";
        };
        if (line.isEmpty()) {
            return;
        }
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x = (event.getWindow().getGuiScaledWidth() - font.width(line)) / 2;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 16;
        graphics.drawString(font, line, x, y, 0xFFF5E6D3, true);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_TRANSLUCENT_BLOCKS still has the camera pose, so ghosts stay world-locked.
        // AFTER_LEVEL's pose is identity — only subtracting camera pos makes the preview
        // float in front of the view and spin with look. Private buffers (not the shared
        // level bufferSource) avoid the mid-frame flush that punched camera-following voids.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!SitePickController.shouldRenderGhost()) {
            // Looking away mid-pick clears the anchor but the baked mesh is still valid.
            if (SitePickController.blueprint() == null) {
                GhostBlockRenderer.release();
            }
            return;
        }
        Blueprint blueprint = SitePickController.blueprint();
        BlockPos origin = SitePickController.ghostOrigin();
        if (blueprint == null || origin == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        boolean picking = SitePickController.isPicking();
        boolean cover = SitePickController.coverTerrain();
        int startLayer = SitePickController.startLayer();
        String highlight = GhostPreviewPolicy.renderHighlight(
                picking, SitePickController.highlightMissingBlockId());
        boolean forceTextured = !highlight.isEmpty();
        if (picking || forceTextured) {
            GhostBlockRenderer.render(
                    pose, blueprint, origin, cam, picking, cover,
                    SitePickController.mode() == SitePickController.Mode.BUILDING,
                    SitePickController.placementYaw(), startLayer, highlight);
        } else {
            GhostBlockRenderer.release();
            GhostOutlineRenderer.render(pose, blueprint, origin, SitePickController.placementYaw(), startLayer);
        }

        pose.popPose();
    }
}
