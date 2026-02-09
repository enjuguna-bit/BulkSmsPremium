package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

/**
 * Entity for tracking opted-out phone numbers
 * Ensures compliance with marketing regulations
 */
@Entity(
    tableName = "opt_outs",
    indices = {
        @Index(value = {"phoneNumber"}, unique = true),
        @Index(value = {"optOutTime"})
    }
)
public class OptOutEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    @ColumnInfo(name = "phoneNumber")
    public String phoneNumber;
    
    @ColumnInfo(name = "reason")
    public String reason;
    
    @ColumnInfo(name = "optOutTime")
    public long optOutTime;
    
    @ColumnInfo(name = "source")
    public String source; // USER_REQUEST, COMPLAINT, BOUNCE, etc.
    
    @ColumnInfo(name = "campaignId")
    public Long campaignId; // Campaign that triggered opt-out
    
    @ColumnInfo(name = "notes")
    public String notes;
    
    @ColumnInfo(name = "isActive")
    public boolean isActive = true;
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;
    
    public OptOutEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    public OptOutEntity(String phoneNumber, String reason, String source) {
        this();
        this.phoneNumber = phoneNumber;
        this.reason = reason;
        this.source = source;
        this.optOutTime = System.currentTimeMillis();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        OptOutEntity that = (OptOutEntity) o;
        
        if (id != that.id) return false;
        if (isActive != that.isActive) return false;
        if (optOutTime != that.optOutTime) return false;
        if (phoneNumber != null ? !phoneNumber.equals(that.phoneNumber) : that.phoneNumber != null) 
            return false;
        if (reason != null ? !reason.equals(that.reason) : that.reason != null) 
            return false;
        if (source != null ? !source.equals(that.source) : that.source != null) 
            return false;
        if (campaignId != null ? !campaignId.equals(that.campaignId) : that.campaignId != null) 
            return false;
        return notes != null ? notes.equals(that.notes) : that.notes == null;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (phoneNumber != null ? phoneNumber.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        result = 31 * result + (int) (optOutTime ^ (optOutTime >>> 32));
        result = 31 * result + (source != null ? source.hashCode() : 0);
        result = 31 * result + (campaignId != null ? campaignId.hashCode() : 0);
        result = 31 * result + (notes != null ? notes.hashCode() : 0);
        result = 31 * result + (isActive ? 1 : 0);
        return result;
    }
}
