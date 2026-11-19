package com.touhoumaid.construction.build;

/**
 * Which world cells a job-finish sweep may break. The tight build margin rejects towers a few
 * blocks outside the footprint; finish reclaim must still see those, up to the outward column search.
 */
public final class ScaffoldReclaimSweepPolicy {
    /** Footprint plus the furthest a column site may step outward from the building. */
    public static final int MARGIN = ScaffoldColumnSitePolicy.DEFAULT_MAX_OUTWARD;

    private ScaffoldReclaimSweepPolicy() {
    }

    public static boolean includes(ScaffoldBoundsPolicy.Box box, int x, int y, int z) {
        if (box == null) {
            return false;
        }
        return ScaffoldBoundsPolicy.contains(box, MARGIN, x, y, z);
    }
}
