package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/** Forces terminal chunks to load during login-time job restore. */
public final class ConstructionChunkLoad {
    private static final TicketType<ChunkPos> RESTORE_TICKET = TicketType.create(
            "touhou_maid_construction_restore",
            Comparator.comparingLong(ChunkPos::toLong),
            40);

    private ConstructionChunkLoad() {
    }

    public static void ensureLoaded(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(pos);
        level.getChunkSource().addRegionTicket(RESTORE_TICKET, chunkPos, 2, chunkPos);
        level.getChunk(chunkPos.x, chunkPos.z);
    }
}
