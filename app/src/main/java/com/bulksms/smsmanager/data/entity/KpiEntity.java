package com.bulksms.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

/**
 * Entity for storing KPI (Key Performance Indicator) data
 * Enables tracking of business metrics and performance indicators
 */
@Entity(
    tableName = "kpi_data",
    indices = {
        @Index(value = {"kpiType"}),
        @Index(value = {"timestamp"}),
        @Index(value = {"period"})
    }
)
public class KpiEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "kpiType")
    public String kpiType; // DELIVERY_RATE, RESPONSE_TIME, CAMPAIGN_SUCCESS, etc.
    
    @ColumnInfo(name = "kpiName")
    public String kpiName; // Human-readable name
    
    @ColumnInfo(name = "kpiValue")
    public double kpiValue;
    
    @ColumnInfo(name = "targetValue")
    public double targetValue;
    
    @ColumnInfo(name = "thresholdWarning")
    public double thresholdWarning;
    
    @ColumnInfo(name = "thresholdCritical")
    public double thresholdCritical;
    
    @ColumnInfo(name = "period")
    public String period; // DAILY, WEEKLY, MONTHLY
    
    @ColumnInfo(name = "timestamp")
    public long timestamp;
    
    @ColumnInfo(name = "status")
    public String status; // GOOD, WARNING, CRITICAL
    
    @ColumnInfo(name = "trend")
    public String trend; // UP, DOWN, STABLE
    
    @ColumnInfo(name = "trendPercentage")
    public float trendPercentage;
    
    @ColumnInfo(name = "unit")
    public String unit; // %, ms, count, $, etc.
    
    @ColumnInfo(name = "category")
    public String category; // PERFORMANCE, COMPLIANCE, FINANCIAL, USER_ENGAGEMENT
    
    @ColumnInfo(name = "description")
    public String description;
    
    @ColumnInfo(name = "isAlert")
    public boolean isAlert = false;
    
    @ColumnInfo(name = "alertMessage")
    public String alertMessage;
    
    @ColumnInfo(name = "metadata")
    public String metadata; // JSON string for additional data
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public KpiEntity() {
        long now = System.currentTimeMillis();
        this.timestamp = now;
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    public KpiEntity(String kpiType, String kpiName, double kpiValue, double targetValue) {
        this();
        this.kpiType = kpiType;
        this.kpiName = kpiName;
        this.kpiValue = kpiValue;
        this.targetValue = targetValue;
        updateStatus();
    }
    
    public void updateStatus() {
        if (kpiValue >= thresholdCritical) {
            this.status = "CRITICAL";
            this.isAlert = true;
            this.alertMessage = kpiName + " has reached critical level: " + formatValue(kpiValue);
        } else if (kpiValue >= thresholdWarning) {
            this.status = "WARNING";
            this.isAlert = true;
            this.alertMessage = kpiName + " requires attention: " + formatValue(kpiValue);
        } else {
            this.status = "GOOD";
            this.isAlert = false;
            this.alertMessage = null;
        }
    }
    
    public void updateTrend(double previousValue) {
        if (previousValue == 0) {
            this.trend = "STABLE";
            this.trendPercentage = 0.0f;
        } else {
            double change = ((kpiValue - previousValue) / previousValue) * 100.0;
            this.trendPercentage = (float) change;
            
            if (change > 5.0) {
                this.trend = "UP";
            } else if (change < -5.0) {
                this.trend = "DOWN";
            } else {
                this.trend = "STABLE";
            }
        }
    }
    
    public double getPerformanceRatio() {
        if (targetValue == 0) return 0.0;
        return kpiValue / targetValue;
    }
    
    public boolean isPerformingWell() {
        return "GOOD".equals(status);
    }
    
    public boolean needsAttention() {
        return "WARNING".equals(status) || "CRITICAL".equals(status);
    }
    
    public String formatValue(double value) {
        if ("%".equals(unit)) {
            return String.format("%.1f%%", value);
        } else if ("ms".equals(unit)) {
            return String.format("%.0f ms", value);
        } else if ("$".equals(unit)) {
            return String.format("$%.2f", value);
        } else if (value == (long) value) {
            return String.format("%d", (long) value);
        } else {
            return String.format("%.2f", value);
        }
    }
    
    public void updateTimestamp() {
        this.timestamp = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        KpiEntity that = (KpiEntity) o;
        
        if (id != that.id) return false;
        if (Double.compare(that.kpiValue, kpiValue) != 0) return false;
        if (Double.compare(that.targetValue, targetValue) != 0) return false;
        if (timestamp != that.timestamp) return false;
        if (isAlert != that.isAlert) return false;
        
        if (kpiType != null ? !kpiType.equals(that.kpiType) : that.kpiType != null) 
            return false;
        if (kpiName != null ? !kpiName.equals(that.kpiName) : that.kpiName != null) 
            return false;
        if (status != null ? !status.equals(that.status) : that.status != null) 
            return false;
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (kpiType != null ? kpiType.hashCode() : 0);
        result = 31 * result + (kpiName != null ? kpiName.hashCode() : 0);
        long temp = Double.doubleToLongBits(kpiValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(targetValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + (isAlert ? 1 : 0);
        return result;
    }
}
