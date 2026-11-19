package com.touhoumaid.construction.placement;

/**
 * 90-degree yaw steps around Y. 0 = schematic native (player looking south).
 * Matches {@code Direction.get2DDataValue()} and vanilla {@code Rotation} ordinals.
 */
public final class SchematicYaw {
    public static final int NONE = 0;
    public static final int CLOCKWISE_90 = 1;
    public static final int CLOCKWISE_180 = 2;
    public static final int COUNTERCLOCKWISE_90 = 3;

    private SchematicYaw() {
    }

    public static int normalize(int yaw) {
        return Math.floorMod(yaw, 4);
    }

    /** SOUTH=0, WEST=1, NORTH=2, EAST=3. */
    public static int fromHorizontal2D(int twoD) {
        return normalize(twoD);
    }

    public static int rotateLeft(int yaw) {
        return normalize(yaw - 1);
    }

    public static int rotateRight(int yaw) {
        return normalize(yaw + 1);
    }

    public static int placedSizeX(int sizeX, int sizeZ, int yaw) {
        return (normalize(yaw) & 1) == 1 ? sizeZ : sizeX;
    }

    public static int placedSizeZ(int sizeX, int sizeZ, int yaw) {
        return (normalize(yaw) & 1) == 1 ? sizeX : sizeZ;
    }

    public static int offsetX(int x, int z, int sizeX, int sizeZ, int yaw) {
        return switch (normalize(yaw)) {
            case CLOCKWISE_90 -> sizeZ - 1 - z;
            case CLOCKWISE_180 -> sizeX - 1 - x;
            case COUNTERCLOCKWISE_90 -> z;
            default -> x;
        };
    }

    public static int offsetZ(int x, int z, int sizeX, int sizeZ, int yaw) {
        return switch (normalize(yaw)) {
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> sizeZ - 1 - z;
            case COUNTERCLOCKWISE_90 -> sizeX - 1 - x;
            default -> z;
        };
    }
}
