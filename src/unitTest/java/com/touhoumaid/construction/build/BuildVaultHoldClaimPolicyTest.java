package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildVaultHoldClaimPolicyTest {
    @Test
    void scaffoldEscalateMustNotDeferRelease() {
        assertFalse(BuildVaultHoldClaimPolicy.allowDeferRelease(true));
    }

    @Test
    void nonEscalateMayStillDeferRelease() {
        assertTrue(BuildVaultHoldClaimPolicy.allowDeferRelease(false));
    }
}
