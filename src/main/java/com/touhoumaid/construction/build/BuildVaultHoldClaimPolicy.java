package com.touhoumaid.construction.build;

/**
 * After scaffold escalate already failed, keep the claim so vault-place failures can accumulate.
 * Defer-release here would reset attempt counters and starve {@code VAULT_PLACE}.
 */
public final class BuildVaultHoldClaimPolicy {
    private BuildVaultHoldClaimPolicy() {
    }

    /**
     * @param alreadyInScaffoldEscalate true when called from scaffold result handling
     *                                  ({@code PLAN_FAILED} / {@code PLACE_BLOCKED} / NOT_NEEDED stuck)
     */
    public static boolean allowDeferRelease(boolean alreadyInScaffoldEscalate) {
        return !alreadyInScaffoldEscalate;
    }
}
