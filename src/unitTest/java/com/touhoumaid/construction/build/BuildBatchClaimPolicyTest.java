package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildBatchClaimPolicyTest {
    @Test
    void capsALongReachableListAtFive() {
        List<String> reachable = List.of("a", "b", "c", "d", "e", "f", "g");
        assertEquals(List.of("a", "b", "c", "d", "e"), BuildBatchClaimPolicy.take(reachable, 5));
    }

    @Test
    void leavesShortListsUnchanged() {
        List<String> reachable = List.of("a", "b");
        assertEquals(reachable, BuildBatchClaimPolicy.take(reachable, 5));
    }

    @Test
    void defaultCapIsFive() {
        assertEquals(5, BuildBatchClaimPolicy.MAX_TASKS);
    }
}
