package com.touhoumaid.construction.network;

import com.touhoumaid.construction.ConstructionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ConstructionMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenTerminalHubPacket.class, id++)
                .decoder(OpenTerminalHubPacket::decode)
                .encoder(OpenTerminalHubPacket::encode)
                .consumerMainThread(OpenTerminalHubPacket::handle)
                .add();
        CHANNEL.messageBuilder(TerminalActionPacket.class, id++)
                .decoder(TerminalActionPacket::decode)
                .encoder(TerminalActionPacket::encode)
                .consumerMainThread(TerminalActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ConfirmSitePacket.class, id++)
                .decoder(ConfirmSitePacket::decode)
                .encoder(ConfirmSitePacket::encode)
                .consumerMainThread(ConfirmSitePacket::handle)
                .add();
        CHANNEL.messageBuilder(StartSitePickPacket.class, id++)
                .decoder(StartSitePickPacket::decode)
                .encoder(StartSitePickPacket::encode)
                .consumerMainThread(StartSitePickPacket::handle)
                .add();
        CHANNEL.messageBuilder(SitePreviewSyncPacket.class, id++)
                .decoder(SitePreviewSyncPacket::decode)
                .encoder(SitePreviewSyncPacket::encode)
                .consumerMainThread(SitePreviewSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(BuildProgressPacket.class, id++)
                .decoder(BuildProgressPacket::decode)
                .encoder(BuildProgressPacket::encode)
                .consumerMainThread(BuildProgressPacket::handle)
                .add();
        CHANNEL.messageBuilder(VaultScrollPacket.class, id++)
                .decoder(VaultScrollPacket::decode)
                .encoder(VaultScrollPacket::encode)
                .consumerMainThread(VaultScrollPacket::handle)
                .add();
        CHANNEL.messageBuilder(VaultSearchPacket.class, id++)
                .decoder(VaultSearchPacket::decode)
                .encoder(VaultSearchPacket::encode)
                .consumerMainThread(VaultSearchPacket::handle)
                .add();
    }
}
