package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildWorkStandSolidPolicyTest {
    @Test
    void solidFootingIsAcceptedAsWorkStand() {
        assertTrue(BuildWorkStandSolidPolicy.acceptsAsWorkStand(true));
    }

    @Test
    void floatingGeometricCellIsNotAWorkStand() {
        assertFalse(BuildWorkStandSolidPolicy.acceptsAsWorkStand(false));
    }
}
