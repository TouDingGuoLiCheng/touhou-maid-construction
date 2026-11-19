package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionEndTaskResetPolicyTest {
    private static final String CONSTRUCTION = "touhou_maid_construction:construction";
    private static final String IDLE = "touhou_little_maid:idle";
    private static final String FARM = "touhou_little_maid:farm";

    @Test
    void resetsOnlyWhenStillOnConstructionTask() {
        assertTrue(ConstructionEndTaskResetPolicy.shouldSetIdle(CONSTRUCTION, CONSTRUCTION));
        assertFalse(ConstructionEndTaskResetPolicy.shouldSetIdle(IDLE, CONSTRUCTION));
        assertFalse(ConstructionEndTaskResetPolicy.shouldSetIdle(FARM, CONSTRUCTION));
        assertFalse(ConstructionEndTaskResetPolicy.shouldSetIdle(null, CONSTRUCTION));
        assertFalse(ConstructionEndTaskResetPolicy.shouldSetIdle(CONSTRUCTION, null));
    }
}
