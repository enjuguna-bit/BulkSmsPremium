package com.afriserve.smsmanager.data.compliance;

import android.telephony.TelephonyManager;
import android.util.Log;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting manager for SMS sending
 * Implements carrier-aware rate limiting to ensure compliance
 */
@Singleton
public class RateLimitManager {
    
    private static final String TAG = "RateLimitManager";
    
    // Carrier-specific rate limits (messages per minute)
    private static final Map<String, Integer> CARRIER_RATE_LIMITS = Map.of(
        "VERIZON", 30,      // 30 messages per minute
        "AT&T", 60,         // 60 messages per minute  
        "T-MOBILE", 120,    // 120 messages per minute
        "SPRINT", 60,       // 60 messages per minute
        "UNKNOWN", 30       // Conservative default
    );
    
    // Rate limit tracking
    private final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();
    private final BehaviorSubject<RateLimitStatus> rateLimitStatus = BehaviorSubject.createDefault(RateLimitStatus.OK);
    
    // Global rate limiting
    private final AtomicLong lastGlobalSendTime = new AtomicLong(0);
    private static final long MIN_GLOBAL_INTERVAL = 100; // 100ms minimum between sends
    
    private final Context context;
    
    @Inject
    public RateLimitManager(@ApplicationContext Context context) {
        this.context = context;
    }
    
    /**
     * Get the delay needed before next send for the specified carrier
     */
    public long getDelayBeforeNextSend(String phoneNumber) {
        String carrier = detectCarrier(phoneNumber);
        RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(carrier, this::createTracker);
        
        return tracker.calculateDelay();
    }
    
    /**
     * Record a successful send for rate limiting
     */
    public void recordSend(String phoneNumber) {
        String carrier = detectCarrier(phoneNumber);
        RateLimitTracker tracker = rateLimitTrackers.get(carrier);
        
        if (tracker != null) {
            tracker.recordSend();
        }
        
        // Update global send time
        lastGlobalSendTime.set(System.currentTimeMillis());
        
        Log.d(TAG, "Recorded send for carrier: " + carrier);
    }
    
    /**
     * Get current rate limit status
     */
    public Flowable<RateLimitStatus> getRateLimitStatus() {
        return rateLimitStatus.toFlowable(BackpressureStrategy.LATEST);
    }
    
    /**
     * Check if sending is currently rate limited
     */
    public boolean isRateLimited() {
        return rateLimitStatus.getValue() != RateLimitStatus.OK;
    }
    
    /**
     * Get rate limit statistics
     */
    public RateLimitStats getStats() {
        Map<String, CarrierStats> carrierStats = new HashMap<>();
        
        for (Map.Entry<String, RateLimitTracker> entry : rateLimitTrackers.entrySet()) {
            String carrier = entry.getKey();
            RateLimitTracker tracker = entry.getValue();
            
            carrierStats.put(carrier, new CarrierStats(
                carrier,
                (double) tracker.getCurrentRate(),
                tracker.getLimit(),
                tracker.isRateLimited()
            ));
        }
        
        return new RateLimitStats(carrierStats, rateLimitStatus.getValue());
    }
    
    /**
     * Reset rate limit tracking
     */
    public void resetTracking() {
        rateLimitTrackers.clear();
        rateLimitStatus.onNext(RateLimitStatus.OK);
        lastGlobalSendTime.set(0);
        Log.d(TAG, "Rate limit tracking reset");
    }
    
