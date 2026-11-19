package com.touhoumaid.construction.build;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/** Optional integration with Neruina tick suspension (reflection, no hard dependency). */
public final class NeruinaCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean LOADED = ModList.get().isLoaded("neruina");

    private NeruinaCompat() {
    }

    public static boolean clearEntitySuspension(Entity entity) {
        if (!LOADED || entity == null) {
            return false;
        }
        try {
            Class<?> neruinaClass = Class.forName("com.bawnorton.neruina.Neruina");
            Object neruina = neruinaClass.getMethod("getInstance").invoke(null);
            Object tickHandler = neruinaClass.getMethod("getTickHandler").invoke(neruina);
            boolean errored = (boolean) tickHandler.getClass()
                    .getMethod("isErrored", Object.class)
                    .invoke(tickHandler, entity);
            if (!errored) {
                return false;
            }
            logStoredError(tickHandler, entity);
            tickHandler.getClass()
                    .getMethod("removeErrored", Object.class)
                    .invoke(tickHandler, entity);
            LOGGER.warn("[TMC-Recover] cleared Neruina tick suspension for entity={}", entity.getUUID());
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[TMC-Recover] Neruina clear failed for entity={}: {}", entity.getUUID(), e.toString());
            return false;
        }
    }

    private static void logStoredError(Object tickHandler, Entity entity) {
        try {
            Object optionalId = tickHandler.getClass()
                    .getMethod("getTickingEntryId", Object.class)
                    .invoke(tickHandler, entity);
            if (!(optionalId instanceof Optional<?> optional) || optional.isEmpty()) {
                return;
            }
            UUID entryId = (UUID) optional.get();
            Object entry = tickHandler.getClass()
                    .getMethod("getTickingEntry", UUID.class)
                    .invoke(tickHandler, entryId);
            if (entry == null) {
                return;
            }
            Throwable error = (Throwable) entry.getClass().getMethod("error").invoke(entry);
            LOGGER.error("[TMC-Recover] Neruina stored tick error for entity={}", entity.getUUID(), error);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
