package com.afriserve.smsmanager.data.search;

import android.util.Log;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.afriserve.smsmanager.data.dao.SmsSearchDao;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Aggressive index optimization manager for search performance
 * Implements proactive optimization strategies including vacuum, analyze, and rebuilding
 */
@Singleton
public class IndexOptimizationManager {
    
    private static final String TAG = "IndexOptimizationManager";
    
    // Optimization schedule configuration
    private static final long INITIAL_DELAY_MS = 5 * 60 * 1000; // 5 minutes after startup
    private static final long OPTIMIZATION_INTERVAL_MS = 30 * 60 * 1000; // Every 30 minutes
    private static final long AGGRESSIVE_INTERVAL_MS = 2 * 60 * 60 * 1000; // Every 2 hours for aggressive
    private static final long MAINTENANCE_INTERVAL_MS = 24 * 60 * 60 * 1000; // Daily maintenance
    
    // Optimization thresholds
    private static final int FRAGMENTATION_THRESHOLD = 20; // 20% fragmentation triggers optimization
    private static final int INDEX_SIZE_THRESHOLD = 10000; // 10k entries triggers optimization
    private static final long STALE_TIME_MS = 7 * 24 * 60 * 60 * 1000; // 7 days for stale entries
    
    private final SmsSearchDao searchDao;
    private final SmsSearchRepository searchRepository;
    private final ScheduledExecutorService scheduler;
    private final CompositeDisposable disposables;
    
    // Optimization state
    private final AtomicBoolean isOptimizing = new AtomicBoolean(false);
    private final AtomicLong lastOptimizationTime = new AtomicLong(0);
    private final AtomicLong lastAggressiveOptimization = new AtomicLong(0);
    private final AtomicInteger optimizationCount = new AtomicInteger(0);
    private final AtomicInteger aggressiveOptimizationCount = new AtomicInteger(0);
    
    // Performance metrics
    private volatile long lastOptimizationDuration = 0;
    private volatile int lastOptimizedEntries = 0;
    private volatile double lastFragmentationReduction = 0.0;
    
