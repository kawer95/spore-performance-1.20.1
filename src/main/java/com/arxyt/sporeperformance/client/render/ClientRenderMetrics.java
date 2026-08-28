package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.config.PerformanceConfig;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Opt-in client-only counters; the disabled hot path is a single config branch. */
public final class ClientRenderMetrics {
    private static final ConcurrentHashMap<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();

    public static boolean enabled() {
        return PerformanceConfig.CLIENT_RENDER_METRICS.get();
    }

    public static void increment(String key) {
        if (enabled()) COUNTERS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }

    public static void add(String key, long amount) {
        if (enabled()) COUNTERS.computeIfAbsent(key, ignored -> new LongAdder()).add(amount);
    }

    public static String snapshot() {
        Map<String, Long> sorted = new TreeMap<>();
        COUNTERS.forEach((key, value) -> sorted.put(key, value.sum()));
        return sorted.toString();
    }

    public static void reset() {
        COUNTERS.clear();
    }

    private ClientRenderMetrics() {}
}
