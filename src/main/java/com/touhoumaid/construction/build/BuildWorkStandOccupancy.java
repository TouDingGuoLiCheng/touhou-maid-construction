package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Work stands already occupied by another maid. */
public final class BuildWorkStandOccupancy {
    private BuildWorkStandOccupancy() {
    }

    public static boolean hasFreeStand(ServerLevel level, EntityMaid maid, BlockPos target) {
        Set<BlockPos> taken = occupiedStandsNear(level, maid, target);
        if (BuildClaimOccupancyProbePolicy.useCheapStandProbeForClaim()) {
            // Claim ordering: cheap solid-near probe. Full gatherCandidates+LOS is for walk assign.
            return BuildClaimEase.hasUntakenSolidStandNear(level, target, taken);
        }
        List<BlockPos> stands = BuildWorkPosition.gatherCandidates(level, maid, target);
        if (stands.isEmpty()) {
            return false;
        }
        return anyFree(stands, taken);
    }

    /** A cell with no stand at all is crowded, not free. */
    static <T> boolean anyFree(List<T> stands, Set<?> taken) {
        for (T stand : stands) {
            if (!taken.contains(stand)) {
                return true;
            }
        }
        return false;
    }

    static Set<BlockPos> occupiedStandsNear(ServerLevel level, EntityMaid maid, BlockPos target) {
        AABB search = new AABB(target).inflate(5.0D);
        Set<BlockPos> taken = new HashSet<>();
        for (EntityMaid other : level.getEntitiesOfClass(
                EntityMaid.class, search, candidate -> !candidate.getUUID().equals(maid.getUUID()))) {
            taken.add(other.blockPosition());
        }
        return taken;
    }
}
