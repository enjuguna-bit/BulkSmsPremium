package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.annotation.NonNull;

/**
 * Entity for storing real-time dashboard statistics
 * Updated frequently for live dashboard updates
 */
@Entity(
    tableName = "dashboard_stats",
    indices = {
        @Index(value = {"statType"}),
        @Index(value = {"lastUpdated"})
    }
)
public class DashboardStatsEntity {
    
    @PrimaryKey
    @NonNull
    public String statType; // e.g., "current", "today", "week", "month"
    
    @ColumnInfo(name = "totalSent")
    public int totalSent = 0;
    
    @ColumnInfo(name = "totalDelivered")
    public int totalDelivered = 0;
    
    @ColumnInfo(name = "totalFailed")
    public int totalFailed = 0;
    
    @ColumnInfo(name = "totalPending")
    public int totalPending = 0;
    
    @ColumnInfo(name = "activeCampaigns")
    public int activeCampaigns = 0;
    
    @ColumnInfo(name = "scheduledCampaigns")
    public int scheduledCampaigns = 0;
    
    @ColumnInfo(name = "totalCampaigns")
    public int totalCampaigns = 0;
    
    @ColumnInfo(name = "totalRecipients")
    public int totalRecipients = 0;
    
    @ColumnInfo(name = "uniqueRecipients")
    public int uniqueRecipients = 0;
    
    @ColumnInfo(name = "optOutCount")
    public int optOutCount = 0;
    
    @ColumnInfo(name = "complianceViolations")
    public int complianceViolations = 0;
    
    @ColumnInfo(name = "averageDeliveryTime")
    public long averageDeliveryTime = 0;
    
    @ColumnInfo(name = "lastSentTime")
    public long lastSentTime = 0;
    
    @ColumnInfo(name = "lastDeliveryTime")
    public long lastDeliveryTime = 0;
    
    @ColumnInfo(name = "totalCost")
    public double totalCost = 0.0;
    
    @ColumnInfo(name = "totalRevenue")
    public double totalRevenue = 0.0;
    
    @ColumnInfo(name = "conversionRate")
    public float conversionRate = 0.0f;
    
    @ColumnInfo(name = "responseRate")
    public float responseRate = 0.0f;
    
    @ColumnInfo(name = "bounceRate")
    public float bounceRate = 0.0f;
    
    @ColumnInfo(name = "peakHourActivity")
    public int peakHourActivity = 0;
    
    @ColumnInfo(name = "currentRateLimit")
    public int currentRateLimit = 0;
    
    @ColumnInfo(name = "rateLimitStatus")
    public String rateLimitStatus; // NORMAL, WARNING, LIMITED
    
    @ColumnInfo(name = "systemStatus")
    public String systemStatus; // HEALTHY, WARNING, ERROR
    
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    public DashboardStatsEntity() {
        long now = System.currentTimeMillis();
        this.lastUpdated = now;
        this.createdAt = now;
    }
    
    public DashboardStatsEntity(String statType) {
        this();
        this.statType = statType;
    }
    
    // Calculated properties
    public float getDeliveryRate() {
        if (totalSent == 0) return 0.0f;
        return (float) totalDelivered / totalSent * 100.0f;
    }
    
    public float getFailureRate() {
        if (totalSent == 0) return 0.0f;
        return (float) totalFailed / totalSent * 100.0f;
    }
    
    public float getPendingRate() {
        if (totalSent == 0) return 0.0f;
        return (float) totalPending / totalSent * 100.0f;
    }
    
    public float getCampaignSuccessRate() {
        if (totalCampaigns == 0) return 0.0f;
        return (float) activeCampaigns / totalCampaigns * 100.0f;
    }
    
    public double getAverageCostPerSms() {
        if (totalSent == 0) return 0.0;
        return totalCost / totalSent;
    }
    
    public double getAverageRevenuePerSms() {
        if (totalSent == 0) return 0.0;
        return totalRevenue / totalSent;
    }
    
    public double getProfit() {
        return totalRevenue - totalCost;
    }
    
    public float getROI() {
        if (totalCost == 0.0) return 0.0f;
        return (float) ((totalRevenue - totalCost) / totalCost * 100.0);
    }
    
    public boolean isSystemHealthy() {
        return "HEALTHY".equals(systemStatus);
    }
    
    public boolean isRateLimited() {
        return "LIMITED".equals(rateLimitStatus);
    }
    
    public long getTimeSinceLastSent() {
        return System.currentTimeMillis() - lastSentTime;
    }
    
    public long getTimeSinceLastDelivery() {
        return System.currentTimeMillis() - lastDeliveryTime;
    }
    
    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        DashboardStatsEntity that = (DashboardStatsEntity) o;
        
        if (totalSent != that.totalSent) return false;
        if (totalDelivered != that.totalDelivered) return false;
        if (totalFailed != that.totalFailed) return false;
        if (activeCampaigns != that.activeCampaigns) return false;
        if (lastUpdated != that.lastUpdated) return false;
        
        if (statType != null ? !statType.equals(that.statType) : that.statType != null) 
            return false;
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = statType != null ? statType.hashCode() : 0;
        result = 31 * result + totalSent;
        result = 31 * result + totalDelivered;
        result = 31 * result + totalFailed;
        result = 31 * result + activeCampaigns;
        result = 31 * result + (int) (lastUpdated ^ (lastUpdated >>> 32));
        return result;
    }
}
