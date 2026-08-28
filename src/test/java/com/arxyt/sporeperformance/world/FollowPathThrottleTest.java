package com.arxyt.sporeperformance.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowPathThrottleTest {
    private static final UUID PARTNER = new UUID(10L, 20L);

    @Test
    void firstPartnerAndMeaningfulMovementBypassWaiting() {
        FollowPathThrottle state = new FollowPathThrottle();
        assertTrue(state.shouldAttempt(PARTNER, 0, 0, 0, 100, true, true, 40, 2.0, true));
        state.recordAttempt(PARTNER, 0, 0, 0, 100, true, 40, 0, true, 80);
        assertFalse(state.shouldAttempt(PARTNER, 1.9, 0, 0, 120, true, true, 40, 2.0, true));
        assertTrue(state.shouldAttempt(PARTNER, 2.1, 0, 0, 120, true, true, 40, 2.0, true));
    }

    @Test
    void completedPathCanRetryBeforePeriodicRefresh() {
        FollowPathThrottle state = new FollowPathThrottle();
        state.shouldAttempt(PARTNER, 0, 0, 0, 100, true, true, 40, 2.0, false);
        state.recordAttempt(PARTNER, 0, 0, 0, 100, true, 40, 0, false, 80);
        assertTrue(state.shouldAttempt(PARTNER, 0, 0, 0, 111, false, true, 40, 2.0, false));
    }

    @Test
    void failuresUseBoundedExponentialBackoff() {
        assertEquals(20, FollowPathThrottle.backoffTicks(1, 80));
        assertEquals(40, FollowPathThrottle.backoffTicks(2, 80));
        assertEquals(80, FollowPathThrottle.backoffTicks(3, 80));
        assertEquals(80, FollowPathThrottle.backoffTicks(10, 80));

        FollowPathThrottle state = new FollowPathThrottle();
        state.shouldAttempt(PARTNER, 0, 0, 0, 100, false, false, 40, 2.0, true);
        state.recordAttempt(PARTNER, 0, 0, 0, 100, false, 40, 0, true, 80);
        assertFalse(state.shouldAttempt(PARTNER, 0, 0, 0, 119, false, false, 40, 2.0, true));
        assertTrue(state.shouldAttempt(PARTNER, 0, 0, 0, 120, false, false, 40, 2.0, true));
    }

    @Test
    void resetForcesImmediateAttempt() {
        FollowPathThrottle state = new FollowPathThrottle();
        state.shouldAttempt(PARTNER, 0, 0, 0, 100, true, true, 40, 2.0, true);
        state.recordAttempt(PARTNER, 0, 0, 0, 100, true, 40, 19, true, 80);
        state.reset();
        assertTrue(state.shouldAttempt(PARTNER, 0, 0, 0, 101, true, true, 40, 2.0, true));
    }
}
