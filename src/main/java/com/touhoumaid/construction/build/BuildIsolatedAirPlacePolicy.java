package com.touhoumaid.construction.build;

/**
 * Near-isolated schematic cells may vault-place immediately — no walk, scaffold, or nav-fail
 * accumulation. Checks four horizontal faces plus above; the cell below is ignored.
 */
public final class BuildIsolatedAirPlacePolicy {
    /** Four horizontal sides + above (DOWN is excluded). */
    public static final int FACE_COUNT = 5;

    private BuildIsolatedAirPlacePolicy() {
    }

    public static boolean allowImmediateVaultPlace(boolean checkedNeighborsAirOrReplaceable) {
        return checkedNeighborsAirOrReplaceable;
    }

    /**
     * @param neighborAirOrReplaceable length {@link #FACE_COUNT}; each entry is one checked face
     *                                 (air or replaceable = true, solid attach = false)
     */
    public static boolean allNeighborsAirOrReplaceable(boolean[] neighborAirOrReplaceable) {
        if (neighborAirOrReplaceable == null || neighborAirOrReplaceable.length != FACE_COUNT) {
            return false;
        }
        for (boolean airOrReplaceable : neighborAirOrReplaceable) {
            if (!airOrReplaceable) {
                return false;
            }
        }
        return true;
    }
}
