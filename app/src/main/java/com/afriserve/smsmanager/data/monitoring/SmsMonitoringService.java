package com.afriserve.smsmanager.data.monitoring;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.afriserve.smsmanager.data.queue.SmsQueueManager;
import com.afriserve.smsmanager.data.compliance.RateLimitManager;
import com.afriserve.smsmanager.data.compliance.ComplianceManager;
import com.afriserve.smsmanager.worker.BulkSmsSendingWorker;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Comprehensive SMS monitoring service
 * Provides real-time monitoring and alerting for SMS delivery pipeline
 */
@Singleton
public class SmsMonitoringService {
    private static final String TAG = "SmsMonitoringService";
    private static final long MONITORING_INTERVAL_MS = 30000; // 30 seconds
    
    private final Context context;
    private final SmsDao smsDao;
    private final CampaignDao campaignDao;
    private final EnhancedDeliveryTracker deliveryTracker;
    private final SmsQueueManager queueManager;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final UploadPersistenceService uploadPersistence;
    
    private final ScheduledExecutorService monitoringExecutor;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    
    // Monitoring data
    private final MutableLiveData<MonitoringStatus> _monitoringStatus = new MutableLiveData<>();
    public final LiveData<MonitoringStatus> monitoringStatus = _monitoringStatus;
    
    private final MutableLiveData<SmsDeliveryMetrics> _deliveryMetrics = new MutableLiveData<>();
    public final LiveData<SmsDeliveryMetrics> deliveryMetrics = _deliveryMetrics;
    
    private final MutableLiveData<QueueStatus> _queueStatus = new MutableLiveData<>();
    public final LiveData<QueueStatus> queueStatus = _queueStatus;
    
    private final MutableLiveData<RateLimitStatus> _rateLimitStatus = new MutableLiveData<>();
    public final LiveData<RateLimitStatus> rateLimitStatus = _rateLimitStatus;

