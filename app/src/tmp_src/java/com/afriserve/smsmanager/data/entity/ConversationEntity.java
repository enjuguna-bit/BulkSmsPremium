package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.ForeignKey;

/**
 * Conversation entity for threading messages
 * Groups messages by phone number/contact
 */
@Entity(
    tableName = "conversations",
    indices = {
        @Index(value = {"phoneNumber"}, unique = true),
        @Index(value = {"lastMessageTime"})
    }
)
public class ConversationEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // Phone number (normalized)
    public String phoneNumber;
    
    // Contact name (resolved)
    public String contactName;
    
    // Contact photo URI
    public String contactPhotoUri;
    
    // Last message details
    public long lastMessageTime;
    public String lastMessagePreview;
    public String lastMessageType; // INBOX, SENT, etc.
    
    // Conversation statistics
    public int messageCount;
    public int unreadCount;
    
    // Metadata
    public long createdAt;
    public long updatedAt;
    
    // Is this conversation archived?
    public boolean isArchived = false;
    
    // Is this conversation pinned?
    public boolean isPinned = false;
    
    public ConversationEntity() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ConversationEntity that = (ConversationEntity) o;
        
        if (id != that.id) return false;
        if (phoneNumber != null ? !phoneNumber.equals(that.phoneNumber) : that.phoneNumber != null) 
            return false;
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (phoneNumber != null ? phoneNumber.hashCode() : 0);
        return result;
    }
}
