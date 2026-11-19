package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildWorkStandOccupancyTest {
    @Test
    void noStandAtAllCountsAsCrowded() {
        assertFalse(BuildWorkStandOccupancy.anyFree(List.<String>of(), Set.of()));
    }

    @Test
    void everyStandTakenIsCrowded() {
        assertFalse(BuildWorkStandOccupancy.anyFree(List.of("a", "b"), Set.of("a", "b")));
    }

    @Test
    void oneUntakenStandIsFree() {
        assertTrue(BuildWorkStandOccupancy.anyFree(List.of("a", "b"), Set.of("a")));
    }
}
