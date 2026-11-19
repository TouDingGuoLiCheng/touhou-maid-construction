package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultAutoCraftPolicyTest {
    @Test
    void overflowStillCountsAsSuccessWhenSomeDeposited() {
        assertTrue(VaultAutoCraftPolicy.depositOkAllowingOverflow(6, 5));
        assertTrue(VaultAutoCraftPolicy.depositOkAllowingOverflow(6, 0));
    }

    @Test
    void zeroDepositIsFailure() {
        assertFalse(VaultAutoCraftPolicy.depositOkAllowingOverflow(6, 6));
        assertFalse(VaultAutoCraftPolicy.depositOkAllowingOverflow(1, 1));
    }

    @Test
    void unavailableOnlyWhenAutoCraftOff() {
        assertTrue(StockIndexUnavailablePolicy.shouldMarkUnavailable(false));
        assertFalse(StockIndexUnavailablePolicy.shouldMarkUnavailable(true));
    }
}
