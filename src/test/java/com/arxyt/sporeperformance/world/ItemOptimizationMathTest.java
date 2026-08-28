package com.arxyt.sporeperformance.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemOptimizationMathTest {
    @Test void neverLengthensAModDefinedLifetime() {
        assertEquals(900, ItemOptimizationMath.shortenedLifetime(900, 1200));
        assertEquals(1200, ItemOptimizationMath.shortenedLifetime(6000, 1200));
    }

    @Test void partialMergeStopsAtTheStackLimit() {
        assertEquals(24, ItemOptimizationMath.transferableUnits(40, 40, 64));
        assertEquals(0, ItemOptimizationMath.transferableUnits(64, 20, 64));
        assertEquals(5, ItemOptimizationMath.transferableUnits(1, 5, 64));
    }

    @Test void rejectsInvalidCounts() {
        assertThrows(IllegalArgumentException.class, () -> ItemOptimizationMath.transferableUnits(-1, 2, 64));
    }
}
