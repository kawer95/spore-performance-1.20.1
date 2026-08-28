package com.arxyt.sporeperformance.ai;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;

/**
 * One-tick cache for the collision snapshot used by ExpAirPathNavigation.
 *
 * The original implementation constructs an identical PathNavigationRegion for
 * every shortcut candidate.  Navigation runs on the level thread, so a small
 * single-entry cache is sufficient and avoids retaining levels in a global map.
 */
public final class AirSweepContext {
    private static Level level;
    private static long gameTime = Long.MIN_VALUE;
    private static long min = Long.MIN_VALUE;
    private static long max = Long.MIN_VALUE;
    private static PathNavigationRegion region;
    private static long terrainVersion;

    public static PathNavigationRegion region(Level current, BlockPos minPos, BlockPos maxPos) {
        if (!PerformanceConfig.REFACTOR_BUSSER_ENABLED.get()
                || !PerformanceConfig.REFACTOR_BUSSER_SHARED_AIR_SWEEP_CONTEXT.get()) {
            return new PathNavigationRegion(current, minPos, maxPos);
        }
        long tick = current.getGameTime();
        long minKey = minPos.asLong();
        long maxKey = maxPos.asLong();
        if (region == null || level != current || gameTime != tick || min != minKey || max != maxKey) {
            if (level != current) terrainVersion = 0L;
            level = current;
            gameTime = tick;
            min = minKey;
            max = maxKey;
            region = new PathNavigationRegion(current, minPos, maxPos);
            PerformanceMetrics.increment("busser.air_sweep_region_created");
        } else {
            PerformanceMetrics.increment("busser.air_sweep_region_reused");
        }
        return region;
    }

    public static long terrainVersion(Level current) {
        return level == current ? terrainVersion : 0L;
    }

    /** Called by the shared path runtime when a block change invalidates navigation. */
    public static void invalidate(Level changedLevel) {
        if (level != changedLevel) return;
        terrainVersion++;
        region = null;
    }

    public static void clear() {
        level = null;
        gameTime = Long.MIN_VALUE;
        min = Long.MIN_VALUE;
        max = Long.MIN_VALUE;
        region = null;
        terrainVersion = 0L;
    }

    private AirSweepContext() {}
}
