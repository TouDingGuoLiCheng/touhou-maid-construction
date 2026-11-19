package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScaffoldClimbPolicyTest {
    @Test
    void stepsUpTheColumnTowardTheTop() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        ScaffoldLayout.Cell next = ScaffoldClimbPolicy.nextStep(new ScaffoldLayout.Cell(0, 65, 0), path);
        assertEquals(new ScaffoldLayout.Cell(0, 66, 0), next);
    }

    @Test
    void offPathWalksToTheNearestCell() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        ScaffoldLayout.Cell next = ScaffoldClimbPolicy.nextStep(new ScaffoldLayout.Cell(2, 64, 0), path);
        assertEquals(new ScaffoldLayout.Cell(0, 65, 0), next);
    }

    @Test
    void staysPutWhenAlreadyAtTheEnd() {
        List<ScaffoldLayout.Cell> path = ScaffoldLayout.columnThenArm(64, 0, 0, 0, 70, 0);
        ScaffoldLayout.Cell top = path.get(path.size() - 1);
        assertEquals(top, ScaffoldClimbPolicy.nextStep(top, path));
    }
}
