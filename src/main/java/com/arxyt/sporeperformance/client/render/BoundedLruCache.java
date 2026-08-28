package com.arxyt.sporeperformance.client.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Small render-thread LRU with deterministic eviction and no background maintenance. */
public final class BoundedLruCache<K, V> {
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, 0.75F, true);
    private int capacity;

    public BoundedLruCache(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> factory) {
        V existing = values.get(key);
        if (existing != null || values.containsKey(key)) return existing;
        V created = factory.apply(key);
        values.put(key, created);
        trim();
        return created;
    }

    public V get(K key) {
        return values.get(key);
    }

    public boolean containsKey(K key) {
        return values.containsKey(key);
    }

    public void put(K key, V value) {
        values.put(key, value);
        trim();
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        trim();
    }

    public void removeIf(java.util.function.Predicate<K> predicate) {
        values.keySet().removeIf(predicate);
    }

    public int size() {
        return values.size();
    }

    public void clear() {
        values.clear();
    }

    private void trim() {
        while (values.size() > capacity) {
            Map.Entry<K, V> eldest = values.entrySet().iterator().next();
            values.remove(eldest.getKey());
        }
    }
}
