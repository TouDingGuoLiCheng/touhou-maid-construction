package com.touhoumaid.construction.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** When construction is active, TLM follow-owner must yield the walk target near the owner. */
public final class ConstructionPriority {
    private static final String ROOT = "TouhouMaidConstruction";
    private static final String ACTIVE = "ConstructionActive";
    private static final String ALLOW_AMBIENT = "AllowAmbientAi";

    private ConstructionPriority() {
    }

    public static void setActive(EntityMaid maid, boolean active) {
        CompoundTag persistent = maid.getPersistentData();
        CompoundTag root = persistent.contains(ROOT) ? persistent.getCompound(ROOT) : new CompoundTag();
        if (active) {
            root.putBoolean(ACTIVE, true);
            root.remove(ALLOW_AMBIENT);
            persistent.put(ROOT, root);
            return;
        }
        root.remove(ACTIVE);
        writeRoot(persistent, root);
    }

    /** Pause / per-maid material wait: allow TLM follow-owner and random walk again. */
    public static void setAllowAmbientAi(EntityMaid maid, boolean allow) {
        CompoundTag persistent = maid.getPersistentData();
        CompoundTag root = persistent.contains(ROOT) ? persistent.getCompound(ROOT) : new CompoundTag();
        if (allow) {
            root.putBoolean(ALLOW_AMBIENT, true);
            root.remove(ACTIVE);
        } else {
            root.remove(ALLOW_AMBIENT);
        }
        writeRoot(persistent, root);
    }

    public static boolean allowAmbientAi(EntityMaid maid) {
        return maid.getPersistentData().getCompound(ROOT).getBoolean(ALLOW_AMBIENT);
    }

    public static void clear(UUID maidId) {
        // Entity unavailable — in-memory follow-restore flag already cleared in endForJob.
    }

    public static boolean isActive(EntityMaid maid) {
        return maid.getPersistentData().getCompound(ROOT).getBoolean(ACTIVE);
    }

    public static boolean followShouldYield(EntityMaid maid) {
        return isActive(maid);
    }

    private static void writeRoot(CompoundTag persistent, CompoundTag root) {
        if (root.isEmpty()) {
            persistent.remove(ROOT);
        } else {
            persistent.put(ROOT, root);
        }
    }
}
