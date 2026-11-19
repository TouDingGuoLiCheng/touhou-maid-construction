package com.touhoumaid.construction.build;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Delayed login passes so chunk/terminal load order does not block auto-resume. */
@Mod.EventBusSubscriber(modid = com.touhoumaid.construction.ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConstructionLoginRecovery {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] PASS_AT_TICKS_REMAINING = {60, 40, 20, 0};
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private ConstructionLoginRecovery() {
    }

    public static void schedule(ServerPlayer player) {
        PENDING.put(player.getUUID(), 60);
        LOGGER.info("[TMC-Recover] scheduled delayed build resume for player={}", player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            entry.setValue(remaining);
            if (shouldRunPass(remaining)) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    runPass(player, remaining);
                }
            }
            if (remaining <= 0) {
                iterator.remove();
            }
        }
    }

    private static boolean shouldRunPass(int remaining) {
        for (int passAt : PASS_AT_TICKS_REMAINING) {
            if (remaining == passAt) {
                return true;
            }
        }
        return false;
    }

    private static void runPass(ServerPlayer player, int remaining) {
        // Only retry missing job/maid bindings here — do not re-run hover/Neruina recovery on
        // every pass or active scaffold climbs get force-stopped mid-air.
        BuildJobService.restoreActiveBuildingJobs(player);
        LOGGER.info("[TMC-Recover] delayed build resume pass remaining={} player={}", remaining, player.getUUID());
    }
}
