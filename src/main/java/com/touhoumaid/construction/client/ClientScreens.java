package com.touhoumaid.construction.client;

import com.touhoumaid.construction.network.OpenTerminalHubPacket;
import com.touhoumaid.construction.network.StartSitePickPacket;
import net.minecraft.client.Minecraft;

/** Client-only screen open helpers (keeps server classpath clean). */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openTerminalHub(OpenTerminalHubPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ConstructionHubScreen hub) {
            hub.applyPacket(packet);
            return;
        }
        mc.setScreen(new ConstructionHubScreen(packet));
    }

    public static void beginSitePick(StartSitePickPacket packet) {
        SitePickController.begin(
                packet.terminalPos(),
                packet.projectId(),
                packet.schematicPath(),
                packet.coverTerrain(),
                packet.ignoreOverlaps(),
                packet.startLayer(),
                packet.itemMode(),
                packet.hand());
    }

    public static void applyPreviewSync(com.touhoumaid.construction.network.SitePreviewSyncPacket packet) {
        SitePickController.setStartLayer(packet.startLayer());
        switch (packet.action()) {
            case LOCK_ANCHORED -> {
                if (packet.origin() == null) {
                    return;
                }
                SitePickController.ensureLoaded(packet.schematicPath(), packet.flag());
                SitePickController.lockAnchored(packet.origin(), packet.yaw());
            }
            case ENTER_BUILDING -> {
                if (packet.origin() != null) {
                    SitePickController.ensureLoaded(packet.schematicPath(), packet.flag());
                    SitePickController.lockAnchored(packet.origin(), packet.yaw());
                }
                SitePickController.enterBuilding();
            }
            case CLEAR -> SitePickController.clear();
            case SET_VISIBLE -> SitePickController.setPreviewVisible(packet.flag());
        }
    }

    public static void applyBuildProgress(com.touhoumaid.construction.network.BuildProgressPacket packet) {
        if (packet.placedPos() != null && !packet.placedPos().equals(net.minecraft.core.BlockPos.ZERO)) {
            SitePickController.markPlaced(packet.placedPos());
        }
        if (packet.done()) {
            SitePickController.markDone();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ConstructionHubScreen hub) {
            hub.applyBuildProgress(packet);
        }
    }
}
