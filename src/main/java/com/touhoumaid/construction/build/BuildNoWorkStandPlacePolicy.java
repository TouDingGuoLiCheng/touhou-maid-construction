package com.touhoumaid.construction.build;

/**
 * When a claimed cell has no solid work stand, vault-place immediately instead of holding IDLE
 * until stall/abandon (scaffold cannot plan without a stand either).
 */
public final class BuildNoWorkStandPlacePolicy {
    private BuildNoWorkStandPlacePolicy() {
    }

    public static boolean shouldImmediateVaultPlace(boolean hasWorkStand) {
        return !hasWorkStand;
    }
}
