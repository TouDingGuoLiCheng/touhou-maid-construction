package com.touhoumaid.construction.build;

/** Fallback when a maid holds a work stand but places no schematic blocks for a long time. */
public final class BuildNoPlaceTeleportPolicy {
    /** 20 seconds at 20 TPS without a placed block. */
    public static final int NO_PLACE_TELEPORT_TICKS = 400;

    private BuildNoPlaceTeleportPolicy() {
    }

    public static boolean shouldTeleport(int ticksSinceLastPlace, int ticksSinceLastTeleport) {
        return ticksSinceLastPlace >= NO_PLACE_TELEPORT_TICKS
                && ticksSinceLastTeleport >= ConstructionSiteArrivePolicy.STUCK_TELEPORT_COOLDOWN_TICKS;
    }
}
