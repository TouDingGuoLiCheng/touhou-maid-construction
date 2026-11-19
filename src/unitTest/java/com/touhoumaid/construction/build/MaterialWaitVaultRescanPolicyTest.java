package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialWaitVaultRescanPolicyTest {
    @Test
    void rescansAboutEveryTenSeconds() {
        assertEquals(200, MaterialWaitVaultRescanPolicy.INTERVAL_TICKS);
        assertEquals(200, MaterialWaitVaultRescanPolicy.arm());
    }

    @Test
    void waitsUntilTheCountdownReachesZero() {
        assertFalse(MaterialWaitVaultRescanPolicy.due(1));
        assertFalse(MaterialWaitVaultRescanPolicy.due(200));
        assertTrue(MaterialWaitVaultRescanPolicy.due(0));
        assertTrue(MaterialWaitVaultRescanPolicy.due(-1));
    }
}
