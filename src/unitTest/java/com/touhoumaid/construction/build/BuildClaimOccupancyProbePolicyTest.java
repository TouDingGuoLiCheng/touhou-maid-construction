package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildClaimOccupancyProbePolicyTest {
    @Test
    void claimScanUsesCheapStandProbe() {
        assertTrue(BuildClaimOccupancyProbePolicy.useCheapStandProbeForClaim());
    }
}
