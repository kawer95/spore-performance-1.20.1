package com.arxyt.sporeperformance.ai;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalamityRepathPolicyTest {
    @Test
    void suppressesDuplicateRequestFromMultipleSelectorsInSameTick() {
        assertEquals(CalamityRepathPolicy.Decision.SUPPRESS_SAME_TICK,
                decide(100, 100, 115, true, 0.0, true, false, false, false));
    }

    @Test
    void reusesValidPathUntilJitteredRefreshDeadline() {
        assertEquals(CalamityRepathPolicy.Decision.REUSE_ACTIVE_PATH,
                decide(105, 100, 115, true, 1.0, true, false, false, false));
    }

    @Test
    void targetJumpAndStuckRecoveryBypassCooldownImmediately() {
        assertEquals(CalamityRepathPolicy.Decision.ALLOW,
                decide(105, 100, 115, true, 25.0, true, false, false, false));
        assertEquals(CalamityRepathPolicy.Decision.ALLOW,
                decide(105, 100, 115, true, 0.0, true, false, false, true));
    }

    @Test
    void failedRequestHasBoundedRetryInsteadOfPerTickPathfinding() {
        assertEquals(CalamityRepathPolicy.Decision.SUPPRESS_FAILED_RETRY,
                decide(105, 100, 115, true, 0.0, false, false, true, false));
        assertEquals(CalamityRepathPolicy.Decision.ALLOW,
                decide(115, 100, 115, true, 0.0, false, false, true, false));
    }

    @Test
    void manyEntitiesSpreadRefreshesAcrossConfiguredWindow() {
        Set<Integer> intervals = new HashSet<>();
        for (int index = 0; index < 256; index++) {
            int interval = CalamityRepathPolicy.jitteredInterval(new UUID(index * 31L, index * 97L + 11L), 10, 20);
            assertTrue(interval >= 10 && interval <= 20);
            intervals.add(interval);
        }
        assertTrue(intervals.size() >= 9, "jitter should use most of the 10-20 tick window");
    }

    private static CalamityRepathPolicy.Decision decide(long now, long last, long next, boolean same,
                                                         double movementSqr, boolean activePath,
                                                         boolean direct, boolean failed, boolean urgent) {
        return CalamityRepathPolicy.decide(new CalamityRepathPolicy.Input(now, last, next, same,
                movementSqr, 16.0, activePath, direct, failed, urgent));
    }
}
