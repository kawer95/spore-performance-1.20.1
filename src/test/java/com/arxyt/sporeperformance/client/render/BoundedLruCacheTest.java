package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundedLruCacheTest {
    @Test
    void evictsLeastRecentlyUsedEntryAndHonorsCapacityChanges() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(1, cache.get("a"));
        cache.put("c", 3);
        assertNull(cache.get("b"));
        assertEquals(1, cache.get("a"));
        assertEquals(3, cache.get("c"));

        cache.setCapacity(1);
        assertEquals(1, cache.size());
        assertEquals(3, cache.get("c"));
    }

    @Test
    void cachesNullFactoryResultsWithoutRepeatedResolution() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        int[] calls = {0};
        assertNull(cache.computeIfAbsent("missing", key -> { calls[0]++; return null; }));
        assertNull(cache.computeIfAbsent("missing", key -> { calls[0]++; return 7; }));
        assertEquals(1, calls[0]);
    }
}
