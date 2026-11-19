package com.touhoumaid.construction.build;

/**
 * When a maid is beside a neighbor tower or hovering one cell above a column,
 * extend scaffolding at her own XZ instead of walking into the tower's collision
 * posts or snap-teleporting down.
 */
public final class ScaffoldFillUnderFeetPolicy {
    private ScaffoldFillUnderFeetPolicy() {
    }

    /**
     * Vanilla pathfinding reports "can reach" a stand on an adjacent tower, but the maid
     * actually clips the scaffolding posts. Those stands must still be planned as a local fill.
     *
     * <p>Only stands that are both a real climb and genuinely off to the side qualify: a stand
     * one cell away used to be skipped here, which made her tower for a wall top she could walk onto.
     */
    public static boolean shouldSkipNotNeededNeighborStand(int dxzToStand, int dyToStand) {
        return dxzToStand >= 2 && dyToStand >= 2;
    }

    /** Stuck next to a taller column: start/raise a tower under her own feet. */
    public static boolean shouldFillWhenStuckBeside(int dxzToWork, int dyToWork, int sameBlockTicks) {
        return dxzToWork <= 1 && dyToWork >= 2 && sameBlockTicks >= 20;
    }

    /**
     * Whether to raise scaffolding at the maid's current XZ. When she is still far from the work
     * column, filling here creates a useless side walkway instead of walking to the job first.
     */
    public static boolean shouldFillUnderFeet(
            int dxzToWork, int dyToWork, boolean floating, boolean stuckBeside) {
        return shouldFillUnderFeet(dxzToWork, dyToWork, floating, stuckBeside, false, 0);
    }

    public static boolean shouldFillUnderFeet(
            int dxzToWork,
            int dyToWork,
            boolean floating,
            boolean stuckBeside,
            boolean onScaffold,
            int sameBlockTicks) {
        if (stuckBeside) {
            return true;
        }
        if (floating) {
            return true;
        }
        if (dyToWork <= 0) {
            return false;
        }
        if (dxzToWork > 1) {
            return false;
        }
        if (onScaffold) {
            return true;
        }
        return sameBlockTicks >= 20;
    }

    /**
     * Y of the next scaffold to place on the maid's column, or {@link Integer#MIN_VALUE}.
     *
     * @param columnTopY highest scaffold block at her XZ, or {@link Integer#MIN_VALUE} if none
     */
    public static int fillY(
            int feetY,
            int workY,
            int columnTopY,
            boolean feetIsScaffold) {
        if (columnTopY != Integer.MIN_VALUE && feetY > columnTopY + 1) {
            return columnTopY + 1;
        }
        if (workY <= feetY) {
            return Integer.MIN_VALUE;
        }
        if (feetIsScaffold) {
            return feetY + 1;
        }
        return feetY;
    }

    /** Scaffold posts sit at cell edges; recenter if the maid is hugging a post. */
    public static boolean shouldCenterInScaffoldCell(double fracX, double fracZ) {
        return Math.abs(fracX - 0.5D) > 0.32D || Math.abs(fracZ - 0.5D) > 0.32D;
    }

    /**
     * Before stepping on scaffolding, stabilize footing for a moment: either she is still hugging
     * the post edges, or her horizontal motion is still high from the previous jump/teleport.
     */
    public static boolean shouldWaitForStableFooting(double fracX, double fracZ, double horizontalSpeedSq) {
        return shouldCenterInScaffoldCell(fracX, fracZ) || horizontalSpeedSq > 0.02D;
    }
}
