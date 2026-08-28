package com.arxyt.sporeperformance.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loaded calamity accounting kept separate from the general Spore population counters.
 *
 * <p>The counter is deliberately dimension-scoped: that matches Forge's spawn accounting and
 * the existing SporePerformance population limits. Saved entities are still counted after load,
 * but are never rejected by a newly enabled limit.</p>
 */
public final class CalamityPopulationCounter<K> {
    public enum Rejection {
        NONE,
        TOTAL,
        TYPE
    }

    public record Limits(int total, int perType) {}

    public record Snapshot(int total, Map<String, Integer> perType) {
        public static final Snapshot EMPTY = new Snapshot(0, Map.of());

        public Snapshot {
            perType = perType == null || perType.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new HashMap<>(perType));
        }
    }

    private final Map<K, Counts> countsByDimension = new HashMap<>();
    private final Map<UUID, Tracked<K>> trackedEntities = new HashMap<>();

    /**
     * Tracks one loaded calamity and enforces only newly spawned entities when requested.
     * Reloaded entities intentionally consume capacity but are not rejected.
     */
    public Rejection track(K dimension, UUID id, String type, Limits limits, boolean enforceLimits) {
        if (type == null || type.isBlank()) return Rejection.NONE;

        Tracked<K> previous = trackedEntities.get(id);
        if (previous != null && previous.dimension.equals(dimension)) return Rejection.NONE;
        if (previous != null) removeTracked(id, previous);

        Counts counts = countsByDimension.computeIfAbsent(dimension, ignored -> new Counts());
        Rejection rejection = enforceLimits ? exceeded(counts, type, limits) : Rejection.NONE;
        if (rejection != Rejection.NONE) {
            removeEmpty(dimension, counts);
            return rejection;
        }

        counts.add(type);
        trackedEntities.put(id, new Tracked<>(dimension, type));
        return Rejection.NONE;
    }

    public void untrack(UUID id) {
        Tracked<K> tracked = trackedEntities.remove(id);
        if (tracked != null) removeTracked(id, tracked);
    }

    public Snapshot snapshot(K dimension) {
        Counts counts = countsByDimension.get(dimension);
        return counts == null ? Snapshot.EMPTY : counts.snapshot();
    }

    public Map<K, Snapshot> snapshots() {
        Map<K, Snapshot> result = new HashMap<>();
        countsByDimension.forEach((dimension, counts) -> result.put(dimension, counts.snapshot()));
        return result;
    }

    public void clear() {
        countsByDimension.clear();
        trackedEntities.clear();
    }

    private static Rejection exceeded(Counts counts, String type, Limits limits) {
        if (limits.total() >= 0 && counts.total >= limits.total()) return Rejection.TOTAL;
        if (limits.perType() >= 0 && counts.perType.getOrDefault(type, 0) >= limits.perType()) return Rejection.TYPE;
        return Rejection.NONE;
    }

    private void removeTracked(UUID id, Tracked<K> tracked) {
        trackedEntities.remove(id);
        Counts counts = countsByDimension.get(tracked.dimension);
        if (counts == null) return;
        counts.remove(tracked.type);
        removeEmpty(tracked.dimension, counts);
    }

    private void removeEmpty(K dimension, Counts counts) {
        if (counts.isEmpty()) countsByDimension.remove(dimension);
    }

    private record Tracked<K>(K dimension, String type) {}

    private static final class Counts {
        private int total;
        private final Map<String, Integer> perType = new HashMap<>();

        private void add(String type) {
            ++total;
            perType.merge(type, 1, Integer::sum);
        }

        private void remove(String type) {
            total = Math.max(0, total - 1);
            perType.computeIfPresent(type, (ignored, count) -> count <= 1 ? null : count - 1);
        }

        private boolean isEmpty() {
            return total == 0;
        }

        private Snapshot snapshot() {
            return new Snapshot(total, perType);
        }
    }
}
