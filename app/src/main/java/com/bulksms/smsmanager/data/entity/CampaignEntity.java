package com.bulksms.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.Ignore;

@Entity(
    tableName = "campaign_entities",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"createdAt"})
    }
)
public class CampaignEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "description")
    public String description;
    
    @ColumnInfo(name = "status")
    public String status; // DRAFT, ACTIVE, PAUSED, COMPLETED, CANCELLED
    
    @ColumnInfo(name = "templateId")
    public Long templateId;
    
    @ColumnInfo(name = "recipientCount")
    public int recipientCount;
    
    @ColumnInfo(name = "sentCount")
    public int sentCount = 0;
    
    @ColumnInfo(name = "deliveredCount")
    public int deliveredCount = 0;
    
    @ColumnInfo(name = "failedCount")
    public int failedCount = 0;
    
    @ColumnInfo(name = "skippedCount")
    public int skippedCount = 0;
    
    @ColumnInfo(name = "scheduledAt")
    public Long scheduledAt;
    
    @ColumnInfo(name = "startedAt")
    public Long startedAt;
    
    @ColumnInfo(name = "completedAt")
    public Long completedAt;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    @ColumnInfo(name = "settings")
    public String settings; // JSON string for campaign settings
    
    public CampaignEntity() {
    }
    
    @Ignore
    public CampaignEntity(String name, String description, String status) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public boolean isDraft() {
        return "DRAFT".equals(status);
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
    
    public boolean isPaused() {
        return "PAUSED".equals(status);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }
    
    public double getDeliveryRate() {
        if (sentCount == 0) return 0.0;
        return (double) deliveredCount / sentCount * 100;
    }
    
    public double getSuccessRate() {
        if (recipientCount == 0) return 0.0;
        return (double) deliveredCount / recipientCount * 100;
    }
    
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        CampaignEntity that = (CampaignEntity) o;
        
        if (id != that.id) return false;
        if (recipientCount != that.recipientCount) return false;
        if (sentCount != that.sentCount) return false;
        if (deliveredCount != that.deliveredCount) return false;
        if (failedCount != that.failedCount) return false;
        if (createdAt != that.createdAt) return false;
        if (updatedAt != that.updatedAt) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (templateId != null ? !templateId.equals(that.templateId) : that.templateId != null) return false;
        if (scheduledAt != null ? !scheduledAt.equals(that.scheduledAt) : that.scheduledAt != null) return false;
        if (startedAt != null ? !startedAt.equals(that.startedAt) : that.startedAt != null) return false;
        if (completedAt != null ? !completedAt.equals(that.completedAt) : that.completedAt != null) return false;
        return settings != null ? settings.equals(that.settings) : that.settings == null;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (templateId != null ? templateId.hashCode() : 0);
        result = 31 * result + recipientCount;
        result = 31 * result + sentCount;
        result = 31 * result + deliveredCount;
        result = 31 * result + failedCount;
        result = 31 * result + (scheduledAt != null ? scheduledAt.hashCode() : 0);
        result = 31 * result + (startedAt != null ? startedAt.hashCode() : 0);
        result = 31 * result + (completedAt != null ? completedAt.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (int) (updatedAt ^ (updatedAt >>> 32));
        result = 31 * result + (settings != null ? settings.hashCode() : 0);
        return result;
    }
}
