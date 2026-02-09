package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Incremental index manager for granular search index updates
 * Implements rate limiting and batch processing for efficient index maintenance
 */
@Singleton
public class IncrementalIndexManager {
    
    private static final String TAG = "IncrementalIndexManager";
    
    // Rate limiting configuration
    private static final int BATCH_SIZE = 10; // Process 10 messages at once
    private static final long RATE_LIMIT_MS = 1000; // 1 second between batches
    private static final long MAX_QUEUE_SIZE = 1000; // Prevent memory issues
    private static final long FLUSH_INTERVAL_MS = 5000; // Force flush every 5 seconds
    
    // Index operation types
    public enum IndexOperation {
        ADD, UPDATE, DELETE
    }
    
    // Index task with operation type and message
    private static class IndexTask {
        public final IndexOperation operation;
        public final SmsEntity message;
        public final long timestamp;
        
        IndexTask(IndexOperation operation, SmsEntity message) {
            this.operation = operation;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    private final SmsSearchRepository searchRepository;
    private final ScheduledExecutorService scheduler;
    private final CompositeDisposable disposables;
    
    // Task queue and state
    private final ConcurrentLinkedQueue<IndexTask> taskQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicLong lastProcessTime = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    // Statistics
    private volatile long totalTasksAdded = 0;
    private volatile long totalTasksProcessed = 0;
    private volatile long averageProcessingTime = 0;
    
    @Inject
    public IncrementalIndexManager(SmsSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IncrementalIndexManager");
            t.setDaemon(true);
            return t;
        });
        this.disposables = new CompositeDisposable();
        
        // Start periodic processing
        startPeriodicProcessing();
        
        Log.d(TAG, "IncrementalIndexManager initialized");
    }
    
    /**
     * Add message to be indexed
     */
    public void addMessage(SmsEntity message) {
        if (message == null || message.id <= 0) {
            Log.w(TAG, "Invalid message for indexing");
            return;
        }
        
        if (taskQueue.size() >= MAX_QUEUE_SIZE) {
            Log.w(TAG, "Index queue full, dropping oldest tasks");
            // Remove oldest tasks to make room
            for (int i = 0; i < BATCH_SIZE && !taskQueue.isEmpty(); i++) {
                taskQueue.poll();
            }
        }
        
        taskQueue.offer(new IndexTask(IndexOperation.ADD, message));
        totalTasksAdded++;
        
        // Trigger immediate processing if idle
        if (!isProcessing.get() && canProcessNow()) {
            scheduler.submit(this::processBatch);
        }
    }
    
    /**
     * Update message in index
     */
    public void updateMessage(SmsEntity message) {
        if (message == null || message.id <= 0) {
            Log.w(TAG, "Invalid message for index update");
            return;
        }
        
        taskQueue.offer(new IndexTask(IndexOperation.UPDATE, message));
        totalTasksAdded++;
        
        if (!isProcessing.get() && canProcessNow()) {
            scheduler.submit(this::processBatch);
        }
    }
    
    /**
     * Remove message from index
     */
    public void removeMessage(SmsEntity message) {
        if (message == null || message.id <= 0) {
            Log.w(TAG, "Invalid message for index removal");
            return;
        }
        
        taskQueue.offer(new IndexTask(IndexOperation.DELETE, message));
        totalTasksAdded++;
        
        if (!isProcessing.get() && canProcessNow()) {
            scheduler.submit(this::processBatch);
        }
    }
    
    /**
     * Force immediate processing of all pending tasks
     */
    public void flush() {
        Log.d(TAG, "Force flushing index queue");
        while (!taskQueue.isEmpty()) {
            processBatch();
        }
    }
    
    /**
     * Get current queue statistics
     */
    public IndexStats getStats() {
        return new IndexStats(
            taskQueue.size(),
            totalTasksAdded,
            totalTasksProcessed,
            processedCount.get(),
            errorCount.get(),
            averageProcessingTime
        );
    }
    
