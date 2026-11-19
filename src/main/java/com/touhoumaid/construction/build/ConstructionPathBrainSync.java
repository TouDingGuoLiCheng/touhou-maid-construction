package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;

/**
 * TLM's {@code MaidInteractWithDoor} (CORE) only opens doors when {@link MemoryModuleType#PATH}
 * is present. Construction walks via {@code Navigation.moveTo} without brain WalkTarget, so the
 * active nav path must be published into brain memory or maids walk into closed doors and stall.
 */
public final class ConstructionPathBrainSync {
    private ConstructionPathBrainSync() {
    }

    /** Publish when navigation has a non-finished path. */
    public static boolean shouldPublishPath(boolean hasPath, boolean pathDone) {
        return hasPath && !pathDone;
    }

    /** Clear when navigation is stopped (or caller is about to stop). */
    public static boolean shouldClearPath(boolean stoppingOrStopped) {
        return stoppingOrStopped;
    }

    public static void syncFromNavigation(EntityMaid maid) {
        Path path = maid.getNavigation().getPath();
        if (shouldPublishPath(path != null, path != null && path.isDone())) {
            maid.getBrain().setMemory(MemoryModuleType.PATH, path);
        } else if (shouldClearPath(true)) {
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        }
    }

    public static void publish(EntityMaid maid, Path path) {
        if (shouldPublishPath(path != null, path != null && path.isDone())) {
            maid.getBrain().setMemory(MemoryModuleType.PATH, path);
        } else {
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        }
    }

    public static void stopNavigation(EntityMaid maid) {
        maid.getNavigation().stop();
        if (shouldClearPath(true)) {
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        }
    }
}
