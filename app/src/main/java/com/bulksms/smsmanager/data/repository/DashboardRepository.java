package com.bulksms.smsmanager.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bulksms.smsmanager.data.dao.DashboardDao;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.dao.CampaignDao;
import com.bulksms.smsmanager.data.dao.OptOutDao;
import com.bulksms.smsmanager.data.entity.DashboardMetricsEntity;
import com.bulksms.smsmanager.data.entity.DashboardStatsEntity;
import com.bulksms.smsmanager.data.entity.KpiEntity;
import com.bulksms.smsmanager.data.compliance.ComplianceManager;
import com.bulksms.smsmanager.data.tracking.DeliveryTracker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Reactive Dashboard Repository
 * Provides real-time dashboard data with LiveData and RxJava
 */
@Singleton
public class DashboardRepository {
    
    private static final String TAG = "DashboardRepository";
    
    // Data sources
    private final DashboardDao dashboardDao;
    private final SmsDao smsDao;
    private final CampaignDao campaignDao;
    private final OptOutDao optOutDao;
    private final ComplianceManager complianceManager;
    private final DeliveryTracker deliveryTracker;
    
    // Live data streams
    private final MutableLiveData<DashboardData> _dashboardData = new MutableLiveData<>();
    public final LiveData<DashboardData> dashboardData = _dashboardData;
    
    private final MutableLiveData<List<KpiEntity>> _kpiAlerts = new MutableLiveData<>();
    public final LiveData<List<KpiEntity>> kpiAlerts = _kpiAlerts;
    
    private final MutableLiveData<DashboardTrend> _trendData = new MutableLiveData<>();
    public final LiveData<DashboardTrend> trendData = _trendData;
    
    // Background processing
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @Inject
    public DashboardRepository(
        DashboardDao dashboardDao,
        SmsDao smsDao,
        CampaignDao campaignDao,
        OptOutDao optOutDao,
        ComplianceManager complianceManager,
        DeliveryTracker deliveryTracker
    ) {
        this.dashboardDao = dashboardDao;
        this.smsDao = smsDao;
        this.campaignDao = campaignDao;
        this.optOutDao = optOutDao;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        
        // Initialize dashboard
        initializeDashboard();
        
        // Start real-time updates
        startRealTimeUpdates();
        
        Log.d(TAG, "Dashboard repository initialized");
    }
    
    /**
     * Get current dashboard statistics
     */
    public LiveData<DashboardData> getCurrentDashboardData() {
        return dashboardData;
    }
    
