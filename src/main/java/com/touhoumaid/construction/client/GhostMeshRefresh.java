package com.touhoumaid.construction.client;

/**
 * The ghost is submitted every frame so it stays locked to the camera.
 * Block models are baked once; conflict tint is the only thing that follows the anchor.
 */
public final class GhostMeshRefresh {
    public enum Action {
        DRAW,
        RETINT,
        REBUILD
    }

    public record Geometry(int blueprintIdentity, int yaw, int startLayer, String highlight, int placedGeneration) {
    }

    public record Tint(long origin, boolean cover, boolean applyConflict, int alphaMilli, int worldBucket) {
    }

    private GhostMeshRefresh() {
    }

    public static Geometry geometry(
            int blueprintIdentity,
            int yaw,
            int startLayer,
            String highlight,
            boolean building,
            int placedGeneration) {
        return new Geometry(
                blueprintIdentity,
                yaw,
                startLayer,
                highlight == null ? "" : highlight,
                building ? placedGeneration : 0);
    }

    /**
     * Conflict red is a site-pick concern. After the anchor is confirmed the mesh
     * no longer depends on where the box sits, so moving it must not rebake or retint.
     */
    public static Tint tint(long origin, boolean cover, boolean picking, boolean building, int worldBucket) {
        int alphaMilli = building ? 450 : picking ? 700 : 600;
        if (!picking) {
            return new Tint(0L, false, false, alphaMilli, 0);
        }
        return new Tint(origin, cover, true, alphaMilli, worldBucket);
    }

    public static Action choose(boolean hasMesh, Geometry baked, Geometry next, Tint bakedTint, Tint nextTint) {
        if (!hasMesh || baked == null || !baked.equals(next)) {
            return Action.REBUILD;
        }
        if (bakedTint == null || !bakedTint.equals(nextTint)) {
            return Action.RETINT;
        }
        return Action.DRAW;
    }
}