    @Inject
    public IndexOptimizationManager(SmsSearchDao searchDao, SmsSearchRepository searchRepository) {
        this.searchDao = searchDao;
        this.searchRepository = searchRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "IndexOptimizationManager");
            t.setDaemon(true);
            return t;
        });
        this.disposables = new CompositeDisposable();
        
        // Start optimization schedules
        startOptimizationSchedules();
        
        Log.d(TAG, "IndexOptimizationManager initialized");
    }
    
    /**
     * Start all optimization schedules
     */
    private void startOptimizationSchedules() {
        // Regular optimization
        scheduler.scheduleAtFixedRate(
            this::performRegularOptimization,
            INITIAL_DELAY_MS,
            OPTIMIZATION_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // Aggressive optimization
        scheduler.scheduleAtFixedRate(
            this::performAggressiveOptimization,
            INITIAL_DELAY_MS + AGGRESSIVE_INTERVAL_MS,
            AGGRESSIVE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // Daily maintenance
        scheduler.scheduleAtFixedRate(
            this::performDailyMaintenance,
            INITIAL_DELAY_MS + MAINTENANCE_INTERVAL_MS,
            MAINTENANCE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        Log.d(TAG, "Optimization schedules started");
    }
    
    /**
     * Perform regular optimization (lightweight)
     */
    public void performRegularOptimization() {
        if (!isOptimizing.compareAndSet(false, true)) {
            Log.d(TAG, "Optimization already in progress, skipping regular optimization");
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Log.d(TAG, "Starting regular optimization");
            
            // Check if optimization is needed
            OptimizationMetrics metrics = getOptimizationMetrics();
            if (!needsOptimization(metrics)) {
                Log.d(TAG, "Optimization not needed: " + metrics);
                return;
            }
            
            // Optimization not supported by current DAO; skip detailed operations
            Log.d(TAG, "Regular optimization skipped: DAO does not expose optimization APIs");
            isOptimizing.set(false);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during regular optimization", e);
            isOptimizing.set(false);
        }
    }
    
    /**
     * Perform aggressive optimization (heavyweight)
     */
    public void performAggressiveOptimization() {
        if (!isOptimizing.compareAndSet(false, true)) {
            Log.d(TAG, "Optimization already in progress, skipping aggressive optimization");
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Log.d(TAG, "Starting aggressive optimization");
            
            // Aggressive optimization not supported by current DAO; skip
            Log.d(TAG, "Aggressive optimization skipped: DAO does not expose optimization APIs");
            isOptimizing.set(false);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during aggressive optimization", e);
            isOptimizing.set(false);
        }
    }
    
    /**
     * Perform daily maintenance tasks
     */
    public void performDailyMaintenance() {
        Log.d(TAG, "Starting daily maintenance");
        
        Log.d(TAG, "Daily maintenance skipped: DAO does not expose maintenance APIs");
    }
    
    /**
     * Force immediate optimization
     */
    public void forceOptimization(boolean aggressive) {
        Log.d(TAG, "Force " + (aggressive ? "aggressive" : "regular") + " optimization requested");
        
        if (aggressive) {
            performAggressiveOptimization();
        } else {
            performRegularOptimization();
        }
    }
    
    /**
     * Get current optimization metrics
     */
    public OptimizationMetrics getOptimizationMetrics() {
        // DAO does not expose detailed metrics in this build; return defaults
        return new OptimizationMetrics(
            0,
            0.0,
            0L,
            lastOptimizationTime.get(),
            lastAggressiveOptimization.get()
        );
    }
    
    /**
     * Get optimization statistics
     */
    public OptimizationStats getOptimizationStats() {
        return new OptimizationStats(
            optimizationCount.get(),
            aggressiveOptimizationCount.get(),
            lastOptimizationTime.get(),
            lastAggressiveOptimization.get(),
            lastOptimizationDuration,
            lastOptimizedEntries,
            lastFragmentationReduction
        );
    }
    
    /**
     * Check if optimization is needed
     */
    private boolean needsOptimization(OptimizationMetrics metrics) {
        // Check fragmentation level
        if (metrics.fragmentationLevel > FRAGMENTATION_THRESHOLD) {
            return true;
        }
        
        // Check index size
        if (metrics.indexSize > INDEX_SIZE_THRESHOLD) {
            return true;
        }
        
        // Check time since last optimization
        long timeSinceLastOptimization = System.currentTimeMillis() - metrics.lastOptimizationTime;
        if (timeSinceLastOptimization > OPTIMIZATION_INTERVAL_MS * 2) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Update optimization statistics
     */
    private void updateOptimizationStats(long duration, boolean aggressive) {
        lastOptimizationDuration = duration;
        lastOptimizationTime.set(System.currentTimeMillis());
        
        if (aggressive) {
            aggressiveOptimizationCount.incrementAndGet();
        } else {
            optimizationCount.incrementAndGet();
        }
        
        isOptimizing.set(false);
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        try {
            Log.d(TAG, "Shutting down IndexOptimizationManager");
            
            // Wait for current optimization to complete
            if (isOptimizing.get()) {
                Log.d(TAG, "Waiting for optimization to complete");
                Thread.sleep(5000); // Wait up to 5 seconds
            }
            
            // Shutdown scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Dispose RxJava subscriptions
            disposables.clear();
            
            Log.d(TAG, "IndexOptimizationManager shutdown completed");
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted during shutdown", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Optimization metrics data class
     */
    public static class OptimizationMetrics {
        public final int indexSize;
        public final double fragmentationLevel;
        public final long indexSizeBytes;
        public final long lastOptimizationTime;
        public final long lastAggressiveOptimization;
        
        OptimizationMetrics(int indexSize, double fragmentationLevel, long indexSizeBytes,
                          long lastOptimizationTime, long lastAggressiveOptimization) {
            this.indexSize = indexSize;
            this.fragmentationLevel = fragmentationLevel;
            this.indexSizeBytes = indexSizeBytes;
            this.lastOptimizationTime = lastOptimizationTime;
            this.lastAggressiveOptimization = lastAggressiveOptimization;
        }
        
        @Override
        public String toString() {
            return String.format("OptimizationMetrics{size=%d, fragmentation=%.2f%%, bytes=%d, lastOpt=%d, lastAggressive=%d}",
                indexSize, fragmentationLevel, indexSizeBytes, lastOptimizationTime, lastAggressiveOptimization);
        }
    }
    
    /**
     * Optimization statistics data class
     */
    public static class OptimizationStats {
        public final int regularOptimizations;
        public final int aggressiveOptimizations;
        public final long lastOptimizationTime;
        public final long lastAggressiveOptimization;
        public final long lastOptimizationDuration;
        public final int lastOptimizedEntries;
        public final double lastFragmentationReduction;
        
        OptimizationStats(int regularOptimizations, int aggressiveOptimizations,
                         long lastOptimizationTime, long lastAggressiveOptimization,
                         long lastOptimizationDuration, int lastOptimizedEntries,
                         double lastFragmentationReduction) {
            this.regularOptimizations = regularOptimizations;
            this.aggressiveOptimizations = aggressiveOptimizations;
            this.lastOptimizationTime = lastOptimizationTime;
            this.lastAggressiveOptimization = lastAggressiveOptimization;
            this.lastOptimizationDuration = lastOptimizationDuration;
            this.lastOptimizedEntries = lastOptimizedEntries;
            this.lastFragmentationReduction = lastFragmentationReduction;
        }
        
        @Override
        public String toString() {
            return String.format("OptimizationStats{regular=%d, aggressive=%d, lastDuration=%dms, lastEntries=%d, fragReduction=%.2f%%}",
                regularOptimizations, aggressiveOptimizations, lastOptimizationDuration, 
                lastOptimizedEntries, lastFragmentationReduction);
        }
    }
}
