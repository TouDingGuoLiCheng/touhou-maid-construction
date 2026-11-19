package com.touhoumaid.construction.network;

import com.touhoumaid.construction.client.ClientScreens;
import com.touhoumaid.construction.placement.SchematicYaw;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: keep / update client ghost outline after confirm or when build starts. */
public final class SitePreviewSyncPacket {
    public enum Action {
        LOCK_ANCHORED,
        ENTER_BUILDING,
        CLEAR,
        SET_VISIBLE
    }

    private final Action action;
    private final BlockPos origin;
    private final String schematicPath;
    private final boolean flag;
    private final int yaw;
    private final int startLayer;

    public SitePreviewSyncPacket(Action action, BlockPos origin, String schematicPath, boolean flag) {
        this(action, origin, schematicPath, flag, 0, 0);
    }

    public SitePreviewSyncPacket(Action action, BlockPos origin, String schematicPath, boolean flag, int yaw) {
        this(action, origin, schematicPath, flag, yaw, 0);
    }

    public SitePreviewSyncPacket(
            Action action, BlockPos origin, String schematicPath, boolean flag, int yaw, int startLayer) {
        this.action = action;
        this.origin = origin;
        this.schematicPath = schematicPath == null ? "" : schematicPath;
        this.flag = flag;
        this.yaw = SchematicYaw.normalize(yaw);
        this.startLayer = Math.max(0, startLayer);
    }

    public static void encode(SitePreviewSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeBoolean(packet.origin != null);
        if (packet.origin != null) {
            buf.writeBlockPos(packet.origin);
        }
        buf.writeUtf(packet.schematicPath);
        buf.writeBoolean(packet.flag);
        buf.writeVarInt(packet.yaw);
        buf.writeVarInt(packet.startLayer);
    }

    public static SitePreviewSyncPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        BlockPos origin = buf.readBoolean() ? buf.readBlockPos() : null;
        return new SitePreviewSyncPacket(
                action, origin, buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SitePreviewSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreens.applyPreviewSync(packet)));
        ctx.get().setPacketHandled(true);
    }

    public Action action() {
        return action;
    }

    public BlockPos origin() {
        return origin;
    }

    public String schematicPath() {
        return schematicPath;
    }

    public boolean flag() {
        return flag;
    }

    public int yaw() {
        return yaw;
    }

    public int startLayer() {
        return startLayer;
    }
}
