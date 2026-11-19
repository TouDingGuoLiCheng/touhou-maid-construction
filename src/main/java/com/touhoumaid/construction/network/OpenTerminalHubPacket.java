package com.touhoumaid.construction.network;

import com.touhoumaid.construction.client.ClientScreens;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.project.BuildProject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** S2C: open the three-page construction hub (placed block or held item). */
public final class OpenTerminalHubPacket {
    private final BlockPos pos;
    private final List<BuildProject> projects;
    private final String selectedId;
    private final List<String> schematicFiles;
    private final Map<String, Integer> vaultCounts;
    private final List<BuildJobService.MaidInfo> nearbyMaids;
    private final boolean itemMode;
    private final InteractionHand hand;

    public OpenTerminalHubPacket(
            BlockPos pos,
            List<BuildProject> projects,
            String selectedId,
            List<String> schematicFiles,
            Map<String, Integer> vaultCounts,
            List<BuildJobService.MaidInfo> nearbyMaids,
            boolean itemMode,
            InteractionHand hand) {
        this.pos = pos;
        this.projects = List.copyOf(projects);
        this.selectedId = selectedId == null ? "" : selectedId;
        this.schematicFiles = List.copyOf(schematicFiles);
        this.vaultCounts = Map.copyOf(vaultCounts == null ? Map.of() : vaultCounts);
        this.nearbyMaids = List.copyOf(nearbyMaids == null ? List.of() : nearbyMaids);
        this.itemMode = itemMode;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    public static OpenTerminalHubPacket forBlock(
            BlockPos pos,
            List<BuildProject> projects,
            String selectedId,
            List<String> schematicFiles,
            Map<String, Integer> vaultCounts,
            List<BuildJobService.MaidInfo> nearbyMaids) {
        return new OpenTerminalHubPacket(
                pos, projects, selectedId, schematicFiles, vaultCounts, nearbyMaids, false, InteractionHand.MAIN_HAND);
    }

    public static OpenTerminalHubPacket forItem(
            InteractionHand hand,
            List<BuildProject> projects,
            String selectedId,
            List<String> schematicFiles,
            Map<String, Integer> vaultCounts,
            List<BuildJobService.MaidInfo> nearbyMaids) {
        return new OpenTerminalHubPacket(
                BlockPos.ZERO, projects, selectedId, schematicFiles, vaultCounts, nearbyMaids, true, hand);
    }

    public static void encode(OpenTerminalHubPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.projects.size());
        for (BuildProject project : packet.projects) {
            buf.writeNbt(project.save());
        }
        buf.writeUtf(packet.selectedId);
        buf.writeVarInt(packet.schematicFiles.size());
        for (String name : packet.schematicFiles) {
            buf.writeUtf(name);
        }
        buf.writeVarInt(packet.vaultCounts.size());
        for (Map.Entry<String, Integer> entry : packet.vaultCounts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeBoolean(packet.itemMode);
        buf.writeEnum(packet.hand);
        buf.writeVarInt(packet.nearbyMaids.size());
        for (BuildJobService.MaidInfo maid : packet.nearbyMaids) {
            buf.writeUUID(maid.id());
            buf.writeUtf(maid.name());
            buf.writeBoolean(maid.onConstructionTask());
            buf.writeUtf(maid.materialWaitBlockId() == null ? "" : maid.materialWaitBlockId());
        }
    }

    public static OpenTerminalHubPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<BuildProject> projects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            var nbt = buf.readNbt();
            if (nbt != null) {
                projects.add(BuildProject.load(nbt));
            }
        }
        String selected = buf.readUtf();
        int fileCount = buf.readVarInt();
        List<String> files = new ArrayList<>(fileCount);
        for (int i = 0; i < fileCount; i++) {
            files.add(buf.readUtf());
        }
        int vaultSize = buf.readVarInt();
        Map<String, Integer> vault = new HashMap<>(vaultSize);
        for (int i = 0; i < vaultSize; i++) {
            vault.put(buf.readUtf(), buf.readVarInt());
        }
        boolean itemMode = buf.readBoolean();
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        int maidCount = buf.readVarInt();
        List<BuildJobService.MaidInfo> maids = new ArrayList<>(maidCount);
        for (int i = 0; i < maidCount; i++) {
            maids.add(new BuildJobService.MaidInfo(
                    buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readUtf()));
        }
        return new OpenTerminalHubPacket(pos, projects, selected, files, vault, maids, itemMode, hand);
    }

    public static void handle(OpenTerminalHubPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreens.openTerminalHub(packet)));
        ctx.get().setPacketHandled(true);
    }

    public BlockPos pos() {
        return pos;
    }

    public List<BuildProject> projects() {
        return projects;
    }

    public String selectedId() {
        return selectedId;
    }

    public List<String> schematicFiles() {
        return schematicFiles;
    }

    public Map<String, Integer> vaultCounts() {
        return vaultCounts;
    }

    public boolean itemMode() {
        return itemMode;
    }

    public InteractionHand hand() {
        return hand;
    }

    public List<BuildJobService.MaidInfo> nearbyMaids() {
        return nearbyMaids;
    }
}
