package com.bulksms.smsmanager.data.cache;

import android.util.LruCache;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Enhanced LRU cache with TTL support and statistics
 * Provides intelligent cache management with expiration and size limits
 */
public class EnhancedLruCache<K, V> {
    
    private final LruCache<K, CacheEntry<V>> cache;
    private final ConcurrentHashMap<K, Long> accessTimes;
    private final long defaultTtlMs;
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    /**
     * Cache entry with TTL support
     */
    private static class CacheEntry<V> {
        public final V value;
        public final long timestamp;
        public final long ttlMs;
        public final boolean isNotFound;
        
        CacheEntry(V value, long ttlMs, boolean isNotFound) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.ttlMs = ttlMs;
            this.isNotFound = isNotFound;
        }
        
        boolean isExpired() {
            return ttlMs > 0 && (System.currentTimeMillis() - timestamp) > ttlMs;
        }
    }
    
    /**
     * Constructor for enhanced LRU cache
     * 
     * @param maxSize Maximum number of entries in cache
     * @param defaultTtlMs Default TTL in milliseconds (0 = no expiration)
     */
    public EnhancedLruCache(int maxSize, long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
        this.accessTimes = new ConcurrentHashMap<>();
        
        this.cache = new LruCache<K, CacheEntry<V>>(maxSize) {
            @Override
            protected int sizeOf(K key, CacheEntry<V> entry) {
                // Estimate size based on key and value
                return key.toString().length() + (entry.value != null ? entry.value.toString().length() : 0);
            }
            
            @Override
            protected void entryRemoved(boolean evicted, K key, CacheEntry<V> oldValue, CacheEntry<V> newValue) {
                if (evicted) {
                    evictionCount.incrementAndGet();
                }
                accessTimes.remove(key);
            }
        };
    }
    
    /**
     * Get value from cache
     */
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        
        if (entry == null) {
            totalMisses.incrementAndGet();
            return null;
        }
        
        // Check expiration
        if (entry.isExpired()) {
            cache.remove(key);
            accessTimes.remove(key);
            totalMisses.incrementAndGet();
            return null;
        }
        
        // Update access time
        accessTimes.put(key, System.currentTimeMillis());
        totalHits.incrementAndGet();
        
        return entry.value;
    }
    
    /**
     * Put value in cache with default TTL
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMs, false);
    }
    
    /**
     * Put value in cache with custom TTL
     */
    public void put(K key, V value, long ttlMs) {
        put(key, value, ttlMs, false);
    }
    
    /**
     * Put "not found" result in cache with shorter TTL
     */
    public void putNotFound(K key, long notFoundTtlMs) {
        put(key, null, notFoundTtlMs, true);
    }
    
    /**
     * Internal put method
     */
    private void put(K key, V value, long ttlMs, boolean isNotFound) {
        CacheEntry<V> entry = new CacheEntry<>(value, ttlMs, isNotFound);
        cache.put(key, entry);
        accessTimes.put(key, System.currentTimeMillis());
    }
    
    /**
     * Remove entry from cache
     */
    public V remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        accessTimes.remove(key);
        return entry != null ? entry.value : null;
    }
    
    /**
     * Clear all entries
     */
    public void clear() {
        cache.evictAll();
        accessTimes.clear();
    }
    
    /**
     * Get cache size
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Get max cache size
     */
    public int maxSize() {
        return cache.maxSize();
    }
    
    /**
     * Check if cache is empty
     */
    public boolean isEmpty() {
        return cache.size() == 0;
    }
    
    /**
     * Get hit rate
     */
    public double getHitRate() {
        long hits = totalHits.get();
        long misses = totalMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(
            cache.size(),
            cache.maxSize(),
            totalHits.get(),
            totalMisses.get(),
            evictionCount.get(),
            getHitRate()
        );
    }
    
    /**
     * Get all keys (for debugging)
     */
    public List<K> getKeys() {
        return new ArrayList<>(cache.snapshot().keySet());
    }
    
    /**
     * Get expired entries count
     */
    public int getExpiredEntriesCount() {
        int expiredCount = 0;
        
        for (CacheEntry<V> entry : cache.snapshot().values()) {
            if (entry.isExpired()) {
                expiredCount++;
            }
        }
        
        return expiredCount;
    }
    
    /**
     * Clean up expired entries
     */
    public int cleanupExpired() {
        int cleanedCount = 0;
        List<K> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<K, CacheEntry<V>> entry : cache.snapshot().entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (K key : expiredKeys) {
            cache.remove(key);
            accessTimes.remove(key);
            cleanedCount++;
        }
        
        return cleanedCount;
    }
    
    /**
     * Get least recently used key
     */
    public K getLruKey() {
        if (accessTimes.isEmpty()) {
            return null;
        }
        
        K lruKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<K, Long> entry : accessTimes.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                lruKey = entry.getKey();
            }
        }
        
        return lruKey;
    }
    
    /**
     * Cache statistics data class
     */
    public static class CacheStats {
        public final int currentSize;
        public final int maxSize;
        public final long totalHits;
        public final long totalMisses;
        public final long evictionCount;
        public final double hitRate;
        
        CacheStats(int currentSize, int maxSize, long totalHits, long totalMisses, 
                  long evictionCount, double hitRate) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.totalHits = totalHits;
            this.totalMisses = totalMisses;
            this.evictionCount = evictionCount;
            this.hitRate = hitRate;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d/%d, hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}",
                currentSize, maxSize, totalHits, totalMisses, evictionCount, hitRate * 100);
        }
    }
}
