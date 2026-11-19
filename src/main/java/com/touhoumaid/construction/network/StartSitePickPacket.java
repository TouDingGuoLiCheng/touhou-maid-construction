package com.touhoumaid.construction.network;

import com.touhoumaid.construction.client.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: close hub and enter site-pick mode on the client. */
public final class StartSitePickPacket {
    private final BlockPos terminalPos;
    private final String projectId;
    private final String schematicPath;
    private final boolean coverTerrain;
    private final boolean ignoreOverlaps;
    private final int startLayer;
    private final boolean itemMode;
    private final InteractionHand hand;

    public StartSitePickPacket(
            BlockPos terminalPos,
            String projectId,
            String schematicPath,
            boolean coverTerrain,
            boolean ignoreOverlaps,
            int startLayer,
            boolean itemMode,
            InteractionHand hand) {
        this.terminalPos = terminalPos;
        this.projectId = projectId;
        this.schematicPath = schematicPath;
        this.coverTerrain = coverTerrain;
        this.ignoreOverlaps = ignoreOverlaps;
        this.startLayer = Math.max(0, startLayer);
        this.itemMode = itemMode;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    public static void encode(StartSitePickPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.terminalPos);
        buf.writeUtf(packet.projectId);
        buf.writeUtf(packet.schematicPath);
        buf.writeBoolean(packet.coverTerrain);
        buf.writeBoolean(packet.ignoreOverlaps);
        buf.writeVarInt(packet.startLayer);
        buf.writeBoolean(packet.itemMode);
        buf.writeEnum(packet.hand);
    }

    public static StartSitePickPacket decode(FriendlyByteBuf buf) {
        return new StartSitePickPacket(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readEnum(InteractionHand.class));
    }

    public static void handle(StartSitePickPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreens.beginSitePick(packet)));
        ctx.get().setPacketHandled(true);
    }

    public BlockPos terminalPos() {
        return terminalPos;
    }

    public String projectId() {
        return projectId;
    }

    public String schematicPath() {
        return schematicPath;
    }

    public boolean coverTerrain() {
        return coverTerrain;
    }

    public boolean ignoreOverlaps() {
        return ignoreOverlaps;
    }

    public int startLayer() {
        return startLayer;
    }

    public boolean itemMode() {
        return itemMode;
    }

    public InteractionHand hand() {
        return hand;
    }
}
