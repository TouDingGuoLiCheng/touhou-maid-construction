package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstructionDebugIdsTest {
    @Test
    void maidLabelUsesNameAndShortUuid() {
        UUID id = UUID.fromString("5635aeb0-ea2d-4f5f-945d-70efe3a51cc6");
        assertEquals("酒尾狐#5635aeb0", ConstructionDebugIds.maid("酒尾狐", id));
    }
}
