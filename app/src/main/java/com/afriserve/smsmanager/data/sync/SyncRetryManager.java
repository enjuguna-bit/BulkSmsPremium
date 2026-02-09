package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Retry manager for failed sync operations with detailed error diagnostics
 * Implements exponential backoff and comprehensive error tracking
 */
@Singleton
public class SyncRetryManager {
    
    private static final String TAG = "SyncRetryManager";
    
    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000; // 1 second
    private static final long MAX_BACKOFF_MS = 30000; // 30 seconds
    private static final double BACKOFF_MULTIPLIER = 2.0;
    
    // Error tracking
    private final ConcurrentHashMap<String, RetryInfo> retryAttempts = new ConcurrentHashMap<>();
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong successfulRetries = new AtomicLong(0);
    private final AtomicLong failedRetries = new AtomicLong(0);
    
    // Error diagnostics
    private final ConcurrentHashMap<String, ErrorStats> errorStats = new ConcurrentHashMap<>();
    
    @Inject
    public SyncRetryManager() {
        Log.d(TAG, "SyncRetryManager initialized");
    }
    
    /**
     * Execute operation with retry logic
     */
    public Completable executeWithRetry(String operationId, Completable operation) {
        return Completable.defer(() -> {
            RetryInfo retryInfo = retryAttempts.computeIfAbsent(operationId, 
                k -> new RetryInfo(operationId));
            
            return operation
                .doOnComplete(() -> {
                    // Success - clear retry info
                    retryAttempts.remove(operationId);
                    successfulRetries.incrementAndGet();
                    Log.d(TAG, "Operation " + operationId + " succeeded");
                })
                .doOnError(error -> {
                    // Failure - record error and schedule retry if needed
                    recordError(operationId, error, retryInfo);
                })
                .retryWhen(errors -> errors
                    .zipWith(Flowable.range(1, MAX_RETRY_ATTEMPTS + 1), (error, attempt) -> {
                        if (attempt > MAX_RETRY_ATTEMPTS) {
                            throw new RuntimeException("Max retry attempts exceeded for " + operationId, error);
                        }
                        
                        retryInfo.attemptCount = attempt;
                        retryInfo.lastError = error;
                        retryInfo.lastAttemptTime = System.currentTimeMillis();
                        
                        long backoffTime = calculateBackoffTime(attempt);
                        retryInfo.nextRetryTime = System.currentTimeMillis() + backoffTime;
                        
                        Log.w(TAG, String.format("Operation %s failed (attempt %d/%d), retrying in %dms: %s", 
                            operationId, attempt, MAX_RETRY_ATTEMPTS, backoffTime, error.getMessage()));
                        
                        return backoffTime;
                    })
                    .flatMap(backoffTime -> Flowable.timer(backoffTime, TimeUnit.MILLISECONDS))
                )
                .subscribeOn(Schedulers.io());
        });
    }
    
    /**
     * Execute Single operation with retry logic
     */
    public <T> Single<T> executeSingleWithRetry(String operationId, Single<T> operation) {
        return Single.defer(() -> {
            RetryInfo retryInfo = retryAttempts.computeIfAbsent(operationId, 
                k -> new RetryInfo(operationId));
            
            return operation
                .doOnSuccess(result -> {
                    retryAttempts.remove(operationId);
                    successfulRetries.incrementAndGet();
                    Log.d(TAG, "Single operation " + operationId + " succeeded");
                })
                .doOnError(error -> {
                    recordError(operationId, error, retryInfo);
                })
                .retryWhen(errors -> errors
                    .zipWith(Flowable.range(1, MAX_RETRY_ATTEMPTS + 1), (error, attempt) -> {
                        if (attempt > MAX_RETRY_ATTEMPTS) {
                            throw new RuntimeException("Max retry attempts exceeded for " + operationId, error);
                        }
                        
                        retryInfo.attemptCount = attempt;
                        retryInfo.lastError = error;
                        retryInfo.lastAttemptTime = System.currentTimeMillis();
                        
                        long backoffTime = calculateBackoffTime(attempt);
                        retryInfo.nextRetryTime = System.currentTimeMillis() + backoffTime;
                        
                        Log.w(TAG, String.format("Single operation %s failed (attempt %d/%d), retrying in %dms: %s", 
                            operationId, attempt, MAX_RETRY_ATTEMPTS, backoffTime, error.getMessage()));
                        
                        return backoffTime;
                    })
                    .flatMap(backoffTime -> Flowable.timer(backoffTime, TimeUnit.MILLISECONDS))
                )
                .subscribeOn(Schedulers.io());
        });
    }
    
    /**
     * Get retry information for an operation
     */
    public RetryInfo getRetryInfo(String operationId) {
        return retryAttempts.get(operationId);
    }
    
