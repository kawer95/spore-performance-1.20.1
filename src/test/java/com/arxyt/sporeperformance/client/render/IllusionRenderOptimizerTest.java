package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IllusionRenderOptimizerTest {
    @Test
    void requiresMadnessLevelTwoAndStrictlyMoreThanThirtyBlocks() {
        assertFalse(IllusionPolicy.required(-1, 10_000));
        assertFalse(IllusionPolicy.required(0, 10_000));
        assertFalse(IllusionPolicy.required(1, 900));
        assertTrue(IllusionPolicy.required(1, 900.0001));
    }
}