    /**
     * Get dashboard data for specific date range
     */
    public Single<DashboardData> getDashboardDataInRange(long startDate, long endDate) {
        return Single.fromCallable(() -> {
            try {
                // Get metrics for date range
                List<DashboardMetricsEntity> metrics = dashboardDao
                    .getMetricsInDateRange(startDate, endDate).blockingGet();
                
                // Get summary statistics
                DashboardDao.DashboardMetricsSummary summary = dashboardDao
                    .getTotalMetricsInRange(startDate, endDate).blockingGet();
                
                // Get campaign performance
                List<DashboardDao.CampaignPerformanceData> campaignPerformance = 
                    getCampaignPerformanceData(startDate, endDate);
                
                // Get compliance report
                List<DashboardDao.ComplianceReportData> complianceReport = 
                    getComplianceReportData(startDate, endDate);
                
                return new DashboardData(
                    metrics,
                    summary,
                    campaignPerformance,
                    complianceReport,
                    startDate,
                    endDate
                );
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get dashboard data in range", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get KPI alerts
     */
    public LiveData<List<KpiEntity>> getKpiAlerts() {
        return kpiAlerts;
    }
    
    /**
     * Get trend data
     */
    public LiveData<DashboardTrend> getTrendData() {
        return trendData;
    }
    
    /**
     * Refresh dashboard data
     */
    public Completable refreshDashboardData() {
        return Completable.fromAction(() -> {
            try {
                // Update current stats
                updateCurrentStats();
                
                // Update KPIs
                updateKpis();
                
                // Update trends
                updateTrends();
                
                // Update alerts
                updateAlerts();
                
                Log.d(TAG, "Dashboard data refreshed");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh dashboard data", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record SMS event for real-time updates
     */
    public Completable recordSmsEvent(String eventType, int count) {
        return Completable.fromAction(() -> {
            try {
                long now = System.currentTimeMillis();
                
                switch (eventType) {
                    case "SENT":
                        dashboardDao.incrementTotalSent("current", count, now).blockingAwait();
                        break;
                    case "DELIVERED":
                        dashboardDao.incrementTotalDelivered("current", count, now).blockingAwait();
                        break;
                    case "FAILED":
                        dashboardDao.incrementTotalFailed("current", count, now).blockingAwait();
                        break;
                }
                
                // Trigger dashboard update
                updateCurrentStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record SMS event", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Record campaign event
     */
    public Completable recordCampaignEvent(String eventType, int count) {
        return Completable.fromAction(() -> {
            try {
                long now = System.currentTimeMillis();
                
                switch (eventType) {
                    case "ACTIVE":
                        dashboardDao.updateActiveCampaigns("current", count, now).blockingAwait();
                        break;
                    case "SCHEDULED":
                        dashboardDao.updateScheduledCampaigns("current", count, now).blockingAwait();
                        break;
                }
                
                updateCurrentStats();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to record campaign event", e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Initialize dashboard with default data
     */
    private void initializeDashboard() {
        Completable.fromAction(() -> {
            try {
                // Ensure current stats exist
                DashboardStatsEntity currentStats = dashboardDao
                    .getDashboardStats("current").blockingGet();
                
                if (currentStats == null) {
                    currentStats = new DashboardStatsEntity("current");
                    dashboardDao.insertDashboardStats(currentStats).blockingAwait();
                }
                
                // Initialize daily metrics
                long today = getStartOfDay(System.currentTimeMillis());
                DashboardMetricsEntity todayMetrics = dashboardDao
                    .getDashboardMetrics(today, "DAILY").blockingGet();
                
                if (todayMetrics == null) {
                    todayMetrics = new DashboardMetricsEntity(today, "DAILY");
                    dashboardDao.insertDashboardMetrics(todayMetrics).blockingAwait();
                }
                
                // Initialize KPIs
                initializeKpis();
                
                Log.d(TAG, "Dashboard initialized");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize dashboard", e);
            }
        }).subscribeOn(Schedulers.io()).subscribe(
            () -> updateCurrentStats(),
            error -> Log.e(TAG, "Dashboard initialization failed", error)
        );
    }
    
    /**
     * Start real-time updates
     */
    private void startRealTimeUpdates() {
        // Update dashboard every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateCurrentStats();
                updateKpis();
                updateAlerts();
            } catch (Exception e) {
                Log.e(TAG, "Real-time update failed", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        // Update trends every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateTrends();
            } catch (Exception e) {
                Log.e(TAG, "Trend update failed", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
        
        // Daily metrics aggregation at midnight
        scheduler.scheduleAtFixedRate(() -> {
            try {
                aggregateDailyMetrics();
            } catch (Exception e) {
                Log.e(TAG, "Daily aggregation failed", e);
            }
        }, calculateTimeUntilMidnight(), 24, TimeUnit.HOURS);
    }
    
    /**
     * Update current statistics
     */
    private void updateCurrentStats() {
        try {
            // Get current stats from database
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            
            if (stats != null) {
                // Update with latest data from other sources
                stats.totalSent = smsDao.getTotalCount().getValue() != null ? smsDao.getTotalCount().getValue() : 0;
                stats.totalDelivered = smsDao.getDeliveredCount().getValue() != null ? smsDao.getDeliveredCount().getValue() : 0;
                stats.totalFailed = smsDao.getFailedCount().getValue() != null ? smsDao.getFailedCount().getValue() : 0;
                stats.activeCampaigns = campaignDao.getActiveCampaignsCount().blockingFirst();
                stats.scheduledCampaigns = dashboardDao.getPendingScheduledCampaignsCount(System.currentTimeMillis()).blockingGet();
                stats.optOutCount = optOutDao.getOptOutCount().blockingGet();
                
                // Update timestamp
                stats.updateTimestamp();
                
                // Save to database
                dashboardDao.updateDashboardStats(stats).blockingAwait();
                
                // Post to LiveData
                DashboardData dashboardData = new DashboardData(stats);
                _dashboardData.postValue(dashboardData);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update current stats", e);
        }
    }
    
    /**
     * Update KPIs
     */
    private void updateKpis() {
        try {
            List<KpiEntity> kpis = new ArrayList<>();
            
            // Delivery Rate KPI
            KpiEntity deliveryRateKpi = createDeliveryRateKpi();
            kpis.add(deliveryRateKpi);
            
            // Response Time KPI
            KpiEntity responseTimeKpi = createResponseTimeKpi();
            kpis.add(responseTimeKpi);
            
            // Campaign Success KPI
            KpiEntity campaignSuccessKpi = createCampaignSuccessKpi();
            kpis.add(campaignSuccessKpi);
            
            // Compliance KPI
            KpiEntity complianceKpi = createComplianceKpi();
            kpis.add(complianceKpi);
            
            // Financial KPIs
            KpiEntity costKpi = createCostKpi();
            kpis.add(costKpi);
            
            KpiEntity revenueKpi = createRevenueKpi();
            kpis.add(revenueKpi);
            
            // Save KPIs
            for (KpiEntity kpi : kpis) {
                dashboardDao.insertKpi(kpi).blockingAwait();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update KPIs", e);
        }
    }
    
    /**
     * Update trends
     */
    private void updateTrends() {
        try {
            long yesterday = getStartOfDay(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            long today = getStartOfDay(System.currentTimeMillis());
            
            DashboardMetricsEntity yesterdayMetrics = dashboardDao
                .getDashboardMetrics(yesterday, "DAILY").blockingGet();
            DashboardMetricsEntity todayMetrics = dashboardDao
                .getDashboardMetrics(today, "DAILY").blockingGet();
            
            if (yesterdayMetrics != null && todayMetrics != null) {
                DashboardTrend trend = new DashboardTrend(yesterdayMetrics, todayMetrics);
                _trendData.postValue(trend);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update trends", e);
        }
    }
    
    /**
     * Update alerts
     */
    private void updateAlerts() {
        try {
            List<KpiEntity> alerts = dashboardDao.getActiveAlerts().blockingGet();
            _kpiAlerts.postValue(alerts);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update alerts", e);
        }
    }
    
    /**
     * Aggregate daily metrics
     */
    private void aggregateDailyMetrics() {
        try {
            long yesterday = getStartOfDay(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
            long today = getStartOfDay(System.currentTimeMillis());
            
            // Create daily metrics from yesterday's data
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            if (stats != null) {
                DashboardMetricsEntity dailyMetrics = new DashboardMetricsEntity(yesterday, "DAILY");
                dailyMetrics.sentCount = stats.totalSent;
                dailyMetrics.deliveredCount = stats.totalDelivered;
                dailyMetrics.failedCount = stats.totalFailed;
                dailyMetrics.campaignCount = stats.totalCampaigns;
                dailyMetrics.activeCampaigns = stats.activeCampaigns;
                dailyMetrics.scheduledCampaigns = stats.scheduledCampaigns;
                dailyMetrics.optOutCount = stats.optOutCount;
                dailyMetrics.totalCost = stats.totalCost;
                dailyMetrics.totalRevenue = stats.totalRevenue;
                
                dashboardDao.insertDashboardMetrics(dailyMetrics).blockingAwait();
            }
            
            // Reset current stats for new day
            DashboardStatsEntity newDayStats = new DashboardStatsEntity("current");
            dashboardDao.updateDashboardStats(newDayStats).blockingAwait();
            
            Log.d(TAG, "Daily metrics aggregated");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to aggregate daily metrics", e);
        }
    }
    
    // Helper methods for KPI creation
    private KpiEntity createDeliveryRateKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double deliveryRate = stats != null ? stats.getDeliveryRate() : 0.0;
            
            KpiEntity kpi = new KpiEntity("DELIVERY_RATE", "Delivery Rate", deliveryRate, 95.0);
            kpi.thresholdWarning = 90.0;
            kpi.thresholdCritical = 85.0;
            kpi.unit = "%";
            kpi.category = "PERFORMANCE";
            kpi.description = "Percentage of messages successfully delivered";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("DELIVERY_RATE", "Delivery Rate", 0.0, 95.0);
        }
    }
    
    private KpiEntity createResponseTimeKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double responseTime = stats != null ? stats.averageDeliveryTime : 0.0;
            
            KpiEntity kpi = new KpiEntity("RESPONSE_TIME", "Response Time", responseTime, 5000.0);
            kpi.thresholdWarning = 10000.0;
            kpi.thresholdCritical = 15000.0;
            kpi.unit = "ms";
            kpi.category = "PERFORMANCE";
            kpi.description = "Average time for message delivery";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("RESPONSE_TIME", "Response Time", 0.0, 5000.0);
        }
    }
    
    private KpiEntity createCampaignSuccessKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double successRate = stats != null ? stats.getCampaignSuccessRate() : 0.0;
            
            KpiEntity kpi = new KpiEntity("CAMPAIGN_SUCCESS", "Campaign Success", successRate, 85.0);
            kpi.thresholdWarning = 75.0;
            kpi.thresholdCritical = 65.0;
            kpi.unit = "%";
            kpi.category = "PERFORMANCE";
            kpi.description = "Percentage of campaigns completed successfully";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("CAMPAIGN_SUCCESS", "Campaign Success", 0.0, 85.0);
        }
    }
    
    private KpiEntity createComplianceKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double complianceRate = stats != null ? 
                (stats.totalSent > 0 ? ((double)(stats.totalSent - stats.complianceViolations) / stats.totalSent * 100) : 100.0) : 100.0;
            
            KpiEntity kpi = new KpiEntity("COMPLIANCE_RATE", "Compliance Rate", complianceRate, 98.0);
            kpi.thresholdWarning = 95.0;
            kpi.thresholdCritical = 90.0;
            kpi.unit = "%";
            kpi.category = "COMPLIANCE";
            kpi.description = "Percentage of messages compliant with regulations";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("COMPLIANCE_RATE", "Compliance Rate", 100.0, 98.0);
        }
    }
    
    private KpiEntity createCostKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double avgCost = stats != null ? stats.getAverageCostPerSms() : 0.0;
            
            KpiEntity kpi = new KpiEntity("AVERAGE_COST", "Average Cost", avgCost, 0.05);
            kpi.thresholdWarning = 0.08;
            kpi.thresholdCritical = 0.10;
            kpi.unit = "$";
            kpi.category = "FINANCIAL";
            kpi.description = "Average cost per SMS message";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("AVERAGE_COST", "Average Cost", 0.0, 0.05);
        }
    }
    
    private KpiEntity createRevenueKpi() {
        try {
            DashboardStatsEntity stats = dashboardDao.getDashboardStats("current").blockingGet();
            double avgRevenue = stats != null ? stats.getAverageRevenuePerSms() : 0.0;
            
            KpiEntity kpi = new KpiEntity("AVERAGE_REVENUE", "Average Revenue", avgRevenue, 0.10);
            kpi.thresholdWarning = 0.05;
            kpi.thresholdCritical = 0.02;
            kpi.unit = "$";
            kpi.category = "FINANCIAL";
            kpi.description = "Average revenue per SMS message";
            kpi.updateStatus();
            
            return kpi;
        } catch (Exception e) {
            return new KpiEntity("AVERAGE_REVENUE", "Average Revenue", 0.0, 0.10);
        }
    }
    
    private void initializeKpis() {
        try {
            List<KpiEntity> kpis = List.of(
                createDeliveryRateKpi(),
                createResponseTimeKpi(),
                createCampaignSuccessKpi(),
                createComplianceKpi(),
                createCostKpi(),
                createRevenueKpi()
            );
            
            for (KpiEntity kpi : kpis) {
                dashboardDao.insertKpi(kpi).blockingAwait();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize KPIs", e);
        }
    }
    
    // Helper methods
    private long getStartOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
    
    private long calculateTimeUntilMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_MONTH, 1);
        
        return midnight.getTimeInMillis() - now.getTimeInMillis();
    }
    
    private List<DashboardDao.CampaignPerformanceData> getCampaignPerformanceData(long startDate, long endDate) {
        // This would query campaign performance data
        return new ArrayList<>();
    }
    
    private List<DashboardDao.ComplianceReportData> getComplianceReportData(long startDate, long endDate) {
        // This would query compliance report data
        return new ArrayList<>();
    }
    
    /**
     * Dashboard data model
     */
    public static class DashboardData {
        public final DashboardStatsEntity currentStats;
        public final List<DashboardMetricsEntity> metrics;
        public final DashboardDao.DashboardMetricsSummary summary;
        public final List<DashboardDao.CampaignPerformanceData> campaignPerformance;
        public final List<DashboardDao.ComplianceReportData> complianceReport;
        public final long startDate;
        public final long endDate;
        
        public DashboardData(DashboardStatsEntity currentStats) {
            this.currentStats = currentStats;
            this.metrics = new ArrayList<>();
            this.summary = null;
            this.campaignPerformance = new ArrayList<>();
            this.complianceReport = new ArrayList<>();
            this.startDate = 0;
            this.endDate = 0;
        }
        
        public DashboardData(
            List<DashboardMetricsEntity> metrics,
            DashboardDao.DashboardMetricsSummary summary,
            List<DashboardDao.CampaignPerformanceData> campaignPerformance,
            List<DashboardDao.ComplianceReportData> complianceReport,
            long startDate,
            long endDate
        ) {
            this.currentStats = null;
            this.metrics = metrics;
            this.summary = summary;
            this.campaignPerformance = campaignPerformance;
            this.complianceReport = complianceReport;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
    
    /**
     * Dashboard trend data
     */
    public static class DashboardTrend {
        public final float sentTrend;
        public final float deliveryRateTrend;
        public final float campaignTrend;
        public final String trendDirection;
        
        public DashboardTrend(DashboardMetricsEntity yesterday, DashboardMetricsEntity today) {
            this.sentTrend = calculateTrend(yesterday.sentCount, today.sentCount);
            this.deliveryRateTrend = calculateTrend(yesterday.getDeliveryRate(), today.getDeliveryRate());
            this.campaignTrend = calculateTrend(yesterday.campaignCount, today.campaignCount);
            this.trendDirection = determineTrendDirection(sentTrend, deliveryRateTrend, campaignTrend);
        }
        
        private float calculateTrend(double yesterday, double today) {
            if (yesterday == 0) return 0.0f;
            return (float) ((today - yesterday) / yesterday * 100.0);
        }
        
        private String determineTrendDirection(float... trends) {
            int up = 0, down = 0;
            for (float trend : trends) {
                if (trend > 5) up++;
                else if (trend < -5) down++;
            }
            
            if (up > down) return "UP";
            if (down > up) return "DOWN";
            return "STABLE";
        }
    }
}
