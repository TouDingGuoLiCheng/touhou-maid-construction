package com.touhoumaid.construction.build;

/** Maid block interaction distance for construction placement. */
public final class BuildInteractionReach {
    public static final double REACH_DISTANCE = 6.0D;

    private BuildInteractionReach() {
    }

    public static boolean canReach(double eyeX, double eyeY, double eyeZ,
                                   int blockX, int blockY, int blockZ, double reachDistance) {
        double dx = (blockX + 0.5D) - eyeX;
        double dy = (blockY + 0.5D) - eyeY;
        double dz = (blockZ + 0.5D) - eyeZ;
        return dx * dx + dy * dy + dz * dz <= reachDistance * reachDistance;
    }
}
