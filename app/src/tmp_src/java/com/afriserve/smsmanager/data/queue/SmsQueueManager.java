package com.afriserve.smsmanager.data.queue;

import android.util.Log;
import com.afriserve.smsmanager.data.dao.SmsQueueDao;
import com.afriserve.smsmanager.data.entity.SmsQueueEntity;
import com.afriserve.smsmanager.BulkSmsService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SMS Queue Manager with Circuit Breaker Pattern
 * Handles failed SMS retries with exponential backoff and circuit breaking
 */
@Singleton
public class SmsQueueManager {
    private static final String TAG = "SmsQueueManager";
    
    // Circuit breaker configuration
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5; // Failures before opening
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute cooldown
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 5000; // 5 seconds
    private static final long MAX_RETRY_DELAY_MS = 300000; // 5 minutes max
    
    private final SmsQueueDao smsQueueDao;
    private final BulkSmsService bulkSmsService;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;
    
    // Circuit breaker state
    private final AtomicBoolean circuitBreakerOpen = new AtomicBoolean(false);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong circuitBreakerOpenTime = new AtomicLong(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    
    // Queue statistics
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger exhaustedCount = new AtomicInteger(0);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    
    @Inject
    public SmsQueueManager(SmsQueueDao smsQueueDao, BulkSmsService bulkSmsService) {
        this.smsQueueDao = smsQueueDao;
        this.bulkSmsService = bulkSmsService;
        this.executor = Executors.newFixedThreadPool(2);
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        
        // Start periodic queue processing
        startQueueProcessor();
        startStatisticsUpdater();
    }
    
    /**
     * Enqueue SMS for retry
     */
    public Completable enqueueSms(String phoneNumber, String message, int simSlot, Long originalSmsId) {
        return Completable.fromAction(() -> {
            try {
                SmsQueueEntity queueEntity = new SmsQueueEntity();
                queueEntity.phoneNumber = phoneNumber;
                queueEntity.message = message;
                queueEntity.simSlot = simSlot;
                queueEntity.originalSmsId = originalSmsId;
                queueEntity.retryCount = 0;
                queueEntity.status = "PENDING";
                queueEntity.createdAt = System.currentTimeMillis();
                queueEntity.nextRetryAt = System.currentTimeMillis();
                queueEntity.lastFailureAt = null;
                queueEntity.errorMessage = null;
                
                smsQueueDao.insertQueueItem(queueEntity);
                Log.d(TAG, "Enqueued SMS for retry: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to enqueue SMS", e);
                throw e;
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Process queue items
     */
    public Completable processQueue() {
        return Completable.fromAction(() -> {
            if (circuitBreakerOpen.get()) {
                long timeSinceOpen = System.currentTimeMillis() - circuitBreakerOpenTime.get();
                if (timeSinceOpen >= CIRCUIT_BREAKER_TIMEOUT_MS) {
                    // Try to close circuit breaker
                    if (circuitBreakerOpen.compareAndSet(true, false)) {
                        failureCount.set(0);
                        Log.i(TAG, "Circuit breaker closed after timeout");
                    }
                } else {
                    Log.d(TAG, "Circuit breaker is open, skipping queue processing");
                    return;
                }
            }
            
            try {
                List<SmsQueueEntity> pendingItems = smsQueueDao.getPendingItems(System.currentTimeMillis());
                
                for (SmsQueueEntity item : pendingItems) {
                    if (circuitBreakerOpen.get()) {
                        break; // Stop processing if circuit breaker opened during processing
                    }
                    
                    processQueueItem(item);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing queue", e);
                handleFailure();
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Process individual queue item
     */
    private void processQueueItem(SmsQueueEntity item) {
        try {
            Log.d(TAG, "Processing queue item: " + item.phoneNumber + 
                  " (attempt " + (item.retryCount + 1) + ")");
            
            // Update status to PROCESSING
            item.status = "PROCESSING";
            smsQueueDao.updateQueueItem(item);
            
            // Attempt to send SMS
            boolean success = attemptSmsSend(item);
            
            if (success) {
                // Success - remove from queue
                smsQueueDao.deleteQueueItemById(item.id);
                processedCount.incrementAndGet();
                Log.d(TAG, "✅ Queue item processed successfully: " + item.phoneNumber);
                
                // Reset failure count on success
                if (failureCount.get() > 0) {
                    failureCount.set(0);
                }
                
            } else {
                // Failed - update for retry
                handleQueueItemFailure(item);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing queue item: " + item.phoneNumber, e);
            handleQueueItemFailure(item);
        }
    }
    
    /**
     * Attempt to send SMS
     */
    private boolean attemptSmsSend(SmsQueueEntity item) {
        try {
            // Use BulkSmsService to send single SMS
            BulkSmsService.ProgressCallback callback = (sent, total) -> {
                // No-op for single SMS
            };
            
            // Create a single recipient list
            List<com.afriserve.smsmanager.models.Recipient> recipients = List.of(
                new com.afriserve.smsmanager.models.Recipient(null, item.phoneNumber, null, false, null)
            );
            
            // Send synchronously for queue processing
            bulkSmsService.sendBulkSms(recipients, item.message, item.simSlot, 
                "Queue Retry", callback);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send queued SMS to: " + item.phoneNumber, e);
            return false;
        }
    }
    
    /**
     * Handle queue item failure
     */
    private void handleQueueItemFailure(SmsQueueEntity item) {
        item.retryCount++;
        item.lastFailureAt = System.currentTimeMillis();
        item.status = "FAILED";
        
        if (item.retryCount >= MAX_RETRY_ATTEMPTS) {
            // Max retries reached - mark as exhausted
            item.status = "EXHAUSTED";
            item.nextRetryAt = 0; // No more retries
            exhaustedCount.incrementAndGet();
            Log.w(TAG, "❌ Queue item exhausted: " + item.phoneNumber + 
                  " after " + item.retryCount + " attempts");
        } else {
            // Calculate next retry time with exponential backoff
            long delay = calculateRetryDelay(item.retryCount);
            item.nextRetryAt = System.currentTimeMillis() + delay;
            Log.d(TAG, "⏳ Queue item scheduled for retry: " + item.phoneNumber + 
                  " in " + (delay / 1000) + "s");
        }
        
        // Update item in database
        smsQueueDao.updateQueueItem(item);
        
        // Handle circuit breaker
        handleFailure();
    }
    
    /**
     * Calculate retry delay with exponential backoff
     */
    private long calculateRetryDelay(int retryCount) {
        // Exponential backoff: base * 2^(retryCount-1)
        long delay = BASE_RETRY_DELAY_MS * (1L << (retryCount - 1));
        
        // Add jitter to prevent thundering herd
        long jitter = (long) (Math.random() * 1000);
        
        // Cap at maximum delay
        return Math.min(delay + jitter, MAX_RETRY_DELAY_MS);
    }
    
    /**
     * Handle circuit breaker logic
     */
    private void handleFailure() {
        int failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failures >= CIRCUIT_BREAKER_THRESHOLD && !circuitBreakerOpen.get()) {
            if (circuitBreakerOpen.compareAndSet(false, true)) {
                circuitBreakerOpenTime.set(System.currentTimeMillis());
                Log.w(TAG, "🔌 Circuit breaker OPENED after " + failures + " failures");
            }
        }
    }
    
    /**
     * Get queue statistics
     */
    public QueueStatistics getQueueStatistics() {
        try {
            QueueStatistics stats = smsQueueDao.getQueueStatistics();
            stats.pendingCount = pendingCount.get();
            stats.failedCount = failedCount.get();
            stats.exhaustedCount = exhaustedCount.get();
            stats.processedCount = processedCount.get();
            stats.circuitBreakerActive = circuitBreakerOpen.get();
            
            if (circuitBreakerOpen.get()) {
                long cooldownRemaining = CIRCUIT_BREAKER_TIMEOUT_MS - 
                    (System.currentTimeMillis() - circuitBreakerOpenTime.get());
                stats.cooldownRemainingMs = Math.max(0, cooldownRemaining);
            } else {
                stats.cooldownRemainingMs = null;
            }
            
            return stats;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get queue statistics", e);
            return new QueueStatistics();
        }
    }
    
    /**
     * Clear exhausted messages
     */
    public Completable clearExhaustedMessages() {
        return Completable.fromAction(() -> {
            try {
                int cleared = smsQueueDao.deleteExhaustedItems();
                exhaustedCount.set(0);
                Log.d(TAG, "Cleared " + cleared + " exhausted messages");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear exhausted messages", e);
                throw e;
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Force run queue now
     */
    public Completable runQueueNow() {
        return processQueue();
    }
    
    /**
     * Reset circuit breaker
     */
    public void resetCircuitBreaker() {
        circuitBreakerOpen.set(false);
        failureCount.set(0);
        circuitBreakerOpenTime.set(0);
        Log.i(TAG, "Circuit breaker manually reset");
    }
    
    /**
     * Start periodic queue processor
     */
    private void startQueueProcessor() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                processQueue()
                    .subscribe(
                        () -> Log.d(TAG, "Queue processing completed"),
                        error -> Log.e(TAG, "Queue processing failed", error)
                    );
            } catch (Exception e) {
                Log.e(TAG, "Error in queue processor", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // Process every 30 seconds
    }
    
    /**
     * Start statistics updater
     */
    private void startStatisticsUpdater() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                QueueStatistics stats = smsQueueDao.getQueueStatistics();
                pendingCount.set(stats.pendingCount);
                failedCount.set(stats.failedCount);
                exhaustedCount.set(stats.exhaustedCount);
            } catch (Exception e) {
                Log.e(TAG, "Error updating statistics", e);
            }
        }, 10, 10, TimeUnit.SECONDS); // Update every 10 seconds
    }
    
    /**
     * Shutdown the queue manager
     */
    public void shutdown() {
        try {
            scheduledExecutor.shutdown();
            executor.shutdown();
            
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            
            Log.d(TAG, "Queue manager shutdown completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Interrupted during shutdown", e);
        }
    }
    
    /**
     * Queue statistics data class
     */
    public static class QueueStatistics {
        public int pendingCount = 0;
        public int processingCount = 0;
        public int failedCount = 0;
        public int exhaustedCount = 0;
        public int totalCount = 0;
        
        @androidx.room.Ignore
        public int processedCount = 0;
        
        @androidx.room.Ignore
        public boolean circuitBreakerActive = false;
        
        @androidx.room.Ignore
        public Long cooldownRemainingMs = null;
        
        public QueueStatistics() {}
    }
}
