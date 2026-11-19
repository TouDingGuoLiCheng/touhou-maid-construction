package com.touhoumaid.construction.build;

/**
 * Geometric fallback for "does she actually need scaffolding here". Vanilla
 * {@code canPathReach} reports false while the maid stands on scaffolding or beside a
 * one-block step, which used to make her raise a tower for a stand she could simply walk onto.
 *
 * <p>No Minecraft types so JVM unit tests can cover it.
 */
public final class ScaffoldNeedPolicy {
    /** Beyond this Chebyshev gap a same-level stand is no longer obviously walkable. */
    public static final int TRIVIAL_HORIZONTAL = 2;
    /** On scaffolding she can only step onto an adjacent cell without a planned tower. */
    public static final int TRIVIAL_HORIZONTAL_ON_SCAFFOLD = 1;
    /**
     * Do not trust {@code canPathReach} / scaffold-graph reach above this vertical gap — plan a
     * tower instead (vanilla pathfinding often lies on multi-block climbs).
     */
    public static final int VANILLA_REACH_TRUST_DY = 2;
    /**
     * Relative layer after start-layer trim ({@code 0} = first buildable floor). Scaffold planning
     * is disabled while building floors below this index (i.e. layers 1–2 in player terms).
     */
    public static final int MIN_LAYER_FOR_SCAFFOLD_PLANNING = 2;

    private ScaffoldNeedPolicy() {
    }

    /**
     * True when the stand is at most one block up or down and close by, i.e. a step the maid
     * can walk or jump without any scaffolding.
     */
    public static boolean isTriviallyReachable(int dx, int dy, int dz) {
        return isTriviallyReachable(dx, dy, dz, false);
    }

    /**
     * Same as {@link #isTriviallyReachable(int, int, int)} but tighter on scaffolding: she cannot
     * trivially hop several cells sideways off a tower onto a wall top.
     */
    public static boolean isTriviallyReachable(int dx, int dy, int dz, boolean maidOnScaffold) {
        if (Math.abs(dy) > 1) {
            return false;
        }
        int limit = maidOnScaffold ? TRIVIAL_HORIZONTAL_ON_SCAFFOLD : TRIVIAL_HORIZONTAL;
        return Math.max(Math.abs(dx), Math.abs(dz)) <= limit;
    }

    public static boolean trustVanillaPathReach(int dyToStand) {
        return Math.abs(dyToStand) <= VANILLA_REACH_TRUST_DY;
    }

    /** True when the maid still cannot interact and must climb more than {@link #VANILLA_REACH_TRUST_DY}. */
    public static boolean shouldForceScaffoldForVerticalGap(int feetY, int workStandY, boolean canInteractNow) {
        return !canInteractNow && Math.abs(feetY - workStandY) > VANILLA_REACH_TRUST_DY;
    }

    /**
     * Whether scaffold planning is allowed for the task's relative layer index (after start-layer
     * filtering remaps the first buildable floor to {@code 0}).
     */
    public static boolean allowsScaffoldPlanning(int relativeLayerIndex) {
        return relativeLayerIndex >= MIN_LAYER_FOR_SCAFFOLD_PLANNING;
    }
}
