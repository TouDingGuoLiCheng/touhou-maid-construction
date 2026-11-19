package com.touhoumaid.construction.build;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionDebugTest {
    @AfterEach
    void reset() {
        ConstructionDebug.setEnabled(false);
    }

    @Test
    void setEnabledTurnsDebugOnAndOff() {
        ConstructionDebug.setEnabled(false);
        assertFalse(ConstructionDebug.enabled());

        ConstructionDebug.setEnabled(true);
        assertTrue(ConstructionDebug.enabled());

        ConstructionDebug.setEnabled(false);
        assertFalse(ConstructionDebug.enabled());
    }
}
