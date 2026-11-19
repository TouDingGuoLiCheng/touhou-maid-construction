package com.touhoumaid.construction.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.touhoumaid.construction.blueprint.Blueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Schematic ghosts with real block models. The mesh is baked once in anchor-local
 * space and resubmitted each frame; conflict red is a vertex recolor, not a rebuild.
 */
public final class GhostBlockRenderer {
    /** While site-picking, refresh conflict red about every two seconds if the world changes. */
    static final int CONFLICT_REFRESH_TICKS = 40;

    private static final GhostPreviewMesh MESH = new GhostPreviewMesh();

    private GhostBlockRenderer() {
    }

    public static void render(
            PoseStack pose,
            Blueprint blueprint,
            BlockPos origin,
            Vec3 cam,
            boolean picking,
            boolean cover,
            boolean building,
            int yaw) {
        render(pose, blueprint, origin, cam, picking, cover, building, yaw, 0, null);
    }

    public static void render(
            PoseStack pose,
            Blueprint blueprint,
            BlockPos origin,
            Vec3 cam,
            boolean picking,
            boolean cover,
            boolean building,
            int yaw,
            int startLayer) {
        render(pose, blueprint, origin, cam, picking, cover, building, yaw, startLayer, null);
    }

    public static void render(
            PoseStack pose,
            Blueprint blueprint,
            BlockPos origin,
            Vec3 cam,
            boolean picking,
            boolean cover,
            boolean building,
            int yaw,
            int startLayer,
            String highlightMissingBlockId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || blueprint == null || origin == null) {
            return;
        }
        String highlight = highlightMissingBlockId == null ? "" : highlightMissingBlockId;
        int worldBucket = picking ? (int) (mc.level.getGameTime() / CONFLICT_REFRESH_TICKS) : 0;
        GhostMeshRefresh.Geometry geometry = GhostMeshRefresh.geometry(
                System.identityHashCode(blueprint),
                yaw,
                startLayer,
                highlight,
                building,
                SitePickController.placedGeneration());
        GhostMeshRefresh.Tint tint = GhostMeshRefresh.tint(origin.asLong(), cover, picking, building, worldBucket);
        if (MESH.shouldSkip(blueprint, geometry)) {
            return;
        }
        GhostMeshRefresh.Action action = GhostMeshRefresh.choose(
                MESH.hasMesh() && MESH.blueprint() == blueprint,
                MESH.geometry(),
                geometry,
                MESH.tint(),
                tint);
        try {
            switch (action) {
                case REBUILD -> MESH.rebuild(
                        mc, blueprint, origin, yaw, startLayer, highlight, building, cover, geometry, tint);
                case RETINT -> MESH.retint(mc.level, origin, cover, tint);
                case DRAW -> {
                }
            }
            MESH.draw(pose, origin);
        } catch (RuntimeException ex) {
            MESH.fail(blueprint, geometry, ex);
        }
    }

    public static void release() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(GhostBlockRenderer::release);
            return;
        }
        MESH.close();
    }
}