    /**
     * Get all retry attempts
     */
    public Map<String, RetryInfo> getAllRetryAttempts() {
        return new HashMap<>(retryAttempts);
    }
    
    /**
     * Clear retry information for an operation
     */
    public void clearRetry(String operationId) {
        retryAttempts.remove(operationId);
        Log.d(TAG, "Cleared retry info for " + operationId);
    }
    
    /**
     * Clear all retry attempts
     */
    public void clearAllRetries() {
        retryAttempts.clear();
        Log.d(TAG, "Cleared all retry attempts");
    }
    
    /**
     * Get comprehensive retry statistics
     */
    public RetryStats getRetryStats() {
        return new RetryStats(
            totalRetries.get(),
            successfulRetries.get(),
            failedRetries.get(),
            retryAttempts.size(),
            new HashMap<>(errorStats)
        );
    }
    
    /**
     * Calculate exponential backoff time
     */
    private long calculateBackoffTime(int attempt) {
        long backoff = (long) (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
        return Math.min(backoff, MAX_BACKOFF_MS);
    }
    
    /**
     * Record error for diagnostics
     */
    private void recordError(String operationId, Throwable error, RetryInfo retryInfo) {
        String errorType = error.getClass().getSimpleName();
        String errorMessage = error.getMessage();
        
        // Update error statistics
        ErrorStats stats = errorStats.computeIfAbsent(errorType, k -> new ErrorStats(errorType));
        stats.incrementCount();
        stats.addSample(errorMessage);
        
        // Update retry info
        retryInfo.lastError = error;
        retryInfo.lastAttemptTime = System.currentTimeMillis();
        
        totalRetries.incrementAndGet();
        
        Log.e(TAG, "Recorded error for " + operationId + ": " + errorType + " - " + errorMessage);
    }
    
    /**
     * Retry information for an operation
     */
    public static class RetryInfo {
        public final String operationId;
        public int attemptCount = 0;
        public long lastAttemptTime = 0;
        public long nextRetryTime = 0;
        public Throwable lastError;
        
        RetryInfo(String operationId) {
            this.operationId = operationId;
        }
        
        public boolean isPendingRetry() {
            return nextRetryTime > System.currentTimeMillis();
        }
        
        public long getTimeUntilNextRetry() {
            return Math.max(0, nextRetryTime - System.currentTimeMillis());
        }
        
        @Override
        public String toString() {
            return String.format("RetryInfo{id=%s, attempts=%d, nextRetry=%dms, error=%s}",
                operationId, attemptCount, getTimeUntilNextRetry(), 
                lastError != null ? lastError.getClass().getSimpleName() : "none");
        }
    }
    
    /**
     * Error statistics for diagnostics
     */
    public static class ErrorStats {
        public final String errorType;
        public final AtomicInteger count = new AtomicInteger(0);
        public final List<String> recentMessages = new ArrayList<>();
        public final long firstOccurrence;
        public long lastOccurrence;
        
        ErrorStats(String errorType) {
            this.errorType = errorType;
            this.firstOccurrence = System.currentTimeMillis();
            this.lastOccurrence = this.firstOccurrence;
        }
        
        void incrementCount() {
            count.incrementAndGet();
            lastOccurrence = System.currentTimeMillis();
        }
        
        void addSample(String message) {
            if (message != null) {
                recentMessages.add(message);
                // Keep only last 10 messages
                if (recentMessages.size() > 10) {
                    recentMessages.remove(0);
                }
            }
        }
        
        @Override
        public String toString() {
            return String.format("ErrorStats{type=%s, count=%d, first=%d, last=%d}",
                errorType, count.get(), firstOccurrence, lastOccurrence);
        }
    }
    
    /**
     * Comprehensive retry statistics
     */
    public static class RetryStats {
        public final long totalRetries;
        public final long successfulRetries;
        public final long failedRetries;
        public final int pendingRetries;
        public final Map<String, ErrorStats> errorBreakdown;
        
        RetryStats(long totalRetries, long successfulRetries, long failedRetries, 
                  int pendingRetries, Map<String, ErrorStats> errorBreakdown) {
            this.totalRetries = totalRetries;
            this.successfulRetries = successfulRetries;
            this.failedRetries = failedRetries;
            this.pendingRetries = pendingRetries;
            this.errorBreakdown = errorBreakdown;
        }
        
        public double getSuccessRate() {
            return totalRetries > 0 ? (double) successfulRetries / totalRetries : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("RetryStats{total=%d, success=%d, failed=%d, pending=%d, rate=%.2f%%}",
                totalRetries, successfulRetries, failedRetries, pendingRetries, getSuccessRate() * 100);
        }
    }
}
