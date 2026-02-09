package com.afriserve.smsmanager.data.dao;

import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.RawQuery;

import androidx.sqlite.db.SupportSQLiteQuery;

import com.afriserve.smsmanager.data.entity.DashboardMetricsEntity;
import com.afriserve.smsmanager.data.entity.DashboardStatsEntity;
import com.afriserve.smsmanager.data.entity.KpiEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for Dashboard entities
 * Provides comprehensive dashboard data operations
 */
@Dao
public interface DashboardDao {
    
    // ==================== Dashboard Metrics ====================
    
    @Insert
    Completable insertDashboardMetrics(DashboardMetricsEntity metrics);
    
    @Insert
    Completable insertDashboardMetricsList(List<DashboardMetricsEntity> metricsList);
    
    @Update
    Completable updateDashboardMetrics(DashboardMetricsEntity metrics);
    
    @Query("SELECT * FROM dashboard_metrics WHERE metricDate = :date AND metricType = :type LIMIT 1")
    Single<DashboardMetricsEntity> getDashboardMetrics(long date, String type);
    
    @Query("SELECT * FROM dashboard_metrics WHERE metricType = :type ORDER BY metricDate DESC")
    PagingSource<Integer, DashboardMetricsEntity> getMetricsByTypePaged(String type);
    
    @Query("SELECT * FROM dashboard_metrics WHERE metricDate BETWEEN :startDate AND :endDate ORDER BY metricDate ASC")
    Single<List<DashboardMetricsEntity>> getMetricsInDateRange(long startDate, long endDate);
    
    @Query("SELECT * FROM dashboard_metrics WHERE metricDate >= :since ORDER BY metricDate DESC LIMIT :limit")
    Single<List<DashboardMetricsEntity>> getRecentMetrics(long since, int limit);
    
    @Query("SELECT * FROM dashboard_metrics WHERE metricType = :type AND metricDate >= :since ORDER BY metricDate DESC LIMIT 1")
    Single<DashboardMetricsEntity> getLatestMetricsByType(String type, long since);
    
    @Query("SELECT AVG(sentCount) as avgSent, AVG(deliveredCount) as avgDelivered, AVG(failedCount) as avgFailed FROM dashboard_metrics WHERE metricDate >= :since")
    Single<DashboardMetricsAverage> getAverageMetricsSince(long since);
    
    @Query("SELECT SUM(sentCount) as totalSent, SUM(deliveredCount) as totalDelivered, SUM(failedCount) as totalFailed FROM dashboard_metrics WHERE metricDate BETWEEN :startDate AND :endDate")
    Single<DashboardMetricsSummary> getTotalMetricsInRange(long startDate, long endDate);
    
    @Query("DELETE FROM dashboard_metrics WHERE metricDate < :before")
    Completable deleteOldMetrics(long before);
    
    // ==================== Dashboard Stats ====================
    
    @Insert
    Completable insertDashboardStats(DashboardStatsEntity stats);
    
    @Update
    Completable updateDashboardStats(DashboardStatsEntity stats);
    
    @Query("SELECT * FROM dashboard_stats WHERE statType = :type LIMIT 1")
    Single<DashboardStatsEntity> getDashboardStats(String type);
    
    @Query("SELECT * FROM dashboard_stats ORDER BY lastUpdated DESC")
    Single<List<DashboardStatsEntity>> getAllDashboardStats();
    
    @Query("UPDATE dashboard_stats SET totalSent = totalSent + :increment, lastUpdated = :timestamp WHERE statType = :type")
    Completable incrementTotalSent(String type, int increment, long timestamp);
    
    @Query("UPDATE dashboard_stats SET totalDelivered = totalDelivered + :increment, lastDeliveryTime = :timestamp, lastUpdated = :timestamp WHERE statType = :type")
    Completable incrementTotalDelivered(String type, int increment, long timestamp);
    
    @Query("UPDATE dashboard_stats SET totalFailed = totalFailed + :increment, lastUpdated = :timestamp WHERE statType = :type")
    Completable incrementTotalFailed(String type, int increment, long timestamp);
    
    @Query("UPDATE dashboard_stats SET activeCampaigns = :count, lastUpdated = :timestamp WHERE statType = :type")
    Completable updateActiveCampaigns(String type, int count, long timestamp);
    
    @Query("SELECT COUNT(*) FROM campaign_entities WHERE scheduledAt IS NOT NULL AND scheduledAt > :currentTime AND status = 'DRAFT'")
    Single<Integer> getPendingScheduledCampaignsCount(long currentTime);
    
    @Query("UPDATE dashboard_stats SET scheduledCampaigns = :count, lastUpdated = :timestamp WHERE statType = :type")
    Completable updateScheduledCampaigns(String type, int count, long timestamp);
    
    @Query("UPDATE dashboard_stats SET totalCost = totalCost + :cost, lastUpdated = :timestamp WHERE statType = :type")
    Completable updateTotalCost(String type, double cost, long timestamp);
    
