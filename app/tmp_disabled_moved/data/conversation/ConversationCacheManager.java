package com.bulksms.smsmanager.data.conversation;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.cache.EnhancedLruCache;
import com.bulksms.smsmanager.data.utils.PhoneNumberUtils;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Enhanced conversation manager with intelligent caching and optimization
 * Provides conversation caching, preloading, and performance optimization
 */
@Singleton
public class ConversationCacheManager {
    
    private static final String TAG = "ConversationCacheManager";
    
    // Cache configuration
    private static final int CONVERSATION_CACHE_SIZE = 50;
    private static final int MESSAGE_CACHE_SIZE = 200;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes
    private static final long PRELOAD_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes
    
    private final EnhancedLruCache<String, ConversationEntity> conversationCache;
    private final EnhancedLruCache<String, List<SmsEntity>> messageCache;
    private final ConcurrentHashMap<String, Long> lastAccessTimes;
    private final AtomicLong totalHits = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);
    private final AtomicLong preloadCount = new AtomicLong(0);
    
    // Conversation statistics
    private volatile int totalConversations = 0;
    private volatile long lastUpdateTime = 0;
    
    @Inject
    public ConversationCacheManager() {
        this.conversationCache = new EnhancedLruCache<>(CONVERSATION_CACHE_SIZE, CACHE_TTL_MS);
        this.messageCache = new EnhancedLruCache<>(MESSAGE_CACHE_SIZE, CACHE_TTL_MS);
        this.lastAccessTimes = new ConcurrentHashMap<>();
        
        Log.d(TAG, "ConversationCacheManager initialized");
    }
    
    /**
     * Get conversation from cache with intelligent preloading
     */
    public Single<ConversationEntity> getConversation(String phoneNumber) {
        return Single.fromCallable(() -> {
            String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return null;
            }
            
            // Update access time
            lastAccessTimes.put(normalizedPhone, System.currentTimeMillis());
            
            // Try cache first
            ConversationEntity cached = conversationCache.get(normalizedPhone);
            if (cached != null) {
                totalHits.incrementAndGet();
                
                // Check if preloading is needed
                if (shouldPreloadMessages(normalizedPhone)) {
                    preloadMessagesAsync(normalizedPhone);
                }
                
                return cached;
            }
            
            totalMisses.incrementAndGet();
            return null;
            
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Put conversation in cache
     */
    public void putConversation(ConversationEntity conversation) {
        if (conversation == null || conversation.phoneNumber == null) {
            return;
        }
        
        String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(conversation.phoneNumber);
        if (normalizedPhone == null) {
            return;
        }
        
        conversationCache.put(normalizedPhone, conversation);
        lastAccessTimes.put(normalizedPhone, System.currentTimeMillis());
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Get messages for conversation from cache
     */
    public Single<List<SmsEntity>> getMessages(String phoneNumber) {
        return Single.fromCallable(() -> {
            String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
            if (normalizedPhone == null) {
                return Collections.<SmsEntity>emptyList();
            }
            
            List<SmsEntity> cached = messageCache.get(normalizedPhone);
            if (cached != null) {
                totalHits.incrementAndGet();
                return new ArrayList<>(cached); // Return copy to prevent modification
            }
            
            totalMisses.incrementAndGet();
            return Collections.<SmsEntity>emptyList();
            
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Put messages in cache
     */
    public void putMessages(String phoneNumber, List<SmsEntity> messages) {
        if (phoneNumber == null || messages == null) {
            return;
        }
        
        String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return;
        }
        
        // Store copy to prevent external modification
        messageCache.put(normalizedPhone, new ArrayList<>(messages));
        lastAccessTimes.put(normalizedPhone, System.currentTimeMillis());
    }
    
    /**
     * Add single message to cached conversation
     */
    public void addMessage(String phoneNumber, SmsEntity message) {
        if (phoneNumber == null || message == null) {
            return;
        }
        
        String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return;
        }
        
        List<SmsEntity> messages = messageCache.get(normalizedPhone);
        if (messages != null) {
            // Add message to existing list
            messages.add(0, message); // Add at beginning for chronological order
            
            // Update cache with modified list
            messageCache.put(normalizedPhone, messages);
        } else {
            // Create new list with single message
            List<SmsEntity> newMessages = new ArrayList<>();
            newMessages.add(message);
            messageCache.put(normalizedPhone, newMessages);
        }
        
        lastAccessTimes.put(normalizedPhone, System.currentTimeMillis());
    }
    
    /**
     * Remove conversation and messages from cache
     */
    public void removeConversation(String phoneNumber) {
        String normalizedPhone = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            return;
        }
        
        conversationCache.remove(normalizedPhone);
        messageCache.remove(normalizedPhone);
        lastAccessTimes.remove(normalizedPhone);
    }
    
    /**
     * Clear all caches
     */
    public void clearAll() {
        conversationCache.clear();
        messageCache.clear();
        lastAccessTimes.clear();
        totalHits.set(0);
        totalMisses.set(0);
        preloadCount.set(0);
        
        Log.d(TAG, "All caches cleared");
    }
    
    /**
     * Clean up expired entries
     */
    public CacheCleanupResult cleanupExpired() {
        int expiredConversations = conversationCache.cleanupExpired();
        int expiredMessages = messageCache.cleanupExpired();
        
        // Also clean up old access times
        long currentTime = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : lastAccessTimes.entrySet()) {
            if (currentTime - entry.getValue() > CACHE_TTL_MS * 2) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        for (String key : expiredKeys) {
            lastAccessTimes.remove(key);
        }
        
        return new CacheCleanupResult(expiredConversations, expiredMessages, expiredKeys.size());
    }
    
    /**
     * Get recently accessed conversations for preloading
     */
    public List<String> getRecentlyAccessedConversations(int limit) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(lastAccessTimes.entrySet());
        
        // Sort by access time (most recent first)
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        
        return result;
    }
    
    /**
     * Check if messages should be preloaded for this conversation
     */
    private boolean shouldPreloadMessages(String phoneNumber) {
        Long lastAccess = lastAccessTimes.get(phoneNumber);
        if (lastAccess == null) {
            return false;
        }
        
        // Preload if recently accessed but messages not cached
        long timeSinceAccess = System.currentTimeMillis() - lastAccess;
        return timeSinceAccess < PRELOAD_THRESHOLD_MS && messageCache.get(phoneNumber) == null;
    }
    
    /**
     * Preload messages asynchronously (placeholder for actual implementation)
     */
    private void preloadMessagesAsync(String phoneNumber) {
        // This would typically call the repository to load messages
        // For now, just increment the counter
        preloadCount.incrementAndGet();
        Log.d(TAG, "Preloading messages for " + phoneNumber);
    }
    
    /**
     * Get cache statistics
     */
    public ConversationCacheStats getStats() {
        return new ConversationCacheStats(
            conversationCache.getStats(),
            messageCache.getStats(),
            totalHits.get(),
            totalMisses.get(),
            preloadCount.get(),
            lastAccessTimes.size(),
            totalConversations,
            lastUpdateTime
        );
    }
    
    /**
     * Optimize cache based on usage patterns
     */
    public void optimizeCache() {
        // Get recently accessed conversations
        List<String> recentConversations = getRecentlyAccessedConversations(20);
        
        // Ensure recent conversations are in cache
        for (String phoneNumber : recentConversations) {
            // If conversation is not in cache but is frequently accessed,
            // it will be loaded on next access and cached
        }
        
        // Clean up expired entries
        CacheCleanupResult cleanupResult = cleanupExpired();
        
        Log.d(TAG, "Cache optimization completed: " + cleanupResult);
    }
    
    /**
     * Cache cleanup result
     */
    public static class CacheCleanupResult {
        public final int expiredConversations;
        public final int expiredMessages;
        public final int expiredAccessTimes;
        
        CacheCleanupResult(int expiredConversations, int expiredMessages, int expiredAccessTimes) {
            this.expiredConversations = expiredConversations;
            this.expiredMessages = expiredMessages;
            this.expiredAccessTimes = expiredAccessTimes;
        }
        
        @Override
        public String toString() {
            return String.format("CacheCleanupResult{conversations=%d, messages=%d, accessTimes=%d}",
                expiredConversations, expiredMessages, expiredAccessTimes);
        }
    }
    
    /**
     * Conversation cache statistics
     */
    public static class ConversationCacheStats {
        public final EnhancedLruCache.CacheStats conversationStats;
        public final EnhancedLruCache.CacheStats messageStats;
        public final long totalHits;
        public final long totalMisses;
        public final long preloadCount;
        public final int activeConversations;
        public final int totalConversations;
        public final long lastUpdateTime;
        
        ConversationCacheStats(EnhancedLruCache.CacheStats conversationStats,
                              EnhancedLruCache.CacheStats messageStats,
                              long totalHits, long totalMisses, long preloadCount,
                              int activeConversations, int totalConversations, long lastUpdateTime) {
            this.conversationStats = conversationStats;
            this.messageStats = messageStats;
            this.totalHits = totalHits;
            this.totalMisses = totalMisses;
            this.preloadCount = preloadCount;
            this.activeConversations = activeConversations;
            this.totalConversations = totalConversations;
            this.lastUpdateTime = lastUpdateTime;
        }
        
        public double getHitRate() {
            long total = totalHits + totalMisses;
            return total > 0 ? (double) totalHits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("ConversationCacheStats{hits=%d, misses=%d, hitRate=%.2f%%, preloads=%d, active=%d}",
                totalHits, totalMisses, getHitRate() * 100, preloadCount, activeConversations);
        }
    }
}
