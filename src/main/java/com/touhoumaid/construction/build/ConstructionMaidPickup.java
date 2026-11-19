package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Saves and restores TLM item pickup while construction is actively running. */
public final class ConstructionMaidPickup {
    private static final Map<UUID, Boolean> RESTORE_PICKUP = new ConcurrentHashMap<>();

    private ConstructionMaidPickup() {
    }

    /** Disable pickup when construction work starts or resumes; keep the player's prior setting. */
    public static void disableForWork(EntityMaid maid) {
        RESTORE_PICKUP.computeIfAbsent(maid.getUUID(), id -> maid.isPickup());
        maid.setPickup(false);
    }

    /** Restore pickup when the job is paused or finished. */
    public static void restore(EntityMaid maid) {
        Boolean saved = RESTORE_PICKUP.remove(maid.getUUID());
        if (saved != null) {
            maid.setPickup(saved);
        }
    }

    public static void discard(UUID maidId) {
        RESTORE_PICKUP.remove(maidId);
    }
}
