package com.touhoumaid.construction.build;

/**
 * Claim-time occupancy must not run full placement-stand gather (Manhattan 4×8×4 + LOS) for every
 * stocked candidate — that stalls the server tick with many maids. Use the same small solid-near
 * probe as claim-ease scoring.
 */
public final class BuildClaimOccupancyProbePolicy {
    private BuildClaimOccupancyProbePolicy() {
    }

    /** Prefer the cheap solid-near probe for claim ordering. */
    public static boolean useCheapStandProbeForClaim() {
        return true;
    }
}
