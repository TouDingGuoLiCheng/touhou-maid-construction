package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildClaimEasePolicyTest {
    @Test
    void solidStandBeatsFloatingCell() {
        assertTrue(BuildClaimEasePolicy.score(true, false, 0)
                > BuildClaimEasePolicy.score(false, true, 5));
    }

    @Test
    void supportAndNeighborsRaiseScore() {
        int bare = BuildClaimEasePolicy.score(false, false, 0);
        int supported = BuildClaimEasePolicy.score(false, true, 0);
        int attached = BuildClaimEasePolicy.score(false, false, 2);
        assertTrue(supported > bare);
        assertTrue(attached > bare);
    }

    @Test
    void neighborCountIsClamped() {
        assertEquals(
                BuildClaimEasePolicy.score(true, true, BuildClaimEasePolicy.MAX_SIDE_NEIGHBORS),
                BuildClaimEasePolicy.score(true, true, 99));
    }

    @Test
    void higherScoreFirstThenKeepsNearestOrder() {
        Map<String, Integer> scores = Map.of("floatNear", 0, "easyFar", 9, "floatMid", 0, "easyMid", 9);
        List<String> nearestFirst = List.of("floatNear", "easyMid", "floatMid", "easyFar");
        assertEquals(
                List.of("easyMid", "easyFar", "floatNear", "floatMid"),
                BuildClaimEasePolicy.orderByEase(nearestFirst, scores::get, 10));
    }

    @Test
    void cellsBeyondScoringBudgetStayAtTheEndInOrder() {
        Map<String, Integer> scores = Map.of("a", 0, "b", 5, "c", 99);
        assertEquals(
                List.of("b", "a", "c"),
                BuildClaimEasePolicy.orderByEase(List.of("a", "b", "c"), scores::get, 2));
    }

    @Test
    void emptyListStaysEmpty() {
        assertEquals(List.of(), BuildClaimEasePolicy.orderByEase(List.<String>of(), s -> 0, 10));
    }
}
