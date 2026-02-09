package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

/**
 * Entity for scheduled SMS campaigns
 * Supports one-time and recurring campaigns
 */
@Entity(
    tableName = "scheduled_campaigns",
    indices = {
        @Index(value = {"campaignId"}),
        @Index(value = {"scheduledTime"}),
        @Index(value = {"status"}),
        @Index(value = {"isActive"})
    }
)
public class ScheduledCampaignEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "campaignId")
    public long campaignId;
    
    @ColumnInfo(name = "scheduledTime")
    public long scheduledTime;
    
    @ColumnInfo(name = "timezone")
    public String timezone; // IANA timezone identifier
    
    @ColumnInfo(name = "status")
    public String status; // SCHEDULED, EXECUTING, COMPLETED, FAILED, CANCELLED
    
    @ColumnInfo(name = "isActive")
    public boolean isActive = true;
    
    @ColumnInfo(name = "isRecurring")
    public boolean isRecurring = false;
    
    @ColumnInfo(name = "recurrencePattern")
    public String recurrencePattern; // daily, weekly, monthly, yearly, custom
    
    @ColumnInfo(name = "recurrenceInterval")
    public int recurrenceInterval = 1; // Every N days/weeks/months
    
    @ColumnInfo(name = "recurrenceDays")
    public String recurrenceDays; // For weekly: "1,2,3" (Mon,Tue,Wed)
    
    @ColumnInfo(name = "recurrenceTime")
    public String recurrenceTime; // HH:MM format
    
    @ColumnInfo(name = "maxOccurrences")
    public Integer maxOccurrences; // null for infinite
    
    @ColumnInfo(name = "currentOccurrences")
    public int currentOccurrences = 0;
    
    @ColumnInfo(name = "nextExecutionTime")
    public Long nextExecutionTime;
    
    @ColumnInfo(name = "lastExecutionTime")
    public Long lastExecutionTime;
    
    @ColumnInfo(name = "executionHistory")
    public String executionHistory; // JSON array of execution records
    
    @ColumnInfo(name = "settings")
    public String settings; // JSON string for additional settings
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public ScheduledCampaignEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    @Ignore
    public ScheduledCampaignEntity(long campaignId, long scheduledTime, String timezone) {
        this();
        this.campaignId = campaignId;
        this.scheduledTime = scheduledTime;
        this.timezone = timezone;
        this.status = "SCHEDULED";
        this.nextExecutionTime = scheduledTime;
    }
    
    public boolean isScheduled() {
        return "SCHEDULED".equals(status);
    }
    
    public boolean isExecuting() {
        return "EXECUTING".equals(status);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }
    
    public boolean shouldExecute() {
        return isActive && isScheduled() && 
               nextExecutionTime != null && 
               nextExecutionTime <= System.currentTimeMillis();
    }
    
    public void markAsExecuting() {
        this.status = "EXECUTING";
        this.lastExecutionTime = System.currentTimeMillis();
        this.currentOccurrences++;
        updateTimestamp();
    }
    
    public void markAsCompleted() {
        this.status = "COMPLETED";
        updateTimestamp();
        
        if (isRecurring) {
            scheduleNextExecution();
        } else {
            this.isActive = false;
        }
    }
    
    public void markAsFailed(String reason) {
        this.status = "FAILED";
        updateTimestamp();
        
        // Add to execution history
        addToExecutionHistory("FAILED", reason);
    }
    
    public void cancel() {
        this.status = "CANCELLED";
        this.isActive = false;
        updateTimestamp();
    }
    
    private void scheduleNextExecution() {
        if (!isRecurring || maxOccurrences != null && currentOccurrences >= maxOccurrences) {
            this.isActive = false;
            return;
        }
        
        long nextTime = calculateNextExecutionTime();
        if (nextTime > 0) {
            this.nextExecutionTime = nextTime;
            this.status = "SCHEDULED";
        } else {
            this.isActive = false;
        }
    }
    
    private long calculateNextExecutionTime() {
        if (lastExecutionTime == 0) {
            return scheduledTime;
        }
        
        long baseTime = lastExecutionTime;
        
        switch (recurrencePattern.toLowerCase()) {
            case "daily":
                return baseTime + (recurrenceInterval * 24L * 60L * 60L * 1000L);
                
            case "weekly":
                return baseTime + (recurrenceInterval * 7L * 24L * 60L * 60L * 1000L);
                
            case "monthly":
                return addMonths(baseTime, recurrenceInterval);
                
            case "yearly":
                return addYears(baseTime, recurrenceInterval);
                
            default:
                return 0; // Invalid pattern
        }
    }
    
    private long addMonths(long time, int months) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(java.util.Calendar.MONTH, months);
        return cal.getTimeInMillis();
    }
    
    private long addYears(long time, int years) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(java.util.Calendar.YEAR, years);
        return cal.getTimeInMillis();
    }
    
    private void addToExecutionHistory(String status, String details) {
        // This would typically use JSON serialization
        // For simplicity, we'll use a string format
        String record = System.currentTimeMillis() + ":" + status + ":" + details;
        
        if (executionHistory == null || executionHistory.isEmpty()) {
            executionHistory = "[" + record + "]";
        } else {
            executionHistory = executionHistory.substring(0, executionHistory.length() - 1) + "," + record + "]";
        }
    }
    
    private void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ScheduledCampaignEntity that = (ScheduledCampaignEntity) o;
        
        if (id != that.id) return false;
        if (campaignId != that.campaignId) return false;
        if (scheduledTime != that.scheduledTime) return false;
        if (isActive != that.isActive) return false;
        if (isRecurring != that.isRecurring) return false;
        if (recurrenceInterval != that.recurrenceInterval) return false;
        if (currentOccurrences != that.currentOccurrences) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        
        if (timezone != null ? !timezone.equals(that.timezone) : that.timezone != null) 
            return false;
        if (status != null ? !status.equals(that.status) : that.status != null) 
            return false;
        if (recurrencePattern != null ? !recurrencePattern.equals(that.recurrencePattern) : that.recurrencePattern != null) 
            return false;
        if (recurrenceDays != null ? !recurrenceDays.equals(that.recurrenceDays) : that.recurrenceDays != null) 
            return false;
        if (recurrenceTime != null ? !recurrenceTime.equals(that.recurrenceTime) : that.recurrenceTime != null) 
            return false;
        if (nextExecutionTime != null ? !nextExecutionTime.equals(that.nextExecutionTime) : that.nextExecutionTime != null) 
            return false;
        if (lastExecutionTime != null ? !lastExecutionTime.equals(that.lastExecutionTime) : that.lastExecutionTime != null) 
            return false;
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (int) (campaignId ^ (campaignId >>> 32));
        result = 31 * result + (int) (scheduledTime ^ (scheduledTime >>> 32));
        result = 31 * result + (timezone != null ? timezone.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (isActive ? 1 : 0);
        result = 31 * result + (isRecurring ? 1 : 0);
        result = 31 * result + (recurrencePattern != null ? recurrencePattern.hashCode() : 0);
        result = 31 * result + recurrenceInterval;
        result = 31 * result + (recurrenceDays != null ? recurrenceDays.hashCode() : 0);
        result = 31 * result + (recurrenceTime != null ? recurrenceTime.hashCode() : 0);
        result = 31 * result + (maxOccurrences != null ? maxOccurrences.hashCode() : 0);
        result = 31 * result + currentOccurrences;
        result = 31 * result + (nextExecutionTime != null ? nextExecutionTime.hashCode() : 0);
        result = 31 * result + (lastExecutionTime != null ? lastExecutionTime.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        return result;
    }
}
