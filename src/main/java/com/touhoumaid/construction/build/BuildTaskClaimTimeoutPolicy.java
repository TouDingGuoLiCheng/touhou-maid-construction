package com.touhoumaid.construction.build;

/**
 * Per-maid task claim timeout: release stuck claims so other maids (or a later retry) can take over.
 */
public final class BuildTaskClaimTimeoutPolicy {
    /** 30 seconds at 20 TPS. */
    public static final int CLAIM_TIMEOUT_TICKS = 20 * 30;
    /** Solo maid waits a few seconds before claiming again after abandoning. */
    public static final int SOLO_RETRY_COOLDOWN_TICKS = 100;
    /** Neighbourhood stall while moving with an active claim (3 s at 20 TPS). */
    public static final int STALL_MOVE_ABANDON_SAME_BLOCK = ConstructionSiteArrivePolicy.STUCK_TELEPORT_TICKS;
    /** Neighbourhood stall ticks before defer-to-neighbor is suppressed (escalate recovery). */
    public static final int DEFER_SUPPRESS_STALL_TICKS = STALL_MOVE_ABANDON_SAME_BLOCK / 4;

    private BuildTaskClaimTimeoutPolicy() {
    }

    public static boolean shouldSuppressDefer(int neighborhoodStallTicks) {
        return shouldSuppressDefer(neighborhoodStallTicks, false);
    }

    /**
     * @param idleWithoutActiveClaim when true (IDLE pick loop with no held task), never suppress —
     *                               stall from standing still must not lock the maid onto one cell
     */
    public static boolean shouldSuppressDefer(int neighborhoodStallTicks, boolean idleWithoutActiveClaim) {
        if (idleWithoutActiveClaim) {
            return false;
        }
        return neighborhoodStallTicks >= DEFER_SUPPRESS_STALL_TICKS;
    }

    public static boolean shouldAbandon(int ticksHeld) {
        return ticksHeld >= CLAIM_TIMEOUT_TICKS;
    }

    public static boolean shouldAbandonStalledMove(int claimTicks, int sameBlockTicks) {
        return claimTicks >= STALL_MOVE_ABANDON_SAME_BLOCK
                && sameBlockTicks >= STALL_MOVE_ABANDON_SAME_BLOCK;
    }

    /** FACING / PLACING with no placement progress — same 3 s threshold as stalled move. */
    public static boolean shouldAbandonStalledPlace(int claimTicks, int stallTicks) {
        return claimTicks >= STALL_MOVE_ABANDON_SAME_BLOCK
                && stallTicks >= STALL_MOVE_ABANDON_SAME_BLOCK;
    }

    /**
     * Held a claim with no work stand: allow a brief vault-direct-place window, then release.
     * Returning early from {@code beginDirectPlace} forever left maids frozen on-site ({@code work=-}).
     */
    public static boolean shouldAbandonNoWorkStand(int claimTicks, int stallTicks) {
        return claimTicks >= STALL_MOVE_ABANDON_SAME_BLOCK
                && stallTicks >= STALL_MOVE_ABANDON_SAME_BLOCK;
    }

    /**
     * Full claim timeout always releases — having no work stand must not loop {@code beginDirectPlace}.
     */
    public static boolean shouldAbandonWithoutWorkStandOnFullTimeout(boolean hasWorkStand) {
        return true;
    }

    public static int soloRetryCooldownTicks(int maidsOnSameJob) {
        return maidsOnSameJob <= 1 ? SOLO_RETRY_COOLDOWN_TICKS : 0;
    }
}
