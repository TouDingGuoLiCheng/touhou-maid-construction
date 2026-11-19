package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldReclaimSweepPolicyTest {
    private static final ScaffoldBoundsPolicy.Box BOX =
            new ScaffoldBoundsPolicy.Box(0, 60, 0, 10, 70, 10);

    @Test
    void finishSweepIncludesTowersOutsideTheTightBuildMargin() {
        assertFalse(ScaffoldBoundsPolicy.contains(BOX, ScaffoldBoundsPolicy.MARGIN, 13, 65, 5));
        assertTrue(ScaffoldReclaimSweepPolicy.includes(BOX, 13, 65, 5));
    }

    @Test
    void finishSweepReachesTheOutwardColumnSearchButNotBeyond() {
        int edge = 10 + ScaffoldColumnSitePolicy.DEFAULT_MAX_OUTWARD;
        assertTrue(ScaffoldReclaimSweepPolicy.includes(BOX, edge, 65, 5));
        assertTrue(ScaffoldReclaimSweepPolicy.includes(BOX, 5, 60 - ScaffoldColumnSitePolicy.DEFAULT_MAX_OUTWARD, 5));
        assertFalse(ScaffoldReclaimSweepPolicy.includes(BOX, edge + 1, 65, 5));
    }

    @Test
    void footprintAndNullBox() {
        assertTrue(ScaffoldReclaimSweepPolicy.includes(BOX, 5, 65, 5));
        assertFalse(ScaffoldReclaimSweepPolicy.includes(null, 5, 65, 5));
    }
}
