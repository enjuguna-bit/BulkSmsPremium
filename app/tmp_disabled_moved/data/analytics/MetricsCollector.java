package com.bulksms.smsmanager.data.analytics;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bulksms.smsmanager.data.repository.DashboardRepository;
import com.bulksms.smsmanager.data.tracking.DeliveryTracker;
import com.bulksms.smsmanager.data.compliance.RateLimitManager;
import com.bulksms.smsmanager.data.compliance.ComplianceManager;
import com.bulksms.smsmanager.data.scheduler.CampaignScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Real-time metrics collection system
 * Collects and processes metrics from various sources in real-time
 */
@Singleton
public class MetricsCollector {
    
    private static final String TAG = "MetricsCollector";
    
    // Data sources
    private final DashboardRepository dashboardRepository;
    private final DeliveryTracker deliveryTracker;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final CampaignScheduler campaignScheduler;
    
    // Real-time metrics
    private final MutableLiveData<RealTimeMetrics> _realTimeMetrics = new MutableLiveData<>();
    public final LiveData<RealTimeMetrics> realTimeMetrics = _realTimeMetrics;
    
    // Metrics state
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalDelivered = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalPending = new AtomicLong(0);
    private final AtomicLong currentRateLimitHits = new AtomicLong(0);
    private final AtomicLong complianceViolations = new AtomicLong(0);
    
    // Background processing
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Collection intervals
    private static final long REAL_TIME_INTERVAL = 5; // seconds
    private static final long AGGREGATION_INTERVAL = 30; // seconds
    private static final long PERSISTENCE_INTERVAL = 300; // 5 minutes
    
    @Inject
    public MetricsCollector(
        DashboardRepository dashboardRepository,
        DeliveryTracker deliveryTracker,
        RateLimitManager rateLimitManager,
        ComplianceManager complianceManager,
        CampaignScheduler campaignScheduler
    ) {
        this.dashboardRepository = dashboardRepository;
        this.deliveryTracker = deliveryTracker;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.campaignScheduler = campaignScheduler;
        
        // Start metrics collection
        startMetricsCollection();
        
        Log.d(TAG, "Metrics collector initialized");
    }
    
