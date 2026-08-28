package com.arxyt.sporeperformance.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CalamityNavigationPolicyTest {
    @Test
    void turningWithoutMovementIsNotProgress() {
        assertFalse(CalamityNavigationPolicy.hasProgress(3, 3, 0.001D, 0.01D));
        assertTrue(CalamityNavigationPolicy.hasProgress(3, 4, 0.0D, 0.01D));
        assertTrue(CalamityNavigationPolicy.hasProgress(3, 3, 0.02D, 0.01D));
    }

    @Test
    void progressLimitAndRetryBackoffAreBounded() {
        assertFalse(CalamityNavigationPolicy.noProgress(119, 100, 20));
        assertTrue(CalamityNavigationPolicy.noProgress(120, 100, 20));
        assertEquals(20, CalamityNavigationPolicy.retryDelay(1, 20, 80));
        assertEquals(40, CalamityNavigationPolicy.retryDelay(2, 20, 80));
        assertEquals(80, CalamityNavigationPolicy.retryDelay(3, 20, 80));
        assertEquals(80, CalamityNavigationPolicy.retryDelay(99, 20, 80));
    }

    @Test
    void normalLargeCalamityMotionCountsAsProgress() {
        double threshold = CalamityNavigationPolicy.progressThresholdSqr(20.0D);
        assertTrue(CalamityNavigationPolicy.hasProgress(4, 4, 0.04D * 0.04D, threshold));
        assertFalse(CalamityNavigationPolicy.hasProgress(4, 4, 0.002D * 0.002D, threshold));
    }

    @Test
    void circularMotionDoesNotCountAsRouteProgress() {
        assertFalse(CalamityNavigationPolicy.improvesRouteDistance(69.159D, 69.180D));
        assertTrue(CalamityNavigationPolicy.improvesRouteDistance(69.159D, 69.120D));
        assertFalse(CalamityNavigationPolicy.isCircularSteering(4L, 180.0F));
        assertFalse(CalamityNavigationPolicy.isCircularSteering(5L, 89.9F));
        assertTrue(CalamityNavigationPolicy.isCircularSteering(5L, 90.0F));
    }
}
