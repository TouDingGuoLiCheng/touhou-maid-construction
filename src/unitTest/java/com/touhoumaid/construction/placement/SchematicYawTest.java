package com.touhoumaid.construction.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchematicYawTest {
    @Test
    void noneKeepsLocalOffsets() {
        assertEquals(2, SchematicYaw.offsetX(2, 1, 5, 3, 0));
        assertEquals(1, SchematicYaw.offsetZ(2, 1, 5, 3, 0));
    }

    @Test
    void clockwise90MapsIntoPositiveBox() {
        // size 5×3, cell (0,0) → (2, 0); cell (4,0) → (2, 4)
        assertEquals(2, SchematicYaw.offsetX(0, 0, 5, 3, 1));
        assertEquals(0, SchematicYaw.offsetZ(0, 0, 5, 3, 1));
        assertEquals(2, SchematicYaw.offsetX(4, 0, 5, 3, 1));
        assertEquals(4, SchematicYaw.offsetZ(4, 0, 5, 3, 1));
        assertEquals(3, SchematicYaw.placedSizeX(5, 3, 1));
        assertEquals(5, SchematicYaw.placedSizeZ(5, 3, 1));
    }

    @Test
    void clockwise180FlipsBothAxes() {
        assertEquals(4, SchematicYaw.offsetX(0, 0, 5, 3, 2));
        assertEquals(2, SchematicYaw.offsetZ(0, 0, 5, 3, 2));
        assertEquals(0, SchematicYaw.offsetX(4, 2, 5, 3, 2));
        assertEquals(0, SchematicYaw.offsetZ(4, 2, 5, 3, 2));
    }

    @Test
    void counterclockwise90MapsIntoPositiveBox() {
        assertEquals(0, SchematicYaw.offsetX(0, 0, 5, 3, 3));
        assertEquals(4, SchematicYaw.offsetZ(0, 0, 5, 3, 3));
        assertEquals(3, SchematicYaw.placedSizeX(5, 3, 3));
        assertEquals(5, SchematicYaw.placedSizeZ(5, 3, 3));
    }

    @Test
    void playerSouthIsNativeOrientation() {
        assertEquals(0, SchematicYaw.fromHorizontal2D(0));
    }

    @Test
    void leftAndRightAreOpposites() {
        int yaw = SchematicYaw.fromHorizontal2D(1);
        assertEquals(yaw, SchematicYaw.rotateRight(SchematicYaw.rotateLeft(yaw)));
        assertEquals(SchematicYaw.normalize(yaw + 1), SchematicYaw.rotateRight(yaw));
    }
}
