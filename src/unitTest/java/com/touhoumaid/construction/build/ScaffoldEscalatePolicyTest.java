package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldEscalatePolicyTest {
    @Test
    void normalUnreachableMayReplanScaffold() {
        assertTrue(ScaffoldEscalatePolicy.allowScaffoldReplan(false));
    }

    @Test
    void afterScaffoldEscalateMustNotReplanScaffold() {
        assertFalse(ScaffoldEscalatePolicy.allowScaffoldReplan(true));
    }
}
