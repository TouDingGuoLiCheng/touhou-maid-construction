package com.touhoumaid.construction.build;

import com.touhoumaid.construction.ConstructionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Re-queue placed schematic blocks when they are destroyed during an active job. */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConstructionBlockEvents {
    private ConstructionBlockEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        handleDestroyed(level, event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            handleDestroyed(level, pos);
        }
    }

    private static void handleDestroyed(ServerLevel level, BlockPos pos) {
        BuildPlacementIndex.keyAt(pos)
                .flatMap(BuildJobRegistry::get)
                .ifPresent(job -> {
                    if (job.revertPlacedBlock(pos)) {
                        BuildJobService.onPlacedBlockBroken(level, job, pos);
                    }
                });
    }
}
