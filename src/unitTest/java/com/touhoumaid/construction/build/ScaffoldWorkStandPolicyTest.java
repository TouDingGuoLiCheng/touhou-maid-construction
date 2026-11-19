package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Interact-gated dynamic work stands (not mere height). */
class ScaffoldWorkStandPolicyTest {
    private static final double EYE = ScaffoldWorkStandPolicy.DEFAULT_EYE_HEIGHT;

    @Test
    void interactStandRejectsColumnOffsetTooFarFromWallTarget() {
        // Tower offset so that even the best Y is beyond REACH_DISTANCE.
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(64, -52, 302);
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(66, -52, 312);
        assertFalse(ScaffoldWorkStandPolicy.canInteractFromStand(stand, target, EYE));
    }

    @Test
    void interactStandAcceptsAdjacentWallStand() {
        ScaffoldLayout.Cell stand = new ScaffoldLayout.Cell(65, -52, 306);
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(66, -52, 306);
        assertTrue(ScaffoldWorkStandPolicy.canInteractFromStand(stand, target, EYE));
    }

    @Test
    void pickInteractStandOnColumnFindsUsableY() {
        ScaffoldColumnRegistry.Column column = new ScaffoldColumnRegistry.Column(65, 306, -61, -53, -52);
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(66, -52, 306);
        Optional<ScaffoldLayout.Cell> stand =
                ScaffoldWorkStandPolicy.pickInteractStandOnColumn(column, target, EYE);
        assertTrue(stand.isPresent());
        assertEquals(65, stand.get().x());
        assertEquals(306, stand.get().z());
        assertTrue(ScaffoldWorkStandPolicy.canInteractFromStand(stand.get(), target, EYE));
    }

    @Test
    void pickInteractStandOnColumnEmptyWhenColumnTooFar() {
        ScaffoldColumnRegistry.Column column = new ScaffoldColumnRegistry.Column(64, 302, -61, -53, -52);
        // Farther than REACH_DISTANCE at every stand Y on this column.
        ScaffoldLayout.Cell target = new ScaffoldLayout.Cell(66, -52, 312);
        assertTrue(ScaffoldWorkStandPolicy.pickInteractStandOnColumn(column, target, EYE).isEmpty());
    }

    @Test
    void descendToRelocateWhenOnScaffoldCannotInteract() {
        assertTrue(ConstructionWalkPolicy.shouldDescendToRelocate(true, false, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(true, true, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(false, false, 0));
        assertFalse(ConstructionWalkPolicy.shouldDescendToRelocate(true, false, 3));
    }

    @Test
    void workStandForbidsScaffoldFeetOrScaffoldFloor() {
        assertFalse(ScaffoldWorkStandPolicy.allowsWorkStand(true, false));
        assertFalse(ScaffoldWorkStandPolicy.allowsWorkStand(false, true));
        assertFalse(ScaffoldWorkStandPolicy.allowsWorkStand(true, true));
        assertTrue(ScaffoldWorkStandPolicy.allowsWorkStand(false, false));
    }

    @Test
    void scaffoldColumnIsNotAValidWorkStand() {
        assertFalse(ScaffoldWorkStandPolicy.allowsScaffoldColumnWorkStand());
    }
}
