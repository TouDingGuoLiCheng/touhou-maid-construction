package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildArrivedPolicyTest {
    @Test
    void givesUpAfterFortyTicksWhenNearStandButCannotInteract() {
        BuildArrivedPolicy.State state = BuildArrivedPolicy.State.EMPTY;
        for (int i = 0; i < BuildArrivedPolicy.GIVE_UP_TICKS; i++) {
            state = BuildArrivedPolicy.onTick(state, 1.0D, true, false);
        }
        assertTrue(BuildArrivedPolicy.shouldGiveUp(state));
    }

    @Test
    void doesNotGiveUpWhenNavDoneFarFromStand() {
        BuildArrivedPolicy.State state = BuildArrivedPolicy.State.EMPTY;
        for (int i = 0; i < BuildArrivedPolicy.GIVE_UP_TICKS; i++) {
            state = BuildArrivedPolicy.onTick(state, 16.0D, true, false);
        }
        assertFalse(BuildArrivedPolicy.shouldGiveUp(state));
    }

    @Test
    void resetsWhenCanInteract() {
        BuildArrivedPolicy.State state = BuildArrivedPolicy.onTick(
                BuildArrivedPolicy.State.EMPTY, 1.0D, true, false);
        state = BuildArrivedPolicy.onTick(state, 1.0D, true, true);
        assertFalse(BuildArrivedPolicy.shouldGiveUp(state));
    }

    @Test
    void doesNotCountWhileStillPathingFarAway() {
        BuildArrivedPolicy.State state = BuildArrivedPolicy.State.EMPTY;
        for (int i = 0; i < BuildArrivedPolicy.GIVE_UP_TICKS; i++) {
            state = BuildArrivedPolicy.onTick(state, 100.0D, false, false);
        }
        assertFalse(BuildArrivedPolicy.shouldGiveUp(state));
    }
}