    /**
     * Record SMS sent event
     */
    public Completable recordSmsSent(String phoneNumber, String campaignId) {
        return Completable.fromAction(() -> {
            try {
                totalSent.incrementAndGet();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                // Record in dashboard repository
                dashboardRepository.recordSmsEvent("SENT", 1).blockingAwait();
                
                Log.d(TAG, "SMS sent recorded: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record SMS sent", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record SMS delivered event
     */
    public Completable recordSmsDelivered(String phoneNumber, long deliveryTime) {
        return Completable.fromAction(() -> {
            try {
                totalDelivered.incrementAndGet();
                totalPending.decrementAndGet();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                // Record in dashboard repository
                dashboardRepository.recordSmsEvent("DELIVERED", 1).blockingAwait();
                
                Log.d(TAG, "SMS delivered recorded: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record SMS delivered", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record SMS failed event
     */
    public Completable recordSmsFailed(String phoneNumber, String error) {
        return Completable.fromAction(() -> {
            try {
                totalFailed.incrementAndGet();
                totalPending.decrementAndGet();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                // Record in dashboard repository
                dashboardRepository.recordSmsEvent("FAILED", 1).blockingAwait();
                
                Log.d(TAG, "SMS failed recorded: " + phoneNumber + " - " + error);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record SMS failed", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record campaign event
     */
    public Completable recordCampaignEvent(String eventType, String campaignId) {
        return Completable.fromAction(() -> {
            try {
                // Update campaign counts based on event type
                switch (eventType) {
                    case "STARTED":
                        dashboardRepository.recordCampaignEvent("ACTIVE", 1).blockingAwait();
                        break;
                    case "COMPLETED":
                        dashboardRepository.recordCampaignEvent("ACTIVE", -1).blockingAwait();
                        break;
                    case "SCHEDULED":
                        dashboardRepository.recordCampaignEvent("SCHEDULED", 1).blockingAwait();
                        break;
                    case "CANCELLED":
                        dashboardRepository.recordCampaignEvent("SCHEDULED", -1).blockingAwait();
                        break;
                }
                
                Log.d(TAG, "Campaign event recorded: " + eventType + " - " + campaignId);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record campaign event", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record compliance violation
     */
    public Completable recordComplianceViolation(String violationType, String phoneNumber) {
        return Completable.fromAction(() -> {
            try {
                complianceViolations.incrementAndGet();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                Log.d(TAG, "Compliance violation recorded: " + violationType + " - " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record compliance violation", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record rate limit hit
     */
    public Completable recordRateLimitHit(String carrier) {
        return Completable.fromAction(() -> {
            try {
                currentRateLimitHits.incrementAndGet();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                Log.d(TAG, "Rate limit hit recorded: " + carrier);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record rate limit hit", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    public LiveData<RealTimeMetrics> getCurrentMetrics() {
        return realTimeMetrics;
    }
    
    /**
     * Force metrics refresh
     */
    public Completable refreshMetrics() {
        return Completable.fromAction(() -> {
            try {
                // Reset counters from database
                resetCountersFromDatabase();
                
                // Update real-time metrics
                updateRealTimeMetrics();
                
                Log.d(TAG, "Metrics refreshed");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh metrics", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Start metrics collection
     */
    private void startMetricsCollection() {
        // Real-time updates every 5 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateRealTimeMetrics();
            } catch (Exception e) {
                Log.e(TAG, "Real-time update failed", e);
            }
        }, REAL_TIME_INTERVAL, REAL_TIME_INTERVAL, TimeUnit.SECONDS);
        
        // Aggregation every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                aggregateMetrics();
            } catch (Exception e) {
                Log.e(TAG, "Aggregation failed", e);
            }
        }, AGGREGATION_INTERVAL, AGGREGATION_INTERVAL, TimeUnit.SECONDS);
        
        // Persistence every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                persistMetrics();
            } catch (Exception e) {
                Log.e(TAG, "Persistence failed", e);
            }
        }, PERSISTENCE_INTERVAL, PERSISTENCE_INTERVAL, TimeUnit.SECONDS);
        
        // Subscribe to data sources
        subscribeToDataSources();
        
        Log.d(TAG, "Metrics collection started");
    }
    
    /**
     * Subscribe to data sources for real-time updates
     */
    private void subscribeToDataSources() {
        // Subscribe to delivery tracker
        disposables.add(
            deliveryTracker.getDeliveryStatistics()
                .repeat()
                .delay(10, TimeUnit.SECONDS)
                .subscribe(
                    stats -> {
                        totalDelivered.set(stats.totalDelivered);
                        totalFailed.set(stats.totalFailed);
                        updateRealTimeMetrics();
                    },
                    error -> Log.e(TAG, "Delivery tracker error", error)
                )
        );
        
        // Subscribe to rate limit manager
        disposables.add(
            rateLimitManager.getRateLimitStatus()
                .subscribe(
                    status -> {
                        if (status == RateLimitManager.RateLimitStatus.LIMITED) {
                            recordRateLimitHit("UNKNOWN").blockingAwait();
                        }
                    },
                    error -> Log.e(TAG, "Rate limit manager error", error)
                )
        );
        
        // Subscribe to campaign scheduler
        disposables.add(
            campaignScheduler.getActiveScheduledCampaigns()
                .repeat()
                .delay(15, TimeUnit.SECONDS)
                .subscribe(
                    campaigns -> {
                        // Update campaign-related metrics
                        updateCampaignMetrics(campaigns.size());
                    },
                    error -> Log.e(TAG, "Campaign scheduler error", error)
                )
        );
    }
    
    /**
     * Update real-time metrics
     */
    private void updateRealTimeMetrics() {
        try {
            RealTimeMetrics metrics = new RealTimeMetrics(
                totalSent.get(),
                totalDelivered.get(),
                totalFailed.get(),
                totalPending.get(),
                currentRateLimitHits.get(),
                complianceViolations.get(),
                System.currentTimeMillis()
            );
            
            _realTimeMetrics.postValue(metrics);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update real-time metrics", e);
        }
    }
    
    /**
     * Aggregate metrics
     */
    private void aggregateMetrics() {
        try {
            // Calculate rates and averages
            float deliveryRate = totalSent.get() > 0 ? 
                (float) totalDelivered.get() / totalSent.get() * 100.0f : 0.0f;
            
            float failureRate = totalSent.get() > 0 ? 
                (float) totalFailed.get() / totalSent.get() * 100.0f : 0.0f;
            
            float complianceRate = totalSent.get() > 0 ? 
                (float) (totalSent.get() - complianceViolations.get()) / totalSent.get() * 100.0f : 100.0f;
            
            // Update aggregated metrics
            RealTimeMetrics currentMetrics = _realTimeMetrics.getValue();
            if (currentMetrics != null) {
                RealTimeMetrics aggregatedMetrics = new RealTimeMetrics(
                    currentMetrics.totalSent,
                    currentMetrics.totalDelivered,
                    currentMetrics.totalFailed,
                    currentMetrics.totalPending,
                    currentMetrics.rateLimitHits,
                    currentMetrics.complianceViolations,
                    currentMetrics.timestamp,
                    deliveryRate,
                    failureRate,
                    complianceRate
                );
                
                _realTimeMetrics.postValue(aggregatedMetrics);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to aggregate metrics", e);
        }
    }
    
    /**
     * Persist metrics to database
     */
    private void persistMetrics() {
        try {
            // This would persist current metrics to database
            // For now, we'll just log the metrics
            RealTimeMetrics metrics = _realTimeMetrics.getValue();
            if (metrics != null) {
                Log.d(TAG, "Persisting metrics: " + metrics);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist metrics", e);
        }
    }
    
    /**
     * Reset counters from database
     */
    private void resetCountersFromDatabase() {
        try {
            // This would reset counters from database values
            // For now, we'll keep current values
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset counters", e);
        }
    }
    
    /**
     * Update campaign metrics
     */
    private void updateCampaignMetrics(int activeCampaigns) {
        try {
            // Update campaign-related metrics
            // This would update campaign-specific metrics with count of active campaigns
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update campaign metrics", e);
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        try {
            disposables.clear();
            scheduler.shutdown();
            Log.d(TAG, "Metrics collector cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup metrics collector", e);
        }
    }
    
    /**
     * Real-time metrics data class
     */
    public static class RealTimeMetrics {
        public final long totalSent;
        public final long totalDelivered;
        public final long totalFailed;
        public final long totalPending;
        public final long rateLimitHits;
        public final long complianceViolations;
        public final long timestamp;
        
        // Calculated metrics
        public final float deliveryRate;
        public final float failureRate;
        public final float complianceRate;
        
        public RealTimeMetrics(long totalSent, long totalDelivered, long totalFailed, 
                              long totalPending, long rateLimitHits, long complianceViolations, 
                              long timestamp) {
            this.totalSent = totalSent;
            this.totalDelivered = totalDelivered;
            this.totalFailed = totalFailed;
            this.totalPending = totalPending;
            this.rateLimitHits = rateLimitHits;
            this.complianceViolations = complianceViolations;
            this.timestamp = timestamp;
            
            // Calculate rates
            this.deliveryRate = totalSent > 0 ? (float) totalDelivered / totalSent * 100.0f : 0.0f;
            this.failureRate = totalSent > 0 ? (float) totalFailed / totalSent * 100.0f : 0.0f;
            this.complianceRate = totalSent > 0 ? 
                (float) (totalSent - complianceViolations) / totalSent * 100.0f : 100.0f;
        }
        
        public RealTimeMetrics(long totalSent, long totalDelivered, long totalFailed, 
                              long totalPending, long rateLimitHits, long complianceViolations, 
                              long timestamp, float deliveryRate, float failureRate, 
                              float complianceRate) {
            this.totalSent = totalSent;
            this.totalDelivered = totalDelivered;
            this.totalFailed = totalFailed;
            this.totalPending = totalPending;
            this.rateLimitHits = rateLimitHits;
            this.complianceViolations = complianceViolations;
            this.timestamp = timestamp;
            this.deliveryRate = deliveryRate;
            this.failureRate = failureRate;
            this.complianceRate = complianceRate;
        }
        
        public boolean isHealthy() {
            return deliveryRate >= 90.0f && failureRate <= 5.0f && complianceRate >= 95.0f;
        }
        
        public boolean hasWarnings() {
            return deliveryRate < 90.0f || failureRate > 5.0f || complianceRate < 95.0f;
        }
        
        public boolean hasCriticalIssues() {
            return deliveryRate < 80.0f || failureRate > 10.0f || complianceRate < 90.0f;
        }
        
        @Override
        public String toString() {
            return "RealTimeMetrics{" +
                    "sent=" + totalSent +
                    ", delivered=" + totalDelivered +
                    ", failed=" + totalFailed +
                    ", pending=" + totalPending +
                    ", deliveryRate=" + String.format("%.1f%%", deliveryRate) +
                    ", failureRate=" + String.format("%.1f%%", failureRate) +
                    ", complianceRate=" + String.format("%.1f%%", complianceRate) +
                    '}';
        }
    }
}
