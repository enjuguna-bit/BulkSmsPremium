package com.afriserve.smsmanager.data.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Enhanced LRU cache with optional TTL per-entry and lightweight statistics.
 *
 * <p>The cache supports a "not found" sentinel so callers can cache misses
 * without changing their public return types.</p>
 */
public class EnhancedLruCache<K, V> {
    private static final Object NULL_VALUE = new Object();

    private final int capacity;
    private final long defaultTtlMs;
    private long hitCount;
    private long missCount;
    private long putCount;
    private long evictionCount;
    private long expiredCount;

    private static final class CacheEntry {
        final Object value;
        final long expiresAt; // 0 means no expiry

        CacheEntry(Object value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
        }
    }

    private final LinkedHashMap<K, CacheEntry> delegate;

    public EnhancedLruCache(int capacity) {
        this(capacity, 0L);
    }

    public EnhancedLruCache(int capacity, long defaultTtlMs) {
        this.capacity = Math.max(1, capacity);
        this.defaultTtlMs = Math.max(0, defaultTtlMs);
        this.delegate = new LinkedHashMap<K, CacheEntry>(this.capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry> eldest) {
                boolean shouldRemove = size() > EnhancedLruCache.this.capacity;
                if (shouldRemove) {
                    EnhancedLruCache.this.evictionCount++;
                }
                return shouldRemove;
            }
        };
    }

    public synchronized V get(K key) {
        CacheEntry entry = delegate.get(key);
        if (entry == null) {
            missCount++;
            return null;
        }
        if (entry.isExpired()) {
            delegate.remove(key);
            expiredCount++;
            missCount++;
            return null;
        }
        hitCount++;
        return decodeValue(entry.value);
    }

    public synchronized boolean containsKey(K key) {
        CacheEntry entry = delegate.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            delegate.remove(key);
            expiredCount++;
            return false;
        }
        return true;
    }

    public synchronized void put(K key, V value) {
        put(key, value, defaultTtlMs);
    }

    public synchronized void put(K key, V value, long ttlMs) {
        putInternal(key, encodeValue(value), ttlMs);
        putCount++;
    }

    /**
     * Convenience API to mark a key as "not found" with short TTL.
     */
    public synchronized void putNotFound(K key, long ttlMs) {
        putInternal(key, NULL_VALUE, ttlMs);
        putCount++;
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

    public synchronized int cleanupExpired() {
        int removed = 0;
        Iterator<Map.Entry<K, CacheEntry>> iterator = delegate.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, CacheEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        expiredCount += removed;
        return removed;
    }

    public synchronized CacheStats getStats() {
        return new CacheStats(
                capacity,
                delegate.size(),
                hitCount,
                missCount,
                putCount,
                evictionCount,
                expiredCount
        );
    }

    private void putInternal(K key, Object value, long ttlMs) {
        long effectiveTtlMs = ttlMs >= 0 ? ttlMs : defaultTtlMs;
        long expiresAt = effectiveTtlMs > 0 ? System.currentTimeMillis() + effectiveTtlMs : 0L;
        delegate.put(key, new CacheEntry(value, expiresAt));
    }

    @SuppressWarnings("unchecked")
    private V decodeValue(Object value) {
        return value == NULL_VALUE ? null : (V) value;
    }

    private Object encodeValue(V value) {
        return value == null ? NULL_VALUE : value;
    }

    public static final class CacheStats {
        public final int capacity;
        public final int size;
        public final long hits;
        public final long misses;
        public final long puts;
        public final long evictions;
        public final long expirations;

        CacheStats(int capacity, int size, long hits, long misses, long puts, long evictions, long expirations) {
            this.capacity = capacity;
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.evictions = evictions;
            this.expirations = expirations;
        }

        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }

        @Override
        public String toString() {
            return "CacheStats{size=" + size +
                    ", capacity=" + capacity +
                    ", hits=" + hits +
                    ", misses=" + misses +
                    ", evictions=" + evictions +
                    ", expirations=" + expirations +
                    ", hitRate=" + String.format("%.2f%%", getHitRate() * 100) +
                    '}';
        }
    }
}
