package com.touhoumaid.construction.build;

/** Pure rules for schematic start-layer filtering (local Y from blueprint bottom). */
public final class StartLayerPolicy {
    private StartLayerPolicy() {
    }

    /** Clamp to {@code [0, height)} with height treated as at least 1. */
    public static int clamp(int startLayer, int height) {
        int h = Math.max(1, height);
        return Math.max(0, Math.min(startLayer, h - 1));
    }

    /** Whether a schematic local Y cell is part of the job. */
    public static boolean includesLocalY(int localY, int startLayer) {
        return localY >= Math.max(0, startLayer);
    }

    /** Start is allowed only when at least one included non-air cell remains after filtering. */
    public static boolean canStartBuild(int startLayer, int height, boolean hasIncludedNonAir) {
        clamp(startLayer, height);
        return hasIncludedNonAir;
    }
}
