package com.touhoumaid.construction.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GhostMeshRefreshTest {
    @Test
    void stableCameraOnlyRedrawsTheBakedMesh() {
        GhostMeshRefresh.Geometry geometry = GhostMeshRefresh.geometry(1, 90, 0, "", false, 4);
        GhostMeshRefresh.Tint tint = GhostMeshRefresh.tint(10L, false, true, false, 3);

        assertEquals(
                GhostMeshRefresh.Action.DRAW,
                GhostMeshRefresh.choose(true, geometry, geometry, tint, tint));
    }

    @Test
    void movingTheAnchorRetintsConflictsWithoutRebakingModels() {
        GhostMeshRefresh.Geometry geometry = GhostMeshRefresh.geometry(1, 0, 0, "", false, 0);
        GhostMeshRefresh.Tint here = GhostMeshRefresh.tint(10L, true, true, false, 1);
        GhostMeshRefresh.Tint there = GhostMeshRefresh.tint(11L, true, true, false, 1);

        assertEquals(
                GhostMeshRefresh.Action.RETINT,
                GhostMeshRefresh.choose(true, geometry, geometry, here, there));
    }

    @Test
    void yawOrMissingMeshRebuilds() {
        GhostMeshRefresh.Geometry before = GhostMeshRefresh.geometry(1, 0, 0, "", false, 0);
        GhostMeshRefresh.Geometry rotated = GhostMeshRefresh.geometry(1, 90, 0, "", false, 0);
        GhostMeshRefresh.Tint tint = GhostMeshRefresh.tint(10L, false, true, false, 0);

        assertEquals(
                GhostMeshRefresh.Action.REBUILD,
                GhostMeshRefresh.choose(true, before, rotated, tint, tint));
        assertEquals(
                GhostMeshRefresh.Action.REBUILD,
                GhostMeshRefresh.choose(false, before, before, tint, tint));
    }

    @Test
    void placedCellsAndHighlightChangeTheMeshOnlyWhenTheyAffectWhatIsDrawn() {
        GhostMeshRefresh.Geometry idle = GhostMeshRefresh.geometry(1, 0, 0, "", false, 0);
        GhostMeshRefresh.Geometry placedWhileIdle = GhostMeshRefresh.geometry(1, 0, 0, "", false, 9);
        GhostMeshRefresh.Geometry building = GhostMeshRefresh.geometry(1, 0, 0, "mod:brick", true, 1);
        GhostMeshRefresh.Geometry buildingPlaced = GhostMeshRefresh.geometry(1, 0, 0, "mod:brick", true, 2);
        GhostMeshRefresh.Tint tint = GhostMeshRefresh.tint(1L, false, false, true, 5);

        assertEquals(idle, placedWhileIdle);
        assertEquals(
                GhostMeshRefresh.Action.REBUILD,
                GhostMeshRefresh.choose(true, building, buildingPlaced, tint, tint));
    }

    @Test
    void conflictTintIgnoresAnchorUntilSitePickAndRefreshesOnWorldBucket() {
        GhostMeshRefresh.Tint anchored = GhostMeshRefresh.tint(10L, true, false, false, 4);
        GhostMeshRefresh.Tint anchoredElsewhere = GhostMeshRefresh.tint(99L, false, false, false, 80);
        GhostMeshRefresh.Tint picking = GhostMeshRefresh.tint(10L, true, true, false, 4);
        GhostMeshRefresh.Tint pickingLater = GhostMeshRefresh.tint(10L, true, true, false, 5);
        GhostMeshRefresh.Geometry geometry = GhostMeshRefresh.geometry(1, 0, 0, "", false, 0);

        assertEquals(anchored, anchoredElsewhere);
        assertEquals(
                GhostMeshRefresh.Action.DRAW,
                GhostMeshRefresh.choose(true, geometry, geometry, anchored, anchoredElsewhere));
        assertEquals(
                GhostMeshRefresh.Action.RETINT,
                GhostMeshRefresh.choose(true, geometry, geometry, picking, pickingLater));
    }
}
