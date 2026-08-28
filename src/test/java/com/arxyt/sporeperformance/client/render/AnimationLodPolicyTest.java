package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationLodPolicyTest {
    @Test
    void ordinaryEntitiesUseAllFourDistanceBands() {
        assertEquals(1, interval(false, 16));
        assertEquals(2, interval(false, 48));
        assertEquals(4, interval(false, 80));
        assertEquals(8, interval(false, 120));
    }

    @Test
    void majorEntitiesStayFullRateUntilEnabledAndUseSeparateThresholds() {
        assertEquals(1, majorInterval(false, 200));
        assertEquals(1, majorInterval(true, 48));
        assertEquals(2, majorInterval(true, 96));
        assertEquals(4, majorInterval(true, 160));
    }

    private static int interval(boolean major, int distance) {
        return AnimationLodController.intervalForDistance(major, (double) distance * distance,
                32, 64, 96, 2, 4, 8, true, 64, 128, 4);
    }

    private static int majorInterval(boolean enabled, int distance) {
        return AnimationLodController.intervalForDistance(true, (double) distance * distance,
                32, 64, 96, 2, 4, 8, enabled, 64, 128, 4);
    }
}
