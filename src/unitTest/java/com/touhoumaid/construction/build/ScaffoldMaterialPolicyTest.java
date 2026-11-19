package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaffoldMaterialPolicyTest {
    @Test
    void prefersVaultOverMaid() {
        assertEquals(
                ScaffoldMaterialPolicy.Result.FROM_VAULT,
                ScaffoldMaterialPolicy.resolveSource(true, 5));
    }

    @Test
    void usesMaidWhenVaultEmpty() {
        assertEquals(
                ScaffoldMaterialPolicy.Result.FROM_MAID,
                ScaffoldMaterialPolicy.resolveSource(false, 2));
    }

    @Test
    void missingWhenNeitherHasStock() {
        assertEquals(
                ScaffoldMaterialPolicy.Result.MISSING,
                ScaffoldMaterialPolicy.resolveSource(false, 0));
    }
}
