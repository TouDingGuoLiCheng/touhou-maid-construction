package com.touhoumaid.construction.network;

import com.touhoumaid.construction.build.BuildJobKey;
import com.touhoumaid.construction.client.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Supplier;

/** S2C: block placed / job progress for ghost outline + hub refresh. */
public final class BuildProgressPacket {
    private final BuildJobKey key;
    private final BlockPos placedPos;
    private final int placedCount;
    private final int totalBlocks;
    private final int consumed;
    private final boolean done;
    private final boolean paused;
    private final String pauseReason;
    private final Map<UUID, String> maidMaterialWaits;
    private final Map<String, Integer> placedByBlockId;
    private final List<String> placementFailures;

    public BuildProgressPacket(
            BuildJobKey key,
            BlockPos placedPos,
            int placedCount,
            int totalBlocks,
            int consumed,
            boolean done,
            boolean paused,
            String pauseReason,
            Map<UUID, String> maidMaterialWaits,
            Map<String, Integer> placedByBlockId,
            List<String> placementFailures) {
        this.key = key;
        this.placedPos = placedPos == null ? BlockPos.ZERO : placedPos.immutable();
        this.placedCount = placedCount;
        this.totalBlocks = totalBlocks;
        this.consumed = consumed;
        this.done = done;
        this.paused = paused;
        this.pauseReason = pauseReason == null ? "" : pauseReason;
        this.maidMaterialWaits = Map.copyOf(maidMaterialWaits == null ? Map.of() : maidMaterialWaits);
        this.placedByBlockId = Map.copyOf(placedByBlockId == null ? Map.of() : placedByBlockId);
        this.placementFailures = List.copyOf(placementFailures == null ? List.of() : placementFailures);
    }

    public static void encode(BuildProgressPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.key.projectId());
        buf.writeBlockPos(packet.key.terminalPos());
        buf.writeBoolean(packet.key.itemMode());
        buf.writeEnum(packet.key.hand());
        buf.writeUUID(packet.key.ownerId());
        buf.writeBlockPos(packet.placedPos);
        buf.writeVarInt(packet.placedCount);
        buf.writeVarInt(packet.totalBlocks);
        buf.writeVarInt(packet.consumed);
        buf.writeBoolean(packet.done);
        buf.writeBoolean(packet.paused);
        buf.writeUtf(packet.pauseReason);
        buf.writeVarInt(packet.maidMaterialWaits.size());
        for (Map.Entry<UUID, String> entry : packet.maidMaterialWaits.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
        buf.writeVarInt(packet.placedByBlockId.size());
        for (Map.Entry<String, Integer> entry : packet.placedByBlockId.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
        buf.writeVarInt(packet.placementFailures.size());
        for (String line : packet.placementFailures) {
            buf.writeUtf(line);
        }
    }

    public static BuildProgressPacket decode(FriendlyByteBuf buf) {
        BuildJobKey key = new BuildJobKey(
                buf.readUtf(),
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readEnum(InteractionHand.class),
                buf.readUUID());
        BlockPos placed = buf.readBlockPos();
        int placedCount = buf.readVarInt();
        int total = buf.readVarInt();
        int consumed = buf.readVarInt();
        boolean done = buf.readBoolean();
        boolean paused = buf.readBoolean();
        String pauseReason = buf.readUtf();
        int waitCount = buf.readVarInt();
        Map<UUID, String> waits = new HashMap<>(waitCount);
        for (int i = 0; i < waitCount; i++) {
            waits.put(buf.readUUID(), buf.readUtf());
        }
        int placedTypeCount = buf.readVarInt();
        Map<String, Integer> placedByBlockId = new HashMap<>(placedTypeCount);
        for (int i = 0; i < placedTypeCount; i++) {
            placedByBlockId.put(buf.readUtf(), buf.readVarInt());
        }
        int failureCount = buf.readVarInt();
        List<String> placementFailures = new ArrayList<>(failureCount);
        for (int i = 0; i < failureCount; i++) {
            placementFailures.add(buf.readUtf());
        }
        return new BuildProgressPacket(
                key, placed, placedCount, total, consumed, done, paused, pauseReason, waits, placedByBlockId,
                placementFailures);
    }

    public static void handle(BuildProgressPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientScreens.applyBuildProgress(packet)));
        ctx.get().setPacketHandled(true);
    }

    public BuildJobKey key() {
        return key;
    }

    public BlockPos placedPos() {
        return placedPos;
    }

    public int placedCount() {
        return placedCount;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int consumed() {
        return consumed;
    }

    public boolean done() {
        return done;
    }

    public boolean paused() {
        return paused;
    }

    public String pauseReason() {
        return pauseReason;
    }

    public Map<UUID, String> maidMaterialWaits() {
        return maidMaterialWaits;
    }

    public Map<String, Integer> placedByBlockId() {
        return placedByBlockId;
    }

    public List<String> placementFailures() {
        return placementFailures;
    }
}
