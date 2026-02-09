package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.ForeignKey;
import androidx.room.Ignore;

@Entity(
    tableName = "sms_entities",
    indices = {
        @Index(value = {"status"}),
        @Index(value = {"campaignId"}),
        @Index(value = {"phoneNumber"}),
        @Index(value = {"createdAt"}),
        @Index(value = {"nextRetryAt"}, unique = false),
        @Index(value = {"deviceSmsId"}, unique = true),
        @Index(value = {"boxType"}),
        @Index(value = {"isRead"}),
        @Index(value = {"threadId"})
    },
    foreignKeys = {
        @ForeignKey(
            entity = CampaignEntity.class,
            parentColumns = "id",
            childColumns = "campaignId",
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    }
)
public class SmsEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    /**
     * Telephony provider SMS _ID (from content://sms/)
     * Used to sync with device SMS database
     * Null for app-created messages not yet synced
     */
    @ColumnInfo(name = "deviceSmsId")
    public Long deviceSmsId;
    
    /**
     * SMS box type from Telephony.Sms.TYPE
     * 1=Inbox, 2=Sent, 3=Draft, 4=Outbox, 5=Failed, 6=Queued
     */
    @ColumnInfo(name = "boxType")
    public Integer boxType;

    /**
     * Thread ID from Telephony.Sms.THREAD_ID (groups messages by conversation)
     */
    @ColumnInfo(name = "threadId")
    public Long threadId;
    
    /**
     * Read status from Telephony.Sms.READ
     * 0=unread, 1=read
     */
    @ColumnInfo(name = "isRead")
    public Boolean isRead;
    
    @ColumnInfo(name = "phoneNumber")
    public String phoneNumber;
    
    // Alias for compatibility with inbox functionality
    public String getAddress() { return phoneNumber; }
    
    @ColumnInfo(name = "message")
    public String message;

    // MMS flag
    @ColumnInfo(name = "isMms")
    public Boolean isMms;

    // Optional media URI for MMS preview
    @ColumnInfo(name = "mediaUri")
    public String mediaUri;

    // Attachment count for MMS
    @ColumnInfo(name = "attachmentCount")
    public Integer attachmentCount;
    
    // Alias for compatibility with inbox functionality
    public String getBody() { return message; }
    
    @ColumnInfo(name = "status")
    public String status; // PENDING, SENT, DELIVERED, FAILED
    
    // Alias for compatibility with inbox functionality
    public String getType() { return status; }
    
    @ColumnInfo(name = "createdAt")
    public long createdAt;
    
    // Alias for compatibility with inbox functionality
    public long getDate() { return createdAt; }
    
    // Alias for compatibility with inbox functionality
    public boolean isUnread() { 
        // Use isRead field if available, otherwise fallback to status
        if (isRead != null) {
            return !isRead;
        }
        return "PENDING".equals(status) || "SENT".equals(status) || "RECEIVED".equals(status); 
    }
    
    /**
     * Check if this is an inbox message
     */
    public boolean isInboxMessage() {
        return boxType != null && boxType == 1; // Telephony.Sms.MESSAGE_TYPE_INBOX
    }
    
    /**
     * Check if this is a sent message
     */
    public boolean isSentMessage() {
        return boxType != null && boxType == 2; // Telephony.Sms.MESSAGE_TYPE_SENT
    }
    
    @ColumnInfo(name = "sentAt")
    public Long sentAt;
    
    @ColumnInfo(name = "deliveredAt")
    public Long deliveredAt;
    
    @ColumnInfo(name = "campaignId")
    public Long campaignId;
    
    @ColumnInfo(name = "retryCount")
    public int retryCount = 0;
    
    @ColumnInfo(name = "nextRetryAt")
    public Long nextRetryAt;
    
    @ColumnInfo(name = "errorCode")
    public String errorCode;
    
    @ColumnInfo(name = "errorMessage")
    public String errorMessage;
    
    public SmsEntity() {
    }
    
    @Ignore
    public SmsEntity(String phoneNumber, String message, String status, long createdAt) {
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    public boolean isPending() {
        return "PENDING".equals(status);
    }
    
    public boolean isSent() {
        return "SENT".equals(status);
    }
    
    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean canRetry() {
        return isFailed() && retryCount < 3;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SmsEntity smsEntity = (SmsEntity) o;
        
        if (id != smsEntity.id) return false;
        if (createdAt != smsEntity.createdAt) return false;
        if (retryCount != smsEntity.retryCount) return false;
        if (phoneNumber != null ? !phoneNumber.equals(smsEntity.phoneNumber) : smsEntity.phoneNumber != null) return false;
        if (message != null ? !message.equals(smsEntity.message) : smsEntity.message != null) return false;
        if (status != null ? !status.equals(smsEntity.status) : smsEntity.status != null) return false;
        if (campaignId != null ? !campaignId.equals(smsEntity.campaignId) : smsEntity.campaignId != null) return false;
        if (errorCode != null ? !errorCode.equals(smsEntity.errorCode) : smsEntity.errorCode != null) return false;
        return errorMessage != null ? errorMessage.equals(smsEntity.errorMessage) : smsEntity.errorMessage == null;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (phoneNumber != null ? phoneNumber.hashCode() : 0);
        result = 31 * result + (message != null ? message.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (int) (createdAt ^ (createdAt >>> 32));
        result = 31 * result + (sentAt != null ? sentAt.hashCode() : 0);
        result = 31 * result + (deliveredAt != null ? deliveredAt.hashCode() : 0);
        result = 31 * result + (campaignId != null ? campaignId.hashCode() : 0);
        result = 31 * result + retryCount;
        result = 31 * result + (nextRetryAt != null ? nextRetryAt.hashCode() : 0);
        result = 31 * result + (errorCode != null ? errorCode.hashCode() : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }
}