    /**
     * Clear all pending tasks
     */
    public void clearQueue() {
        taskQueue.clear();
        Log.d(TAG, "Index queue cleared");
    }
    
    /**
     * Start periodic processing
     */
    private void startPeriodicProcessing() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!taskQueue.isEmpty() && !isProcessing.get()) {
                processBatch();
            }
        }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Check if processing is allowed based on rate limiting
     */
    private boolean canProcessNow() {
        long timeSinceLastProcess = System.currentTimeMillis() - lastProcessTime.get();
        return timeSinceLastProcess >= RATE_LIMIT_MS;
    }
    
    /**
     * Process a batch of index tasks
     */
    private void processBatch() {
        if (!isProcessing.compareAndSet(false, true)) {
            return; // Already processing
        }
        
        long startTime = System.currentTimeMillis();
        List<IndexTask> batch = new ArrayList<>();
        
        try {
            // Collect batch of tasks
            for (int i = 0; i < BATCH_SIZE && !taskQueue.isEmpty(); i++) {
                IndexTask task = taskQueue.poll();
                if (task != null) {
                    batch.add(task);
                }
            }
            
            if (batch.isEmpty()) {
                return;
            }
            
            // Process batch
            disposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() -> {
                    for (IndexTask task : batch) {
                        try {
                            switch (task.operation) {
                                case ADD:
                                    searchRepository.indexMessage(task.message).blockingAwait();
                                    break;
                                case UPDATE:
                                    searchRepository.updateMessageIndex(task.message).blockingAwait();
                                    break;
                                case DELETE:
                                    searchRepository.removeMessageFromIndex(task.message).blockingAwait();
                                    break;
                            }
                            processedCount.incrementAndGet();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to process index task: " + task.operation + " for message " + 
                                (task.message != null ? task.message.id : "null"), e);
                            errorCount.incrementAndGet();
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                    () -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        updateProcessingStats(processingTime, batch.size());
                        Log.d(TAG, "Processed index batch of " + batch.size() + " tasks in " + processingTime + "ms");
                    },
                    error -> {
                        Log.e(TAG, "Batch processing failed", error);
                        errorCount.addAndGet(batch.size());
                    }
                )
            );
            
            totalTasksProcessed += batch.size();
            lastProcessTime.set(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "Error during batch processing", e);
            errorCount.incrementAndGet();
        } finally {
            isProcessing.set(false);
        }
    }
    
    /**
     * Update processing statistics
     */
    private void updateProcessingStats(long processingTime, int batchSize) {
        // Update average processing time (exponential moving average)
        if (averageProcessingTime == 0) {
            averageProcessingTime = processingTime;
        } else {
            averageProcessingTime = (long)((averageProcessingTime * 0.9) + (processingTime * 0.1));
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        try {
            Log.d(TAG, "Shutting down IncrementalIndexManager");
            
            // Process remaining tasks
            flush();
            
            // Shutdown scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Dispose RxJava subscriptions
            disposables.clear();
            
            Log.d(TAG, "IncrementalIndexManager shutdown completed");
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted during shutdown", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Index statistics data class
     */
    public static class IndexStats {
        public final int queueSize;
        public final long totalTasksAdded;
        public final long totalTasksProcessed;
        public final long successfullyProcessed;
        public final long errors;
        public final long averageProcessingTime;
        
        IndexStats(int queueSize, long totalTasksAdded, long totalTasksProcessed, 
                  long successfullyProcessed, long errors, long averageProcessingTime) {
            this.queueSize = queueSize;
            this.totalTasksAdded = totalTasksAdded;
            this.totalTasksProcessed = totalTasksProcessed;
            this.successfullyProcessed = successfullyProcessed;
            this.errors = errors;
            this.averageProcessingTime = averageProcessingTime;
        }
        
        @Override
        public String toString() {
            return String.format("IndexStats{queue=%d, added=%d, processed=%d, success=%d, errors=%d, avgTime=%dms}",
                queueSize, totalTasksAdded, totalTasksProcessed, successfullyProcessed, errors, averageProcessingTime);
        }
    }
}
