package com.touhoumaid.construction.network;

import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: jump material-vault GUI to an absolute page and refresh the visible mirror. */
public final class VaultScrollPacket {
    private final int page;

    public VaultScrollPacket(int page) {
        this.page = page;
    }

    public static void encode(VaultScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.page);
    }

    public static VaultScrollPacket decode(FriendlyByteBuf buf) {
        return new VaultScrollPacket(buf.readVarInt());
    }

    public static void handle(VaultScrollPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof ConstructionTerminalMenu menu && menu.stillValid(player)) {
                menu.setVaultPage(packet.page);
                // Push the fresh 27-slot mirror immediately; do not wait for the next tick.
                menu.broadcastFullState();
            }
        });
        context.setPacketHandled(true);
    }
}
