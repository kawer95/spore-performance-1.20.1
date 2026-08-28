package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlphaUvMaskTest {
    @Test
    void detectsAlphaInsideUvRectangleAndOnePixelSafetyBorder() {
        boolean[] pixels = new boolean[64];
        pixels[4 + 4 * 8] = true;
        AlphaUvMask mask = new AlphaUvMask(8, 8, pixels);
        assertTrue(mask.anyOpaque());
        assertTrue(mask.intersects(0.50F, 0.50F, 0.51F, 0.51F));
        assertTrue(mask.intersects(0.37F, 0.50F, 0.38F, 0.51F));
        assertFalse(mask.intersects(0.0F, 0.0F, 0.10F, 0.10F));
    }

    @Test
    void rejectsInvalidDimensionsAndReportsFullyTransparentMask() {
        assertThrows(IllegalArgumentException.class, () -> new AlphaUvMask(2, 2, new boolean[3]));
        assertFalse(new AlphaUvMask(2, 2, new boolean[4]).anyOpaque());
    }
}
