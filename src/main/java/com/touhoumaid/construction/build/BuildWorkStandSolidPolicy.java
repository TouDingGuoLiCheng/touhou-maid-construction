package com.touhoumaid.construction.build;

/**
 * Construction work stands require solid footing. Floating geometric cells that only satisfy
 * reach / line-of-sight must not be used as stands.
 */
public final class BuildWorkStandSolidPolicy {
    private BuildWorkStandSolidPolicy() {
    }

    /** @param canStandAtSolid true when {@link BuildWorkPosition#canStandAt} would accept the cell */
    public static boolean acceptsAsWorkStand(boolean canStandAtSolid) {
        return canStandAtSolid;
    }
}
