package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * SMS Queue Entity for managing failed SMS retries
 */
@Entity(
    tableName = "sms_queue",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"nextRetryAt"}),
        @Index(value = {"phoneNumber"}),
        @Index(value = {"createdAt"})
    }
)
public class SmsQueueEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // Phone number to send SMS to
    public String phoneNumber;
    
    // SMS message content
    public String message;
    
    // SIM slot to use for sending
    public int simSlot;
    
    // Reference to original SMS entity
    public Long originalSmsId;
    
    // Number of retry attempts
    public int retryCount;
    
    // Status: PENDING, PROCESSING, FAILED, EXHAUSTED
    public String status;
    
    // Timestamps
    public long createdAt;
    public long nextRetryAt;
    public Long lastFailureAt;
    
    // Error information
    public String errorMessage;
    public String errorCode;
    
    public SmsQueueEntity() {
        this.createdAt = System.currentTimeMillis();
        this.retryCount = 0;
        this.status = "PENDING";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SmsQueueEntity that = (SmsQueueEntity) o;
        return id == that.id;
    }
    
    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
