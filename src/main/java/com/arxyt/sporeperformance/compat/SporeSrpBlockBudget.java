package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;

/** Shared admission budget for sporesrp's existing incremental Full Hivemind mining slices. */
public final class SporeSrpBlockBudget {
    private static long tick = Long.MIN_VALUE;
    private static int remaining;

    public static synchronized void beginTick(long gameTime) {
        if (tick != gameTime) {
            tick = gameTime;
            remaining = anyBudgetConsumer() ? PerformanceConfig.AGGRESSIVE_SPORESRP_BLOCK_GLOBAL.get() : Integer.MAX_VALUE;
        }
    }

    /** Caps an existing [start,end) mining slice without advancing the handler's cursor past deferred work. */
    public static synchronized int capEnd(int proposedEnd, int queueSize) {
        if (!PerformanceConfig.AGGRESSIVE_SPORESRP_MINING_BUDGET.get()) return Math.min(proposedEnd, queueSize);
        int start = Math.max(0, proposedEnd - 150); // sporesrp's fixed native work slice
        int end = Math.min(proposedEnd, queueSize);
        int allowed = Math.max(0, Math.min(end - start, remaining));
        remaining -= allowed;
        if (allowed < end - start) PerformanceMetrics.increment("sporesrp.full_hivemind_blocks_deferred");
        return start + allowed;
    }

    public static synchronized int reserve(int requested) {
        if (!PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH.get() && !PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER.get()) return requested;
        int granted = Math.max(0, Math.min(requested, remaining));
        remaining -= granted;
        if (granted < requested) PerformanceMetrics.increment("sporesrp.background_blocks_deferred");
        return granted;
    }

    public static synchronized void clear() { tick = Long.MIN_VALUE; remaining = 0; }
    private static boolean anyBudgetConsumer() {
        return PerformanceConfig.AGGRESSIVE_SPORESRP_MINING_BUDGET.get()
                || PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH.get()
                || PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER.get();
    }
    private SporeSrpBlockBudget() {}
}