    @Query("UPDATE dashboard_stats SET totalRevenue = totalRevenue + :revenue, lastUpdated = :timestamp WHERE statType = :type")
    Completable updateTotalRevenue(String type, double revenue, long timestamp);
    
    // ==================== KPI Data ====================
    
    @Insert
    Completable insertKpi(KpiEntity kpi);
    
    @Insert
    Completable insertKpiList(List<KpiEntity> kpiList);
    
    @Update
    Completable updateKpi(KpiEntity kpi);
    
    @Query("SELECT * FROM kpi_data WHERE kpiType = :type ORDER BY timestamp DESC LIMIT 1")
    Single<KpiEntity> getLatestKpiByType(String type);
    
    @Query("SELECT * FROM kpi_data WHERE category = :category ORDER BY timestamp DESC")
    Single<List<KpiEntity>> getKpisByCategory(String category);
    
    @Query("SELECT * FROM kpi_data WHERE isAlert = 1 ORDER BY timestamp DESC")
    Single<List<KpiEntity>> getActiveAlerts();
    
    @Query("SELECT * FROM kpi_data WHERE period = :period ORDER BY timestamp DESC")
    Single<List<KpiEntity>> getKpisByPeriod(String period);
    
    @Query("SELECT * FROM kpi_data WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp ASC")
    Single<List<KpiEntity>> getKpisInDateRange(long startDate, long endDate);
    
    @Query("SELECT * FROM kpi_data WHERE kpiType = :type AND timestamp >= :since ORDER BY timestamp DESC")
    Single<List<KpiEntity>> getKpiHistory(String type, long since);
    
    @Query("DELETE FROM kpi_data WHERE timestamp < :before")
    Completable deleteOldKpis(long before);
    
    // ==================== Analytics Queries ====================
    
    @RawQuery
    Single<List<DailyTrendData>> getDailyTrends(SupportSQLiteQuery query);
    
    @RawQuery
    Single<List<HourlyActivityData>> getHourlyActivity(SupportSQLiteQuery query);
    
    @RawQuery
    Single<List<CampaignPerformanceData>> getCampaignPerformance(SupportSQLiteQuery query);
    
    @RawQuery
    Single<List<ComplianceReportData>> getComplianceReport(SupportSQLiteQuery query);
    
    // ==================== Summary Classes ====================
    
    class DashboardMetricsSummary {
        public final int totalSent;
        public final int totalDelivered;
        public final int totalFailed;
        
        public DashboardMetricsSummary(int totalSent, int totalDelivered, int totalFailed) {
            this.totalSent = totalSent;
            this.totalDelivered = totalDelivered;
            this.totalFailed = totalFailed;
        }
        
        public float getDeliveryRate() {
            return totalSent > 0 ? (float) totalDelivered / totalSent * 100.0f : 0.0f;
        }
    }

    class DashboardMetricsAverage {
        public final double avgSent;
        public final double avgDelivered;
        public final double avgFailed;

        public DashboardMetricsAverage(double avgSent, double avgDelivered, double avgFailed) {
            this.avgSent = avgSent;
            this.avgDelivered = avgDelivered;
            this.avgFailed = avgFailed;
        }

        public float getAverageDeliveryRate() {
            return avgSent > 0 ? (float) (avgDelivered / avgSent * 100.0) : 0.0f;
        }
    }
    
    class DailyTrendData {
        public final long date;
        public final int sentCount;
        public final int deliveredCount;
        public final int failedCount;
        public final float deliveryRate;
        
        public DailyTrendData(long date, int sentCount, int deliveredCount, int failedCount, float deliveryRate) {
            this.date = date;
            this.sentCount = sentCount;
            this.deliveredCount = deliveredCount;
            this.failedCount = failedCount;
            this.deliveryRate = deliveryRate;
        }
    }
    
    class HourlyActivityData {
        public final int hour;
        public final int activityCount;
        public final int sentCount;
        public final int deliveredCount;
        
        public HourlyActivityData(int hour, int activityCount, int sentCount, int deliveredCount) {
            this.hour = hour;
            this.activityCount = activityCount;
            this.sentCount = sentCount;
            this.deliveredCount = deliveredCount;
        }
    }
    
    class CampaignPerformanceData {
        public final long campaignId;
        public final String campaignName;
        public final int sentCount;
        public final int deliveredCount;
        public final float deliveryRate;
        public final long executionTime;
        
        public CampaignPerformanceData(long campaignId, String campaignName, int sentCount, int deliveredCount, float deliveryRate, long executionTime) {
            this.campaignId = campaignId;
            this.campaignName = campaignName;
            this.sentCount = sentCount;
            this.deliveredCount = deliveredCount;
            this.deliveryRate = deliveryRate;
            this.executionTime = executionTime;
        }
    }
    
    class ComplianceReportData {
        public final String violationType;
        public final int violationCount;
        public final float percentage;
        
        public ComplianceReportData(String violationType, int violationCount, float percentage) {
            this.violationType = violationType;
            this.violationCount = violationCount;
            this.percentage = percentage;
        }
    }
}
