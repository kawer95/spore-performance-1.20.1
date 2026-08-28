package com.arxyt.sporeperformance.world;

/** Pure arithmetic used by tests and item optimization decisions. */
public final class ItemOptimizationMath {
    public static int shortenedLifetime(int nativeLifetime, int configuredLifetime) {
        return Math.min(nativeLifetime, configuredLifetime);
    }

    public static int transferableUnits(int targetCount, int sourceCount, int maxStackSize) {
        if (targetCount < 0 || sourceCount < 0 || maxStackSize < 1) throw new IllegalArgumentException("invalid stack size");
        return Math.max(0, Math.min(sourceCount, maxStackSize - targetCount));
    }

    private ItemOptimizationMath() {}
}
