package com.touhoumaid.construction.network;

import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: set material-vault GUI search query and refresh the visible mirror. */
public final class VaultSearchPacket {
    private static final int MAX_QUERY = 64;

    private final String query;

    public VaultSearchPacket(String query) {
        this.query = query == null ? "" : query;
    }

    public static void encode(VaultSearchPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.query, MAX_QUERY);
    }

    public static VaultSearchPacket decode(FriendlyByteBuf buf) {
        return new VaultSearchPacket(buf.readUtf(MAX_QUERY));
    }

    public static void handle(VaultSearchPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof ConstructionTerminalMenu menu && menu.stillValid(player)) {
                menu.setVaultQuery(packet.query);
                menu.broadcastFullState();
            }
        });
        context.setPacketHandled(true);
    }
}
