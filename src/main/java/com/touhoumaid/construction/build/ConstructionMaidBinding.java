package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which maid is bound to which active build job and per-maid material wait. */
public final class ConstructionMaidBinding {
    private static final Map<UUID, BuildJobKey> MAID_TO_JOB = new ConcurrentHashMap<>();
    private static final Map<UUID, String> MATERIAL_WAIT = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> LAST_PLACED = new ConcurrentHashMap<>();

    private ConstructionMaidBinding() {
    }

    public static void bind(EntityMaid maid, BuildJobKey key) {
        MAID_TO_JOB.put(maid.getUUID(), key);
        LAST_PLACED.remove(maid.getUUID());
    }

    public static void unbind(EntityMaid maid) {
        MAID_TO_JOB.remove(maid.getUUID());
        MATERIAL_WAIT.remove(maid.getUUID());
        LAST_PLACED.remove(maid.getUUID());
        ConstructionMaidFollow.onConstructionEnd(maid);
    }

    public static Optional<BuildJob> jobOf(EntityMaid maid) {
        BuildJobKey key = MAID_TO_JOB.get(maid.getUUID());
        if (key == null) {
            return Optional.empty();
        }
        return BuildJobRegistry.get(key);
    }

    public static Optional<BuildJobKey> keyOf(EntityMaid maid) {
        return Optional.ofNullable(MAID_TO_JOB.get(maid.getUUID()));
    }

    public static void clearJob(BuildJobKey key) {
        MAID_TO_JOB.entrySet().removeIf(e -> {
            if (e.getValue().equals(key)) {
                MATERIAL_WAIT.remove(e.getKey());
                LAST_PLACED.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public static Map<UUID, BuildJobKey> activeMaidJobs() {
        return Map.copyOf(MAID_TO_JOB);
    }

    public static int countOnJob(BuildJobKey key) {
        if (key == null) {
            return 0;
        }
        int count = 0;
        for (BuildJobKey bound : MAID_TO_JOB.values()) {
            if (key.equals(bound)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isMaterialWait(EntityMaid maid) {
        String blockId = MATERIAL_WAIT.get(maid.getUUID());
        return blockId != null && !blockId.isEmpty();
    }

    public static Optional<String> materialWaitBlockId(UUID maidId) {
        String blockId = MATERIAL_WAIT.get(maidId);
        if (blockId == null || blockId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(blockId);
    }

    public static void setMaterialWait(EntityMaid maid, String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            MATERIAL_WAIT.remove(maid.getUUID());
        } else {
            MATERIAL_WAIT.put(maid.getUUID(), blockId);
        }
    }

    public static void clearMaterialWait(EntityMaid maid) {
        MATERIAL_WAIT.remove(maid.getUUID());
    }

    public static Optional<BlockPos> lastPlacedPos(UUID maidId) {
        return Optional.ofNullable(LAST_PLACED.get(maidId));
    }

    public static void setLastPlacedPos(EntityMaid maid, BlockPos pos) {
        if (pos == null) {
            LAST_PLACED.remove(maid.getUUID());
        } else {
            LAST_PLACED.put(maid.getUUID(), pos.immutable());
        }
    }

    public static Map<UUID, String> materialWaitsForJob(BuildJobKey key) {
        Map<UUID, String> result = new HashMap<>();
        for (Map.Entry<UUID, BuildJobKey> entry : MAID_TO_JOB.entrySet()) {
            if (!entry.getValue().equals(key)) {
                continue;
            }
            String blockId = MATERIAL_WAIT.get(entry.getKey());
            if (blockId != null && !blockId.isEmpty()) {
                result.put(entry.getKey(), blockId);
            }
        }
        return result;
    }
}