    /**
     * Detect carrier based on phone number
     */
    private String detectCarrier(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Simple carrier detection based on number patterns
        // In production, this would use a more sophisticated method
        if (phoneNumber.startsWith("+1")) {
            // US number - detect based on first 6 digits (NPA-NXX)
            String prefix = phoneNumber.length() >= 6 ? phoneNumber.substring(2, 6) : "";
            return detectUSCarrier(prefix);
        } else if (phoneNumber.startsWith("+44")) {
            // UK number
            return detectUKCarrier(phoneNumber);
        } else if (phoneNumber.startsWith("+91")) {
            // India number
            return detectIndiaCarrier(phoneNumber);
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Detect US carrier based on NPA-NXX prefix
     */
    private String detectUSCarrier(String prefix) {
        // Simplified carrier detection - in production would use comprehensive database
        Map<String, String> carrierMap = Map.of(
            "212", "VERIZON",
            "646", "VERIZON", 
            "917", "VERIZON",
            "213", "AT&T",
            "310", "AT&T",
            "415", "AT&T",
            "206", "T-MOBILE",
            "425", "T-MOBILE",
            "971", "T-MOBILE"
        );
        
        return carrierMap.getOrDefault(prefix, "UNKNOWN");
    }
    
    /**
     * Detect UK carrier
     */
    private String detectUKCarrier(String phoneNumber) {
        // UK carrier detection logic
        if (phoneNumber.startsWith("+4477") || phoneNumber.startsWith("+4479")) {
            return "VODAFONE";
        } else if (phoneNumber.startsWith("+4478")) {
            return "O2";
        } else if (phoneNumber.startsWith("+4474")) {
            return "EE";
        }
        return "UNKNOWN";
    }
    
    /**
     * Detect India carrier
     */
    private String detectIndiaCarrier(String phoneNumber) {
        // India carrier detection logic
        if (phoneNumber.startsWith("+9198") || phoneNumber.startsWith("+9197")) {
            return "AIRTEL";
        } else if (phoneNumber.startsWith("+9190") || phoneNumber.startsWith("+9191")) {
            return "JIO";
        } else if (phoneNumber.startsWith("+9199")) {
            return "IDEA";
        }
        return "UNKNOWN";
    }
    
    /**
     * Create rate limit tracker for carrier
     */
    private RateLimitTracker createTracker(String carrier) {
        int limit = CARRIER_RATE_LIMITS.getOrDefault(carrier, 30);
        return new RateLimitTracker(carrier, limit);
    }
    
    /**
     * Rate limit tracker for individual carriers
     */
    private static class RateLimitTracker {
        private final String carrier;
        private final int limitPerMinute;
        private final AtomicLong messagesInCurrentMinute = new AtomicLong(0);
        private final AtomicLong currentMinuteStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastSendTime = new AtomicLong(0);
        
        public RateLimitTracker(String carrier, int limitPerMinute) {
            this.carrier = carrier;
            this.limitPerMinute = limitPerMinute;
        }
        
        public long calculateDelay() {
            long now = System.currentTimeMillis();
            long currentMinute = (now / 60000) * 60000;
            
            // Reset counter if we're in a new minute
            if (currentMinuteStart.get() != currentMinute) {
                currentMinuteStart.set(currentMinute);
                messagesInCurrentMinute.set(0);
            }
            
            // Check if we've hit the rate limit
            if (messagesInCurrentMinute.get() >= limitPerMinute) {
                long nextMinute = currentMinute + 60000;
                return Math.max(0, nextMinute - now);
            }
            
            // Check minimum interval between sends
            long timeSinceLastSend = now - lastSendTime.get();
            long minInterval = 60000 / limitPerMinute; // Evenly distribute sends
            
            return Math.max(0, minInterval - timeSinceLastSend);
        }
        
        public void recordSend() {
            long now = System.currentTimeMillis();
            long currentMinute = (now / 60000) * 60000;
            
            // Reset counter if we're in a new minute
            if (currentMinuteStart.get() != currentMinute) {
                currentMinuteStart.set(currentMinute);
                messagesInCurrentMinute.set(0);
            }
            
            messagesInCurrentMinute.incrementAndGet();
            lastSendTime.set(now);
        }
        
        public int getMessagesPerMinute() {
            long now = System.currentTimeMillis();
            long currentMinute = (now / 60000) * 60000;
            
            if (currentMinuteStart.get() != currentMinute) {
                return 0;
            }
            
            return (int) messagesInCurrentMinute.get();
        }
        
        public double getCurrentRate() {
            return getMessagesPerMinute();
        }
        
        public int getLimit() {
            return limitPerMinute;
        }
        
        public boolean isRateLimited() {
            return getMessagesPerMinute() >= limitPerMinute;
        }
    }
    
    /**
     * Rate limit status enum
     */
    public enum RateLimitStatus {
        OK,
        WARNING,
        LIMITED,
        BLOCKED
    }
    
    /**
     * Rate limit statistics
     */
    public static class RateLimitStats {
        public final Map<String, CarrierStats> carrierStats;
        public final RateLimitStatus globalStatus;
        
        public RateLimitStats(Map<String, CarrierStats> carrierStats, RateLimitStatus globalStatus) {
            this.carrierStats = carrierStats;
            this.globalStatus = globalStatus;
        }
    }
    
    /**
     * Individual carrier statistics
     */
    public static class CarrierStats {
        public final String carrier;
        public final double currentRate;
        public final int limit;
        public final boolean isRateLimited;
        
        public CarrierStats(String carrier, double currentRate, int limit, boolean isRateLimited) {
            this.carrier = carrier;
            this.currentRate = currentRate;
            this.limit = limit;
            this.isRateLimited = isRateLimited;
        }
    }
}
