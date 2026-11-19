package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoCraftMemoPolicyTest {
    @Test
    void keepsFailuresAcrossTicksWhenVaultUnchanged() {
        assertFalse(AutoCraftMemoPolicy.shouldClearFailures(100, 101, 42L, 42L));
    }

    @Test
    void clearsFailuresWhenVaultSignatureChanges() {
        assertTrue(AutoCraftMemoPolicy.shouldClearFailures(100, 101, 42L, 99L));
    }

    @Test
    void clearsFailuresOnFirstMemo() {
        assertTrue(AutoCraftMemoPolicy.shouldClearFailures(Integer.MIN_VALUE, 0, Long.MIN_VALUE, 1L));
    }
}
