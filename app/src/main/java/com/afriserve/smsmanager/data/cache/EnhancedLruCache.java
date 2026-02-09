package com.afriserve.smsmanager.data.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enhanced LRU cache with optional TTL per-entry and convenience methods
 * Provides a small API surface compatible with callers in ContactResolver
 */
public class EnhancedLruCache<K, V> {
    private final int capacity;
    private final long defaultTtlMs;

    private static class CacheEntry<V> {
        final V value;
        final long expiresAt; // 0 means no expiry

        CacheEntry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }
    }

    private final LinkedHashMap<K, CacheEntry<V>> delegate;

    public EnhancedLruCache(int capacity) {
        this(capacity, 0L);
    }

    public EnhancedLruCache(int capacity, long defaultTtlMs) {
        this.capacity = Math.max(1, capacity);
        this.defaultTtlMs = Math.max(0, defaultTtlMs);
        this.delegate = new LinkedHashMap<K, CacheEntry<V>>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > EnhancedLruCache.this.capacity;
            }
        };
    }

    public synchronized V get(K key) {
        CacheEntry<V> entry = delegate.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            delegate.remove(key);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(K key, V value) {
        put(key, value, defaultTtlMs);
    }

    public synchronized void put(K key, V value, long ttlMs) {
        long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
        delegate.put(key, new CacheEntry<>(value, expiresAt));
    }

    /**
     * Convenience API to mark a key as "not found" with short TTL
     */
    public synchronized void putNotFound(K key, long ttlMs) {
        put(key, null, ttlMs);
    }

    public synchronized void remove(K key) {
        delegate.remove(key);
    }

    public synchronized void clear() {
        delegate.clear();
    }

    public synchronized int size() {
        return delegate.size();
    }
}
