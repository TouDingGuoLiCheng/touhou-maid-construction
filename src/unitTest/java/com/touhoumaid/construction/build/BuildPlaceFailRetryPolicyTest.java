package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildPlaceFailRetryPolicyTest {
    private record Cell(long id) {
    }

    @Test
    void movesFailedCellsToEndWhenAlternativesExist() {
        List<Cell> ordered = List.of(new Cell(1), new Cell(2), new Cell(3));
        List<Cell> result = BuildPlaceFailRetryPolicy.deprioritizeFailed(
                ordered, Set.of(1L), Cell::id);
        assertEquals(List.of(new Cell(2), new Cell(3), new Cell(1)), result);
    }

    @Test
    void keepsOrderWhenOnlyFailedCellsRemain() {
        List<Cell> ordered = List.of(new Cell(1), new Cell(2));
        List<Cell> result = BuildPlaceFailRetryPolicy.deprioritizeFailed(
                ordered, Set.of(1L, 2L), Cell::id);
        assertEquals(ordered, result);
    }

    @Test
    void noOpWhenFailedSetEmpty() {
        List<Cell> ordered = List.of(new Cell(1));
        assertEquals(ordered, BuildPlaceFailRetryPolicy.deprioritizeFailed(ordered, Set.of(), Cell::id));
    }
}
