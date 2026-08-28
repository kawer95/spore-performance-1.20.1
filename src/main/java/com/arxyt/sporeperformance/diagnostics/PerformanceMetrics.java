package com.arxyt.sporeperformance.diagnostics;

import com.arxyt.sporeperformance.config.PerformanceConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe counters used only when diagnostics are explicitly enabled. */
public final class PerformanceMetrics {
    private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

    public static void increment(String key) {
        if (enabled(key)) COUNTERS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }

    public static void add(String key, long amount) {
        if (amount != 0L && enabled(key)) {
            COUNTERS.computeIfAbsent(key, ignored -> new LongAdder()).add(amount);
        }
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> result = new java.util.TreeMap<>();
        COUNTERS.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    public static void reset() { COUNTERS.clear(); }

    public static boolean aiEnabled() {
        return PerformanceConfig.DIAGNOSTICS_METRICS.get() || PerformanceConfig.DIAGNOSTICS_AI_REFACTOR_METRICS.get();
    }

    private static boolean enabled(String key) {
        return PerformanceConfig.DIAGNOSTICS_METRICS.get()
                || key.startsWith("ai_refactor.") && PerformanceConfig.DIAGNOSTICS_AI_REFACTOR_METRICS.get();
    }

    private PerformanceMetrics() {}
}
