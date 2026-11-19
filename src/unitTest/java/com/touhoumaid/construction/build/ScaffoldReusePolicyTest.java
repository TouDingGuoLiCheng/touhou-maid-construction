package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldReusePolicyTest {
    @Test
    void prefersReachableStandClosestToTarget() {
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(10, 64, 0);
        ScaffoldLayout.Cell nearTarget = new ScaffoldLayout.Cell(9, 64, 0);
        ScaffoldLayout.Cell nearMaid = new ScaffoldLayout.Cell(1, 64, 0);
        Set<Long> passable = new HashSet<>();
        for (int x = 0; x <= 9; x++) {
            passable.add(ScaffoldLayout.packCell(x, 64, 0));
        }

        Optional<ScaffoldLayout.Cell> picked = ScaffoldReusePolicy.pickReachableStandViaExistingScaffold(
                maid, target, List.of(nearMaid, nearTarget), passable);
        assertTrue(picked.isPresent());
        assertEquals(nearTarget, picked.get());
    }

    @Test
    void returnsEmptyWhenNoCandidateReachableViaExistingScaffold() {
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(0, 64, 0);
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(10, 64, 0);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(8, 64, 0);
        Set<Long> passable = Set.of(ScaffoldLayout.packCell(maid.x(), maid.y(), maid.z()));

        Optional<ScaffoldLayout.Cell> picked = ScaffoldReusePolicy.pickReachableStandViaExistingScaffold(
                maid, target, List.of(stand), passable);
        assertTrue(picked.isEmpty());
    }

    @Test
    void picksExistingColumnWhenInteractStandReachable() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(5, 68, 8, 63);
        registry.recordBlock(5, 69, 8, 63);
        Set<Long> passable = new HashSet<>();
        for (int y = 68; y <= 70; y++) {
            passable.add(ScaffoldLayout.packCell(5, y, 8));
        }
        // Target adjacent to column so stand on column can interact.
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(6, 70, 8);

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(5, 69, 8), target, registry, passable);
        // Work stands may not sit on scaffold columns — reuse must stay empty.
        assertTrue(picked.isEmpty());
    }

    @Test
    void skipsColumnThatDoesNotReachTargetHeight() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(5, 68, 8, 63);

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(5, 64, 8),
                new ScaffoldLayout.Cell(6, 72, 8),
                registry,
                Set.of());
        assertTrue(picked.isEmpty());
    }

    @Test
    void skipsDistantColumnWhenMaidCannotReachStand() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(106, -52, 229, -60);
        for (int y = -60; y <= -52; y++) {
            registry.recordBlock(106, y, 229);
        }
        Set<Long> ownPassable = new HashSet<>();
        ownPassable.add(ScaffoldLayout.packCell(105, -59, 238));
        registry.recordBlock(105, -60, 238, -61);

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(105, -59, 238),
                new ScaffoldLayout.Cell(106, -51, 229),
                registry,
                ownPassable);
        assertTrue(picked.isEmpty());
    }

    @Test
    void prefersOwnColumnAndSkipsBorrowingWhenOwnColumnStillShort() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        registry.recordBlock(105, -60, 238, -61);
        for (int y = -60; y <= -52; y++) {
            registry.recordBlock(106, y, 229);
        }
        Set<Long> passable = new HashSet<>();
        passable.add(ScaffoldLayout.packCell(105, -59, 238));

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(105, -59, 238),
                new ScaffoldLayout.Cell(106, -51, 229),
                registry,
                passable);
        assertTrue(picked.isEmpty());
    }

    @Test
    void skipsColumnWhenVerticalGapToStandExceedsOne() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        for (int y = -60; y <= -52; y++) {
            registry.recordBlock(5, y, 8);
        }
        Set<Long> passable = new HashSet<>();
        for (int y = -60; y <= -52; y++) {
            passable.add(ScaffoldLayout.packCell(5, y, 8));
        }
        passable.add(ScaffoldLayout.packCell(5, -59, 8));

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(5, -59, 8),
                new ScaffoldLayout.Cell(6, -51, 8),
                registry,
                passable);
        assertTrue(picked.isEmpty());
    }

    @Test
    void skipsTallOwnColumnWhenStandCannotInteractWithTarget() {
        ScaffoldColumnRegistry registry = new ScaffoldColumnRegistry();
        for (int y = -60; y <= -53; y++) {
            registry.recordBlock(64, y, 302);
        }
        Set<Long> passable = new HashSet<>();
        for (int y = -60; y <= -52; y++) {
            passable.add(ScaffoldLayout.packCell(64, y, 302));
        }
        // Too far in XZ for any stand Y on this column to reach.
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(66, -52, 312);

        Optional<ScaffoldReusePolicy.ReuseStand> picked = ScaffoldReusePolicy.pickColumnForVerticalReuse(
                new ScaffoldLayout.Cell(64, -52, 302), target, registry, passable);
        assertTrue(picked.isEmpty());
    }

    @Test
    void withinReuseVerticalGapAllowsOneStep() {
        ScaffoldLayout.Cell maid = new ScaffoldLayout.Cell(5, -52, 8);
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(5, -51, 8);
        assertTrue(ScaffoldReusePolicy.withinReuseVerticalGap(maid, stand));
        assertFalse(ScaffoldReusePolicy.withinReuseVerticalGap(maid, new ScaffoldLayout.Cell(5, -49, 8)));
    }
}
