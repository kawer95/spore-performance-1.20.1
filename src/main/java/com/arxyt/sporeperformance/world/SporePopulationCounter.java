package com.arxyt.sporeperformance.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Constant-time, loaded-entity population accounting.  The generic dimension key deliberately
 * keeps this class independent from Minecraft so its cap and unload behaviour can be unit tested.
 */
public final class SporePopulationCounter<K> {
    public enum Rejection {
        NONE,
        FUNGAL_UNITS,
        MOUNDS,
        TENDRILS
    }

    public record Category(boolean fungalUnit, boolean mound, boolean tendril) {
        public static final Category NONE = new Category(false, false, false);

        public boolean isTracked() {
            return fungalUnit || mound || tendril;
        }
    }

    public record Limits(int fungalUnits, int mounds, int tendrils) {}

    public record Snapshot(int fungalUnits, int mounds, int tendrils) {
        public static final Snapshot EMPTY = new Snapshot(0, 0, 0);
    }

    private final Map<K, Counts> countsByDimension = new HashMap<>();
    private final Map<UUID, Tracked<K>> trackedEntities = new HashMap<>();

    /**
     * Tracks an accepted entity, or returns the cap that rejects a fresh entity.  Reloaded saved
     * entities call this with {@code enforceLimits=false}; they must never be deleted by a cap.
     */
    public Rejection track(K dimension, UUID id, Category category, Limits limits, boolean enforceLimits) {
        if (!category.isTracked()) return Rejection.NONE;

        Tracked<K> previous = trackedEntities.get(id);
        if (previous != null && previous.dimension.equals(dimension)) return Rejection.NONE;
        if (previous != null) removeTracked(id, previous);

        Counts counts = countsByDimension.computeIfAbsent(dimension, ignored -> new Counts());
        Rejection rejection = enforceLimits ? exceeded(counts, category, limits) : Rejection.NONE;
        if (rejection != Rejection.NONE) {
            removeEmpty(dimension, counts);
            return rejection;
        }

        counts.add(category);
        trackedEntities.put(id, new Tracked<>(dimension, category));
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

    private static Rejection exceeded(Counts counts, Category category, Limits limits) {
        if (category.fungalUnit && limits.fungalUnits > 0 && counts.fungalUnits >= limits.fungalUnits) {
            return Rejection.FUNGAL_UNITS;
        }
        if (category.mound && limits.mounds > 0 && counts.mounds >= limits.mounds) {
            return Rejection.MOUNDS;
        }
        if (category.tendril && limits.tendrils > 0 && counts.tendrils >= limits.tendrils) {
            return Rejection.TENDRILS;
        }
        return Rejection.NONE;
    }

    private void removeTracked(UUID id, Tracked<K> tracked) {
        trackedEntities.remove(id);
        Counts counts = countsByDimension.get(tracked.dimension);
        if (counts == null) return;
        counts.remove(tracked.category);
        removeEmpty(tracked.dimension, counts);
    }

    private void removeEmpty(K dimension, Counts counts) {
        if (counts.isEmpty()) countsByDimension.remove(dimension);
    }

    private record Tracked<K>(K dimension, Category category) {}

    private static final class Counts {
        private int fungalUnits;
        private int mounds;
        private int tendrils;

        private void add(Category category) {
            if (category.fungalUnit) ++fungalUnits;
            if (category.mound) ++mounds;
            if (category.tendril) ++tendrils;
        }

        private void remove(Category category) {
            if (category.fungalUnit) fungalUnits = Math.max(0, fungalUnits - 1);
            if (category.mound) mounds = Math.max(0, mounds - 1);
            if (category.tendril) tendrils = Math.max(0, tendrils - 1);
        }

        private boolean isEmpty() {
            return fungalUnits == 0 && mounds == 0 && tendrils == 0;
        }

        private Snapshot snapshot() {
            return new Snapshot(fungalUnits, mounds, tendrils);
        }
    }
}