    @Inject
    public SmsMonitoringService(
            @dagger.hilt.android.qualifiers.ApplicationContext Context context,
            SmsDao smsDao,
            CampaignDao campaignDao,
            EnhancedDeliveryTracker deliveryTracker,
            SmsQueueManager queueManager,
            RateLimitManager rateLimitManager,
            ComplianceManager complianceManager,
            UploadPersistenceService uploadPersistence) {
        
        this.context = context;
        this.smsDao = smsDao;
        this.campaignDao = campaignDao;
        this.deliveryTracker = deliveryTracker;
        this.queueManager = queueManager;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.uploadPersistence = uploadPersistence;
        
        this.monitoringExecutor = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * Start monitoring
     */
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Log.d(TAG, "Starting SMS monitoring");
            scheduleMonitoring();
        }
    }
    
    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            Log.d(TAG, "Stopping SMS monitoring");
            monitoringExecutor.shutdown();
        }
    }
    
    /**
     * Schedule periodic monitoring
     */
    private void scheduleMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(() -> {
            if (!isMonitoring.get()) return;
            
            try {
                updateMonitoringData();
            } catch (Exception e) {
                Log.e(TAG, "Error in monitoring update", e);
            }
        }, 0, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Update all monitoring data
     */
    private void updateMonitoringData() {
        // Update delivery metrics
        updateDeliveryMetrics();
        
        // Update queue status
        updateQueueStatus();
        
        // Update rate limit status
        updateRateLimitStatus();
        
        // Update overall monitoring status
        updateMonitoringStatus();
    }
    
    /**
     * Update delivery metrics
     */
    private void updateDeliveryMetrics() {
        try {
            // Get delivery statistics from tracker
            EnhancedDeliveryTracker.DeliveryStatistics trackerStats = deliveryTracker.getDeliveryStatistics();
            
            // Get SMS counts from database
            Single<Integer> pendingCount = smsDao.getPendingCountSingle();
            Single<Integer> failedCount = smsDao.getFailedCountSingle();
            Single<Integer> deliveredCount = smsDao.getDeliveredCountSingle();
            
            // Get campaign statistics
            Single<Integer> activeCampaigns = campaignDao.getActiveCampaignsCount().first(0);
            
            // Combine all metrics
            Single.zip(
                pendingCount,
                failedCount,
                deliveredCount,
                activeCampaigns,
                (pending, failed, delivered, activeCamps) -> new SmsDeliveryMetrics(
                    pending,
                    failed,
                    delivered,
                    trackerStats.getSentCount(),
                    trackerStats.getDeliveredCount(),
                    trackerStats.getFailedCount(),
                    trackerStats.getDeliveryRate(),
                    trackerStats.getSuccessRate(),
                    activeCamps
                )
            ).subscribeOn(Schedulers.io())
            .subscribe(
                metrics -> _deliveryMetrics.postValue(metrics),
                error -> Log.e(TAG, "Failed to update delivery metrics", error)
            );
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating delivery metrics", e);
        }
    }
    
    /**
     * Update queue status
     */
    private void updateQueueStatus() {
        try {
            SmsQueueManager.QueueStatistics queueStats = queueManager.getQueueStatistics();
            
            QueueStatus status = new QueueStatus(
                queueStats.pendingCount,
                queueStats.processingCount,
                queueStats.failedCount,
                queueStats.exhaustedCount,
                queueStats.totalCount,
                queueStats.processedCount,
                queueStats.circuitBreakerActive,
                queueStats.cooldownRemainingMs
            );
            
            _queueStatus.postValue(status);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating queue status", e);
        }
    }
    
    /**
     * Update rate limit status
     */
    private void updateRateLimitStatus() {
        try {
            RateLimitManager.RateLimitStats rateStats = rateLimitManager.getStats();
            
            RateLimitStatus status = new RateLimitStatus(
                rateStats.isNearLimit(),
                rateStats.getUsagePercentage(),
                rateStats.carrierStats
            );
            
            _rateLimitStatus.postValue(status);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating rate limit status", e);
        }
    }
    
    /**
     * Update overall monitoring status
     */
    private void updateMonitoringStatus() {
        try {
            MonitoringStatus status = new MonitoringStatus();
            
            // Check delivery metrics
            SmsDeliveryMetrics metrics = _deliveryMetrics.getValue();
            if (metrics != null) {
                status.deliveryHealthy = metrics.getSuccessRate() > 80.0; // 80% success rate threshold
                status.deliveryIssues = metrics.getFailedCount() > 10; // More than 10 failed messages
            }
            
            // Check queue status
            QueueStatus queueStatus = _queueStatus.getValue();
            if (queueStatus != null) {
                status.queueHealthy = !queueStatus.circuitBreakerActive && queueStatus.pendingCount < 100;
                status.queueIssues = queueStatus.exhaustedCount > 0 || queueStatus.circuitBreakerActive;
            }
            
            // Check rate limits
            RateLimitStatus rateStatus = _rateLimitStatus.getValue();
            if (rateStatus != null) {
                status.rateLimitHealthy = !rateStatus.isNearLimit;
                status.rateLimitIssues = rateStatus.isNearLimit;
            }
            
            // Check session status
            status.sessionHealthy = checkSessionHealth();
            status.sessionIssues = !status.sessionHealthy;
            
            // Overall health
            status.overallHealthy = status.deliveryHealthy && status.queueHealthy && 
                                  status.rateLimitHealthy && status.sessionHealthy;
            
            _monitoringStatus.postValue(status);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating monitoring status", e);
        }
    }
    
    /**
     * Check session health
     */
    private boolean checkSessionHealth() {
        try {
            UploadPersistenceService.UploadSession currentSession = uploadPersistence.loadSessionSync(
                uploadPersistence.getActiveSessionId()
            );
            
            if (currentSession == null) {
                return true; // No active session is healthy
            }
            
            // Check if session is valid and not expired
            return currentSession.isActive && 
                   currentSession.recipients != null && 
                   !currentSession.recipients.isEmpty() &&
                   currentSession.template != null &&
                   !currentSession.template.trim().isEmpty();
                   
        } catch (Exception e) {
            Log.e(TAG, "Error checking session health", e);
            return false;
        }
    }
    
    /**
     * Get current monitoring snapshot
     */
    public MonitoringSnapshot getSnapshot() {
        return new MonitoringSnapshot(
            _monitoringStatus.getValue(),
            _deliveryMetrics.getValue(),
            _queueStatus.getValue(),
            _rateLimitStatus.getValue()
        );
    }
    
    /**
     * Trigger manual monitoring update
     */
    public void triggerUpdate() {
        updateMonitoringData();
    }
    
    /**
     * Data class for monitoring status
     */
    public static class MonitoringStatus {
        public boolean overallHealthy = true;
        public boolean deliveryHealthy = true;
        public boolean queueHealthy = true;
        public boolean rateLimitHealthy = true;
        public boolean sessionHealthy = true;
        public boolean deliveryIssues = false;
        public boolean queueIssues = false;
        public boolean rateLimitIssues = false;
        public boolean sessionIssues = false;
        public String lastUpdate = "";
        
        public MonitoringStatus() {
            this.lastUpdate = java.time.LocalDateTime.now().toString();
        }
    }
    
    /**
     * Data class for SMS delivery metrics
     */
    public static class SmsDeliveryMetrics {
        public final int pendingCount;
        public final int failedCount;
        public final int deliveredCount;
        public final int trackerSentCount;
        public final int trackerDeliveredCount;
        public final int trackerFailedCount;
        public final double deliveryRate;
        public final double successRate;
        public final int activeCampaigns;
        
        public SmsDeliveryMetrics(int pendingCount, int failedCount, int deliveredCount,
                                int trackerSentCount, int trackerDeliveredCount, int trackerFailedCount,
                                double deliveryRate, double successRate, int activeCampaigns) {
            this.pendingCount = pendingCount;
            this.failedCount = failedCount;
            this.deliveredCount = deliveredCount;
            this.trackerSentCount = trackerSentCount;
            this.trackerDeliveredCount = trackerDeliveredCount;
            this.trackerFailedCount = trackerFailedCount;
            this.deliveryRate = deliveryRate;
            this.successRate = successRate;
            this.activeCampaigns = activeCampaigns;
        }
        
        public double getDeliveryRate() { return deliveryRate; }
        public double getSuccessRate() { return successRate; }
        public int getFailedCount() { return failedCount; }
    }
    
    /**
     * Data class for queue status
     */
    public static class QueueStatus {
        public final int pendingCount;
        public final int processingCount;
        public final int failedCount;
        public final int exhaustedCount;
        public final int totalCount;
        public final int processedCount;
        public final boolean circuitBreakerActive;
        public final Long cooldownRemainingMs;
        
        public QueueStatus(int pendingCount, int processingCount, int failedCount,
                         int exhaustedCount, int totalCount, int processedCount,
                         boolean circuitBreakerActive, Long cooldownRemainingMs) {
            this.pendingCount = pendingCount;
            this.processingCount = processingCount;
            this.failedCount = failedCount;
            this.exhaustedCount = exhaustedCount;
            this.totalCount = totalCount;
            this.processedCount = processedCount;
            this.circuitBreakerActive = circuitBreakerActive;
            this.cooldownRemainingMs = cooldownRemainingMs;
        }
    }
    
    /**
     * Data class for rate limit status
     */
    public static class RateLimitStatus {
        public final boolean isNearLimit;
        public final int usagePercentage;
        public final Map<String, RateLimitManager.CarrierStats> carrierStats;
        
        public RateLimitStatus(boolean isNearLimit, int usagePercentage,
                             Map<String, RateLimitManager.CarrierStats> carrierStats) {
            this.isNearLimit = isNearLimit;
            this.usagePercentage = usagePercentage;
            this.carrierStats = carrierStats;
        }
    }
    
    /**
     * Data class for monitoring snapshot
     */
    public static class MonitoringSnapshot {
        public final MonitoringStatus status;
        public final SmsDeliveryMetrics deliveryMetrics;
        public final QueueStatus queueStatus;
        public final RateLimitStatus rateLimitStatus;
        
        public MonitoringSnapshot(MonitoringStatus status, SmsDeliveryMetrics deliveryMetrics,
                                QueueStatus queueStatus, RateLimitStatus rateLimitStatus) {
            this.status = status;
            this.deliveryMetrics = deliveryMetrics;
            this.queueStatus = queueStatus;
            this.rateLimitStatus = rateLimitStatus;
        }
    }
}
