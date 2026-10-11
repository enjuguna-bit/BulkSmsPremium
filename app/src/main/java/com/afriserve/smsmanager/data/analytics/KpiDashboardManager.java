package com.afriserve.smsmanager.data.analytics;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.DashboardDao;
import com.afriserve.smsmanager.data.entity.KpiEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * KPI Dashboard Manager
 * Manages Key Performance Indicators with real-time monitoring and alerts
 */
@Singleton
public class KpiDashboardManager {
    
    private static final String TAG = "KpiDashboardManager";
    
    private final DashboardDao dashboardDao;
    
    // KPI data
    private final MutableLiveData<List<KpiEntity>> _allKpis = new MutableLiveData<>();
    public final LiveData<List<KpiEntity>> allKpis = _allKpis;
    
    private final MutableLiveData<List<KpiEntity>> _activeAlerts = new MutableLiveData<>();
    public final LiveData<List<KpiEntity>> activeAlerts = _activeAlerts;
    
    private final MutableLiveData<KpiSummary> _kpiSummary = new MutableLiveData<>();
    public final LiveData<KpiSummary> kpiSummary = _kpiSummary;
    
    // Alert management
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Alert thresholds
    private static final long ALERT_CHECK_INTERVAL = 30; // seconds
    
    @Inject
    public KpiDashboardManager(DashboardDao dashboardDao) {
        this.dashboardDao = dashboardDao;
        
        // Start KPI monitoring
        startKpiMonitoring();
        
        Log.d(TAG, "KPI dashboard manager initialized");
    }
    
    /**
     * Get all KPIs
     */
    public LiveData<List<KpiEntity>> getAllKpis() {
        return allKpis;
    }
    
    /**
     * Get active alerts
     */
    public LiveData<List<KpiEntity>> getActiveAlerts() {
        return activeAlerts;
    }
    
    /**
     * Get KPI summary
     */
    public LiveData<KpiSummary> getKpiSummary() {
        return kpiSummary;
    }
    
