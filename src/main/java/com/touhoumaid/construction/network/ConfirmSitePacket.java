package com.touhoumaid.construction.network;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.LitematicReader;
import com.touhoumaid.construction.build.BuildJobKey;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.placement.ConflictScanner;
import com.touhoumaid.construction.placement.PlacementRules;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.SchematicFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

/** C2S: confirm site anchor after client conflict-free preview. */
public final class ConfirmSitePacket {
    private final BlockPos terminalPos;
    private final String projectId;
    private final BlockPos clicked;
    private final boolean coverTerrain;
    private final boolean ignoreOverlaps;
    private final boolean itemMode;
    private final InteractionHand hand;
    private final int yaw;

    public ConfirmSitePacket(
            BlockPos terminalPos,
            String projectId,
            BlockPos clicked,
            boolean coverTerrain,
            boolean ignoreOverlaps,
            boolean itemMode,
            InteractionHand hand,
            int yaw) {
        this.terminalPos = terminalPos;
        this.projectId = projectId;
        this.clicked = clicked;
        this.coverTerrain = coverTerrain;
        this.ignoreOverlaps = ignoreOverlaps;
        this.itemMode = itemMode;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        this.yaw = yaw;
    }

    public static void encode(ConfirmSitePacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.terminalPos);
        buf.writeUtf(packet.projectId);
        buf.writeBlockPos(packet.clicked);
        buf.writeBoolean(packet.coverTerrain);
        buf.writeBoolean(packet.ignoreOverlaps);
        buf.writeBoolean(packet.itemMode);
        buf.writeEnum(packet.hand);
        buf.writeVarInt(packet.yaw);
    }

    public static ConfirmSitePacket decode(FriendlyByteBuf buf) {
        return new ConfirmSitePacket(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readEnum(InteractionHand.class),
                buf.readVarInt());
    }

    public static void handle(ConfirmSitePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.itemMode) {
                handleItem(player, packet);
            } else {
                handleBlock(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleBlock(ServerPlayer player, ConfirmSitePacket packet) {
        BlockEntity be = player.level().getBlockEntity(packet.terminalPos);
        if (!(be instanceof ConstructionTerminalBE terminal) || !terminal.stillValid(player)) {
            return;
        }
        BuildProject project = BuildProject.find(terminal.getProjects(), packet.projectId).orElse(null);
        if (project == null) {
            return;
        }
        applyConfirm(player, project, packet);
        terminal.setSelectedProjectId(project.id());
        terminal.setChanged();
        TerminalActionPacket.reopen(player, terminal);
    }

    private static void handleItem(ServerPlayer player, ConfirmSitePacket packet) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        List<BuildProject> projects = ItemTerminalData.getProjects(stack);
        BuildProject project = BuildProject.find(projects, packet.projectId).orElse(null);
        if (project == null) {
            return;
        }
        applyConfirm(player, project, packet);
        ItemTerminalData.saveProjects(stack, projects, project.id());
        TerminalActionPacket.reopenItem(player, packet.hand);
    }

    private static void applyConfirm(ServerPlayer player, BuildProject project, ConfirmSitePacket packet) {
        BuildJobKey key = BuildJobService.jobKeyFor(
                player, project, packet.terminalPos, packet.itemMode, packet.hand);
        BuildJobService.onSiteRelocated(player, project, key);
        try {
            Blueprint blueprint = LitematicReader.read(SchematicFiles.resolveSchematic(project.schematicPath()));
            BlockPos origin = PlacementRules.originForClick(packet.clicked, packet.coverTerrain);
            ConflictScanner.Result result = ConflictScanner.scan(
                    player.level(), blueprint, origin, packet.coverTerrain, packet.ignoreOverlaps,
                    packet.yaw, project.startLayer());
            project.setCoverTerrain(packet.coverTerrain);
            project.setIgnoreOverlaps(packet.ignoreOverlaps);
            project.setPlacementYaw(packet.yaw);
            project.setConflictCount(result.conflictCount());
            if (result.hasConflicts()) {
                project.setSiteConfirmed(false);
                project.setAnchor(null);
                player.sendSystemMessage(result.blockingMessage(packet.coverTerrain, packet.ignoreOverlaps));
            } else {
                project.setAnchor(origin);
                project.setSiteConfirmed(true);
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_maid_construction.site_ok_server",
                        origin.getX(), origin.getY(), origin.getZ()));
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new SitePreviewSyncPacket(
                                SitePreviewSyncPacket.Action.LOCK_ANCHORED,
                                origin,
                                project.schematicPath(),
                                packet.coverTerrain,
                                packet.yaw,
                                project.startLayer()));
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Confirm site failed: " + e.getMessage()));
        }
    }
}
