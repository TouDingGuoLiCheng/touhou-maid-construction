package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkStandMissRescanPolicyTest {
    @Test
    void rescansImmediatelyWhenTheTargetChanges() {
        assertTrue(WorkStandMissRescanPolicy.shouldScan(false, 0));
    }

    @Test
    void holdsAnEmptyResultForAboutASecond() {
        assertFalse(WorkStandMissRescanPolicy.shouldScan(true, 0));
        assertFalse(WorkStandMissRescanPolicy.shouldScan(true, 19));
        assertTrue(WorkStandMissRescanPolicy.shouldScan(
                true, WorkStandMissRescanPolicy.RESCAN_INTERVAL_TICKS));
    }
}
