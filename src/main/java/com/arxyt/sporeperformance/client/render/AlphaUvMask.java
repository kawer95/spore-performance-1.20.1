package com.arxyt.sporeperformance.client.render;

/** Immutable alpha occupancy map used to decide whether a model face can contribute pixels. */
public final class AlphaUvMask {
    private final int width;
    private final int height;
    private final boolean[] opaque;

    public AlphaUvMask(int width, int height, boolean[] opaque) {
        if (width <= 0 || height <= 0 || opaque.length != width * height) throw new IllegalArgumentException("mask dimensions");
        this.width = width;
        this.height = height;
        this.opaque = opaque.clone();
    }

    public boolean intersects(float minU, float minV, float maxU, float maxV) {
        int minX = clamp((int) Math.floor(Math.min(minU, maxU) * width) - 1, 0, width - 1);
        int maxX = clamp((int) Math.ceil(Math.max(minU, maxU) * width) + 1, 0, width - 1);
        int minY = clamp((int) Math.floor(Math.min(minV, maxV) * height) - 1, 0, height - 1);
        int maxY = clamp((int) Math.ceil(Math.max(minV, maxV) * height) + 1, 0, height - 1);
        for (int y = minY; y <= maxY; y++) {
            int row = y * width;
            for (int x = minX; x <= maxX; x++) if (opaque[row + x]) return true;
        }
        return false;
    }

    public boolean anyOpaque() {
        for (boolean value : opaque) if (value) return true;
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