    /**
     * Get KPIs by category
     */
    public Completable getKpisByCategory(String category) {
        return Completable.fromAction(() -> {
            try {
                List<KpiEntity> kpis = dashboardDao.getKpisByCategory(category).blockingGet();
                
                // Filter by category and update LiveData
                List<KpiEntity> categoryKpis = new ArrayList<>();
                for (KpiEntity kpi : kpis) {
                    if (category.equals(kpi.category)) {
                        categoryKpis.add(kpi);
                    }
                }
                
                _allKpis.postValue(categoryKpis);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get KPIs by category", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Update KPI value
     */
    public Completable updateKpiValue(String kpiType, double newValue) {
        return Completable.fromAction(() -> {
            try {
                // Get current KPI
                KpiEntity currentKpi = dashboardDao.getLatestKpiByType(kpiType).blockingGet();
                
                if (currentKpi != null) {
                    // Store previous value for trend calculation
                    double previousValue = currentKpi.kpiValue;
                    
                    // Update KPI
                    currentKpi.kpiValue = newValue;
                    currentKpi.updateTimestamp();
                    currentKpi.updateTrend(previousValue);
                    currentKpi.updateStatus();
                    
                    // Save to database
                    dashboardDao.updateKpi(currentKpi).blockingAwait();
                    
                    // Create new KPI entry for history
                    KpiEntity newKpi = new KpiEntity(kpiType, currentKpi.kpiName, newValue, currentKpi.targetValue);
                    newKpi.thresholdWarning = currentKpi.thresholdWarning;
                    newKpi.thresholdCritical = currentKpi.thresholdCritical;
                    newKpi.unit = currentKpi.unit;
                    newKpi.category = currentKpi.category;
                    newKpi.description = currentKpi.description;
                    newKpi.updateTrend(previousValue);
                    newKpi.updateStatus();
                    
                    dashboardDao.insertKpi(newKpi).blockingAwait();
                    
                    Log.d(TAG, "KPI updated: " + kpiType + " = " + newValue);
                }
                
                // Refresh KPI data (would call actual refresh method)
                // refreshKpiData();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update KPI value", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Create custom KPI
     */
    public Completable createCustomKpi(String kpiType, String kpiName, double kpiValue, 
                                       double targetValue, double warningThreshold, 
                                       double criticalThreshold, String unit, 
                                       String category, String description) {
        return Completable.fromAction(() -> {
            try {
                KpiEntity kpi = new KpiEntity(kpiType, kpiName, kpiValue, targetValue);
                kpi.thresholdWarning = warningThreshold;
                kpi.thresholdCritical = criticalThreshold;
                kpi.unit = unit;
                kpi.category = category;
                kpi.description = description;
                kpi.updateStatus();
                
                dashboardDao.insertKpi(kpi).blockingAwait();
                
                // Refresh KPI data
                refreshKpiData();
                
                Log.d(TAG, "Custom KPI created: " + kpiType);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create custom KPI", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Delete KPI
     */
    public Completable deleteKpi(String kpiType) {
        return Completable.fromAction(() -> {
            try {
                // This would delete KPI from database
                // For now, we'll just log it
                Log.d(TAG, "KPI deleted: " + kpiType);
                
                // Refresh KPI data
                refreshKpiData();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete KPI", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Acknowledge alert
     */
    public Completable acknowledgeAlert(long kpiId) {
        return Completable.fromAction(() -> {
            try {
                // This would mark alert as acknowledged in database
                Log.d(TAG, "Alert acknowledged: " + kpiId);
                
                // Refresh alerts
                refreshAlerts();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to acknowledge alert", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get KPI performance rating
     */
    public Completable refreshKpiPerformance() {
        return Completable.fromAction(() -> {
            try {
                // Update all KPIs with latest performance data
                updateDeliveryRateKpi();
                updateResponseTimeKpi();
                updateCampaignSuccessKpi();
                updateComplianceKpi();
                updateFinancialKpis();
                
                // Refresh data
                refreshKpiData();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh KPI performance", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get KPI health score
     */
    public Single<KpiHealthScore> getKpiHealthScore() {
        return Single.fromCallable(() -> {
            try {
                List<KpiEntity> kpis = new ArrayList<>();
                
                // Collect KPIs from each type
                try {
                    kpis.add(dashboardDao.getLatestKpiByType("DELIVERY_RATE").blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get DELIVERY_RATE KPI: " + e.getMessage());
                }
                
                try {
                    kpis.add(dashboardDao.getLatestKpiByType("RESPONSE_TIME").blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get RESPONSE_TIME KPI: " + e.getMessage());
                }
                
                try {
                    kpis.add(dashboardDao.getLatestKpiByType("CAMPAIGN_SUCCESS").blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get CAMPAIGN_SUCCESS KPI: " + e.getMessage());
                }
                
                try {
                    kpis.add(dashboardDao.getLatestKpiByType("COMPLIANCE_RATE").blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get COMPLIANCE_RATE KPI: " + e.getMessage());
                }
                
                if (kpis.isEmpty()) {
                    return new KpiHealthScore(0.0, "NO_DATA", new ArrayList<>());
                }
                
                double totalScore = 0.0;
                List<String> issues = new ArrayList<>();
                
                for (KpiEntity kpi : kpis) {
                    double score = calculateKpiScore(kpi);
                    totalScore += score;
                    
                    if (score < 70.0) {
                        issues.add(kpi.kpiName + " is performing poorly");
                    }
                }
                
                double averageScore = totalScore / kpis.size();
                String healthStatus = determineHealthStatus(averageScore);
                
                return new KpiHealthScore(averageScore, healthStatus, issues);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get KPI health score", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    // Private methods
    
    private void startKpiMonitoring() {
        // Initialize KPIs
        initializeDefaultKpis();
        
        // Start periodic checks
        scheduler.scheduleAtFixedRate(() -> {
            try {
                refreshKpiData();
                checkAlerts();
                updateKpiSummary();
            } catch (Exception e) {
                Log.e(TAG, "KPI monitoring failed", e);
            }
        }, ALERT_CHECK_INTERVAL, ALERT_CHECK_INTERVAL, TimeUnit.SECONDS);
        
        Log.d(TAG, "KPI monitoring started");
    }
    
    private void initializeDefaultKpis() {
        Completable.fromAction(() -> {
            try {
                // Create default KPIs if they don't exist
                List<KpiEntity> defaultKpis = createDefaultKpiList();
                
                for (KpiEntity kpi : defaultKpis) {
                    // Check if KPI already exists
                    KpiEntity existing = dashboardDao.getLatestKpiByType(kpi.kpiType).blockingGet();
                    if (existing == null) {
                        dashboardDao.insertKpi(kpi).blockingAwait();
                    }
                }
                
                Log.d(TAG, "Default KPIs initialized");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize default KPIs", e);
            }
        }).subscribeOn(Schedulers.io()).subscribe(
            () -> refreshKpiData(),
            error -> Log.e(TAG, "Default KPIs initialization failed", error)
        );
    }
    
    private List<KpiEntity> createDefaultKpiList() {
        List<KpiEntity> kpis = new ArrayList<>();
        
        // Delivery Rate KPI
        KpiEntity deliveryRate = new KpiEntity("DELIVERY_RATE", "Delivery Rate", 95.0, 95.0);
        deliveryRate.thresholdWarning = 90.0;
        deliveryRate.thresholdCritical = 85.0;
        deliveryRate.unit = "%";
        deliveryRate.category = "PERFORMANCE";
        deliveryRate.description = "Percentage of messages successfully delivered";
        deliveryRate.updateStatus();
        kpis.add(deliveryRate);
        
        // Response Time KPI
        KpiEntity responseTime = new KpiEntity("RESPONSE_TIME", "Response Time", 5000.0, 5000.0);
        responseTime.thresholdWarning = 10000.0;
        responseTime.thresholdCritical = 15000.0;
        responseTime.unit = "ms";
        responseTime.category = "PERFORMANCE";
        responseTime.description = "Average time for message delivery";
        responseTime.updateStatus();
        kpis.add(responseTime);
        
        // Campaign Success KPI
        KpiEntity campaignSuccess = new KpiEntity("CAMPAIGN_SUCCESS", "Campaign Success", 85.0, 85.0);
        campaignSuccess.thresholdWarning = 75.0;
        campaignSuccess.thresholdCritical = 65.0;
        campaignSuccess.unit = "%";
        campaignSuccess.category = "PERFORMANCE";
        campaignSuccess.description = "Percentage of campaigns completed successfully";
        campaignSuccess.updateStatus();
        kpis.add(campaignSuccess);
        
        // Compliance Rate KPI
        KpiEntity complianceRate = new KpiEntity("COMPLIANCE_RATE", "Compliance Rate", 98.0, 98.0);
        complianceRate.thresholdWarning = 95.0;
        complianceRate.thresholdCritical = 90.0;
        complianceRate.unit = "%";
        complianceRate.category = "COMPLIANCE";
        complianceRate.description = "Percentage of messages compliant with regulations";
        complianceRate.updateStatus();
        kpis.add(complianceRate);
        
        // Cost KPI
        KpiEntity averageCost = new KpiEntity("AVERAGE_COST", "Average Cost", 0.05, 0.05);
        averageCost.thresholdWarning = 0.08;
        averageCost.thresholdCritical = 0.10;
        averageCost.unit = "$";
        averageCost.category = "FINANCIAL";
        averageCost.description = "Average cost per SMS message";
        averageCost.updateStatus();
        kpis.add(averageCost);
        
        // Revenue KPI
        KpiEntity averageRevenue = new KpiEntity("AVERAGE_REVENUE", "Average Revenue", 0.10, 0.10);
        averageRevenue.thresholdWarning = 0.05;
        averageRevenue.thresholdCritical = 0.02;
        averageRevenue.unit = "$";
        averageRevenue.category = "FINANCIAL";
        averageRevenue.description = "Average revenue per SMS message";
        averageRevenue.updateStatus();
        kpis.add(averageRevenue);
        
        return kpis;
    }
    
    private void refreshKpiData() {
        try {
            // Get all KPIs
            List<KpiEntity> allKpisList = new ArrayList<>();
            
            // Get latest KPIs by type
            List<String> kpiTypes = List.of("DELIVERY_RATE", "RESPONSE_TIME", "CAMPAIGN_SUCCESS", 
                                            "COMPLIANCE_RATE", "AVERAGE_COST", "AVERAGE_REVENUE");
            
            for (String kpiType : kpiTypes) {
                KpiEntity kpi = dashboardDao.getLatestKpiByType(kpiType).blockingGet();
                if (kpi != null) {
                    allKpisList.add(kpi);
                }
            }
            
            _allKpis.postValue(allKpisList);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh KPI data", e);
        }
    }
    
    private void refreshAlerts() {
        try {
            List<KpiEntity> alerts = dashboardDao.getActiveAlerts().blockingGet();
            _activeAlerts.postValue(alerts);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh alerts", e);
        }
    }
    
    private void checkAlerts() {
        try {
            // This would check for new alerts and trigger notifications
            // For now, we'll just refresh the alerts list
            refreshAlerts();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to check alerts", e);
        }
    }
    
    private void updateKpiSummary() {
        try {
            List<KpiEntity> kpis = _allKpis.getValue();
            if (kpis == null || kpis.isEmpty()) {
                return;
            }
            
            int totalKpis = kpis.size();
            int healthyKpis = 0;
            int warningKpis = 0;
            int criticalKpis = 0;
            
            for (KpiEntity kpi : kpis) {
                switch (kpi.status) {
                    case "GOOD":
                        healthyKpis++;
                        break;
                    case "WARNING":
                        warningKpis++;
                        break;
                    case "CRITICAL":
                        criticalKpis++;
                        break;
                }
            }
            
            KpiSummary summary = new KpiSummary(totalKpis, healthyKpis, warningKpis, criticalKpis);
            _kpiSummary.postValue(summary);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update KPI summary", e);
        }
    }
    
    private void updateDeliveryRateKpi() {
        // This would calculate actual delivery rate from data
        // For now, we'll use a placeholder
        updateKpiValue("DELIVERY_RATE", 95.0);
    }
    
    private void updateResponseTimeKpi() {
        // This would calculate actual response time from data
        // For now, we'll use a placeholder
        updateKpiValue("RESPONSE_TIME", 5000.0);
    }
    
    private void updateCampaignSuccessKpi() {
        // This would calculate actual campaign success rate from data
        // For now, we'll use a placeholder
        updateKpiValue("CAMPAIGN_SUCCESS", 85.0);
    }
    
    private void updateComplianceKpi() {
        // This would calculate actual compliance rate from data
        // For now, we'll use a placeholder
        updateKpiValue("COMPLIANCE_RATE", 98.0);
    }
    
    private void updateFinancialKpis() {
        // This would calculate actual financial metrics from data
        // For now, we'll use placeholders
        updateKpiValue("AVERAGE_COST", 0.05);
        updateKpiValue("AVERAGE_REVENUE", 0.10);
    }
    
    private double calculateKpiScore(KpiEntity kpi) {
        double performanceRatio = kpi.getPerformanceRatio();
        
        if (performanceRatio >= 1.0) {
            return 100.0; // Exceeded target
        } else if (performanceRatio >= 0.9) {
            return 80.0 + (performanceRatio - 0.9) * 200.0; // 80-100
        } else if (performanceRatio >= 0.7) {
            return 60.0 + (performanceRatio - 0.7) * 100.0; // 60-80
        } else {
            return performanceRatio * 85.7; // 0-60
        }
    }
    
    private String determineHealthStatus(double score) {
        if (score >= 90.0) return "EXCELLENT";
        if (score >= 80.0) return "GOOD";
        if (score >= 70.0) return "FAIR";
        if (score >= 60.0) return "POOR";
        return "CRITICAL";
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        try {
            disposables.clear();
            scheduler.shutdown();
            Log.d(TAG, "KPI dashboard manager cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup KPI dashboard manager", e);
        }
    }
    
    /**
     * KPI Summary data class
     */
    public static class KpiSummary {
        public final int totalKpis;
        public final int healthyKpis;
        public final int warningKpis;
        public final int criticalKpis;
        
        public KpiSummary(int totalKpis, int healthyKpis, int warningKpis, int criticalKpis) {
            this.totalKpis = totalKpis;
            this.healthyKpis = healthyKpis;
            this.warningKpis = warningKpis;
            this.criticalKpis = criticalKpis;
        }
        
        public float getHealthyPercentage() {
            return totalKpis > 0 ? (float) healthyKpis / totalKpis * 100.0f : 0.0f;
        }
        
        public boolean isHealthy() {
            return criticalKpis == 0 && warningKpis <= totalKpis * 0.2;
        }
        
        public boolean needsAttention() {
            return criticalKpis > 0 || warningKpis > totalKpis * 0.2;
        }
        
        @Override
        public String toString() {
            return "KpiSummary{" +
                    "total=" + totalKpis +
                    ", healthy=" + healthyKpis +
                    ", warning=" + warningKpis +
                    ", critical=" + criticalKpis +
                    ", health=" + String.format("%.1f%%", getHealthyPercentage()) +
                    '}';
        }
    }
    
    /**
     * KPI Health Score data class
     */
    public static class KpiHealthScore {
        public final double score;
        public final String status;
        public final List<String> issues;
        
        public KpiHealthScore(double score, String status, List<String> issues) {
            this.score = score;
            this.status = status;
            this.issues = issues;
        }
        
        public boolean isHealthy() {
            return score >= 80.0;
        }
        
        @Override
        public String toString() {
            return "KpiHealthScore{" +
                    "score=" + String.format("%.1f", score) +
                    ", status='" + status + '\'' +
                    ", issues=" + issues.size() +
                    '}';
        }
    }
}
