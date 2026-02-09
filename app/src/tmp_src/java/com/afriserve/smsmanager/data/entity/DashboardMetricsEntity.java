package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

/**
 * Entity for storing daily dashboard metrics
 * Enables historical analytics and trend analysis
 */
@Entity(
    tableName = "dashboard_metrics",
    indices = {
        @Index(value = {"metricDate"}),
        @Index(value = {"metricType"}),
        @Index(value = {"createdAt"})
    }
)
public class DashboardMetricsEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "metricDate")
    public long metricDate; // Start of day timestamp
    
    @ColumnInfo(name = "metricType")
    public String metricType; // DAILY, WEEKLY, MONTHLY
    
    @ColumnInfo(name = "sentCount")
    public int sentCount = 0;
    
    @ColumnInfo(name = "deliveredCount")
    public int deliveredCount = 0;
    
    @ColumnInfo(name = "failedCount")
    public int failedCount = 0;
    
    @ColumnInfo(name = "pendingCount")
    public int pendingCount = 0;
    
    @ColumnInfo(name = "campaignCount")
    public int campaignCount = 0;
    
    @ColumnInfo(name = "activeCampaigns")
    public int activeCampaigns = 0;
    
    @ColumnInfo(name = "scheduledCampaigns")
    public int scheduledCampaigns = 0;
    
    @ColumnInfo(name = "optOutCount")
    public int optOutCount = 0;
    
    @ColumnInfo(name = "complianceViolations")
    public int complianceViolations = 0;
    
    @ColumnInfo(name = "averageDeliveryTime")
    public long averageDeliveryTime = 0; // in milliseconds
    
    @ColumnInfo(name = "peakHour")
    public int peakHour = -1; // 0-23, -1 if no data
    
    @ColumnInfo(name = "totalRecipients")
    public int totalRecipients = 0;
    
    @ColumnInfo(name = "uniqueRecipients")
    public int uniqueRecipients = 0;
    
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
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public DashboardMetricsEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    public DashboardMetricsEntity(long metricDate, String metricType) {
        this();
        this.metricDate = metricDate;
        this.metricType = metricType;
    }
    
    // Calculated properties
    public float getDeliveryRate() {
        if (sentCount == 0) return 0.0f;
        return (float) deliveredCount / sentCount * 100.0f;
    }
    
    public float getFailureRate() {
        if (sentCount == 0) return 0.0f;
        return (float) failedCount / sentCount * 100.0f;
    }
    
    public float getSuccessRate() {
        if (totalRecipients == 0) return 0.0f;
        return (float) deliveredCount / totalRecipients * 100.0f;
    }
    
    public float getCampaignSuccessRate() {
        if (campaignCount == 0) return 0.0f;
        return (float) activeCampaigns / campaignCount * 100.0f;
    }
    
    public double getAverageCostPerSms() {
        if (sentCount == 0) return 0.0;
        return totalCost / sentCount;
    }
    
    public double getAverageRevenuePerSms() {
        if (sentCount == 0) return 0.0;
        return totalRevenue / sentCount;
    }
    
    public double getProfit() {
        return totalRevenue - totalCost;
    }
    
    public float getROI() {
        if (totalCost == 0.0) return 0.0f;
        return (float) ((totalRevenue - totalCost) / totalCost * 100.0);
    }
    
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        DashboardMetricsEntity that = (DashboardMetricsEntity) o;
        
        if (id != that.id) return false;
        if (metricDate != that.metricDate) return false;
        if (sentCount != that.sentCount) return false;
        if (deliveredCount != that.deliveredCount) return false;
        if (failedCount != that.failedCount) return false;
        if (campaignCount != that.campaignCount) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        
        if (metricType != null ? !metricType.equals(that.metricType) : that.metricType != null) 
            return false;
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (metricDate ^ (metricDate >>> 32));
        result = 31 * result + (metricType != null ? metricType.hashCode() : 0);
        result = 31 * result + sentCount;
        result = 31 * result + deliveredCount;
        result = 31 * result + failedCount;
        result = 31 * result + campaignCount;
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
