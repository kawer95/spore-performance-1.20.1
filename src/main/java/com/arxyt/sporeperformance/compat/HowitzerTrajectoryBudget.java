package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-shooter admission control for AI Fix's expensive candidate-only trajectory probes. */
public final class HowitzerTrajectoryBudget {
    private static final Map<UUID, Counter> COUNTERS = new HashMap<>();

    public static boolean allow(Entity shooter) {
        if (!PerformanceConfig.AGGRESSIVE_HOWITZER_CACHE.get()) return true;
        long tick = shooter.level().getGameTime();
        synchronized (COUNTERS) {
            Counter counter = COUNTERS.computeIfAbsent(shooter.getUUID(), ignored -> new Counter(tick));
            if (counter.tick != tick) {
                counter.tick = tick;
                counter.used = 0;
            }
            if (counter.used >= PerformanceConfig.AGGRESSIVE_HOWITZER_MAX_NEW_TRAJECTORIES.get()) {
                PerformanceMetrics.increment("howitzer.trajectory_budget_deferred");
                return false;
            }
            ++counter.used;
            return true;
        }
    }

    public static void clear() { synchronized (COUNTERS) { COUNTERS.clear(); } }

    private static final class Counter {
        private long tick;
        private int used;
        private Counter(long tick) { this.tick = tick; }
    }
    private HowitzerTrajectoryBudget() {}
}
