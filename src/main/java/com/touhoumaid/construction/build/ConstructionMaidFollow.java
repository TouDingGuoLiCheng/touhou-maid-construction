package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.touhoumaid.construction.ai.ConstructionPriority;
import com.touhoumaid.construction.task.TaskConstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Switches construction maids from follow-owner to stay-put for the job, restoring follow when done. */
public final class ConstructionMaidFollow {
    private static final Map<UUID, Boolean> RESTORE_FOLLOW = new ConcurrentHashMap<>();
    /** Maids currently in construction-imposed stay-put (may be temporarily released while bound). */
    private static final Set<UUID> CONSTRUCTION_STAY = ConcurrentHashMap.newKeySet();
    /** Server-side guard so release/resume side effects run once per idle spell. */
    private static final Set<UUID> IDLE_RELEASED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, StayArea> STAY_AREA = new ConcurrentHashMap<>();

    public record StayArea(BlockPos center, int radius, int minX, int maxX, int minZ, int maxZ) {
        public StayArea(BlockPos center, int radius) {
            this(
                    center,
                    radius,
                    center.getX() - radius,
                    center.getX() + radius,
                    center.getZ() - radius,
                    center.getZ() + radius);
        }
    }

    private ConstructionMaidFollow() {
    }

    public static void onConstructionStart(EntityMaid maid) {
        onConstructionStart(maid, ConstructionMaidBinding.jobOf(maid).orElse(null));
    }

    public static void onConstructionStart(EntityMaid maid, BuildJob job) {
        IDLE_RELEASED.remove(maid.getUUID());
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        ConstructionMaidPickup.disableForWork(maid);
        ConstructionPriority.setActive(maid, true);
        CONSTRUCTION_STAY.add(maid.getUUID());
        ConstructionMaidBrain.prepareForActiveWork(maid);
        if (!maid.isHomeModeEnable()) {
            RESTORE_FOLLOW.put(maid.getUUID(), Boolean.TRUE);
        }
        applyJobStay(maid, job);
    }

    public static void onConstructionEnd(EntityMaid maid) {
        IDLE_RELEASED.remove(maid.getUUID());
        CONSTRUCTION_STAY.remove(maid.getUUID());
        StayArea area = STAY_AREA.remove(maid.getUUID());
        ConstructionMaidPickup.restore(maid);
        ConstructionPriority.setAllowAmbientAi(maid, false);
        ConstructionPriority.setActive(maid, false);
        resetTaskToIdleIfNeeded(maid);
        if (Boolean.TRUE.equals(RESTORE_FOLLOW.remove(maid.getUUID()))) {
            maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
            maid.setHomeModeEnable(false);
            return;
        }
        if (area != null) {
            maid.getSchedulePos().setHomeModeEnable(maid, area.center());
            maid.setHomeModeEnable(true);
            maid.restrictTo(area.center(), area.radius());
        }
    }

    /** Switch TLM work mode from construction back to idle when the session ends. */
    private static void resetTaskToIdleIfNeeded(EntityMaid maid) {
        IMaidTask current = maid.getTask();
        String currentUid = current == null || current.getUid() == null ? null : current.getUid().toString();
        if (!ConstructionEndTaskResetPolicy.shouldSetIdle(currentUid, TaskConstruction.UID.toString())) {
            return;
        }
        IMaidTask idle = TaskManager.getIdleTask();
        if (idle != null) {
            maid.setTask(idle);
        }
    }

    /**
     * Pause or material wait: stop navigating, but stay in the construction restriction.
     * Do not path to the owner — that pulled maids off-site and looked like random running.
     */
    public static void releaseForIdle(EntityMaid maid) {
        if (!IDLE_RELEASED.add(maid.getUUID())) {
            return;
        }
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        // Keep ConstructionPriority active so TLM follow-owner yields; wait at the site.
    }

    /**
     * Freeze motion/navigation for material wait. Keep the site stay-area restrict (do not shrink
     * it — that caused in-place stomping). Owner teleport is blocked separately via mixin.
     */
    public static void pinToCurrentCell(EntityMaid maid) {
        if (BuildMaterialWaitStayPolicy.shouldResetNavigation(true)) {
            maid.getNavigation().stop();
            maid.getNavigationManager().resetNavigation();
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        Vec3 motion = maid.getDeltaMovement();
        double y = BuildMaterialWaitStayPolicy.shouldZeroVerticalMotion(maid.onGround()) ? 0.0D : motion.y;
        maid.setDeltaMovement(0.0D, y, 0.0D);
        maid.setHomeModeEnable(false);
        if (!BuildMaterialWaitStayPolicy.shouldTightenRestrict(true)) {
            StayArea area = STAY_AREA.get(maid.getUUID());
            if (area != null) {
                applyStayArea(maid, area);
            }
        }
    }

    /** Resume stay-put after idle release when construction work continues. */
    public static void resumeActiveWork(EntityMaid maid) {
        if (!IDLE_RELEASED.remove(maid.getUUID())) {
            return;
        }
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        ConstructionMaidPickup.disableForWork(maid);
        ConstructionPriority.setActive(maid, true);
        if (CONSTRUCTION_STAY.contains(maid.getUUID()) && !maid.isHomeModeEnable()) {
            StayArea area = STAY_AREA.get(maid.getUUID());
            if (area != null) {
                applyStayArea(maid, area);
            } else {
                applyJobStay(maid, ConstructionMaidBinding.jobOf(maid).orElse(null));
            }
        }
    }

    public static void releaseForJob(BuildJobKey key, ServerLevel level) {
        List<UUID> maidIds = new ArrayList<>();
        for (Map.Entry<UUID, BuildJobKey> entry : ConstructionMaidBinding.activeMaidJobs().entrySet()) {
            if (key.equals(entry.getValue())) {
                maidIds.add(entry.getKey());
            }
        }
        for (UUID maidId : maidIds) {
            if (level.getEntity(maidId) instanceof EntityMaid maid) {
                ConstructionMaidPickup.restore(maid);
                releaseForIdle(maid);
            }
        }
    }

    public static void endForJob(BuildJobKey key, ServerLevel level) {
        List<UUID> maidIds = new ArrayList<>();
        for (Map.Entry<UUID, BuildJobKey> entry : ConstructionMaidBinding.activeMaidJobs().entrySet()) {
            if (key.equals(entry.getValue())) {
                maidIds.add(entry.getKey());
            }
        }
        for (UUID maidId : maidIds) {
            if (level.getEntity(maidId) instanceof EntityMaid maid) {
                onConstructionEnd(maid);
            } else {
                RESTORE_FOLLOW.remove(maidId);
                CONSTRUCTION_STAY.remove(maidId);
                IDLE_RELEASED.remove(maidId);
                STAY_AREA.remove(maidId);
                ConstructionMaidPickup.discard(maidId);
            }
        }
    }

    private static void applyJobStay(EntityMaid maid, BuildJob job) {
        StayArea area = job != null
                ? job.workStayArea(maid.blockPosition())
                : new StayArea(maid.blockPosition(), 32);
        STAY_AREA.put(maid.getUUID(), area);
        applyStayArea(maid, area);
    }

    public static StayArea stayAreaOf(EntityMaid maid) {
        return maid == null ? null : STAY_AREA.get(maid.getUUID());
    }

    /**
     * Restrict to the site sphere so vanilla navigation stays in bounds, but do not enable TLM
     * home mode — that teleports toward the stay-put center while climbing.
     */
    private static void applyStayArea(EntityMaid maid, StayArea area) {
        maid.setHomeModeEnable(false);
        maid.restrictTo(area.center(), area.radius());
    }
}
