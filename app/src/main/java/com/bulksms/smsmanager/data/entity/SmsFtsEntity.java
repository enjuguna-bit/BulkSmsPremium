package com.bulksms.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Fts4;

/**
 * Full-Text Search entity for SMS messages
 * Uses SQLite FTS4 for efficient text search
 */
@Fts4(contentEntity = SmsEntity.class)
@Entity(tableName = "sms_fts")
public class SmsFtsEntity {
    
    @PrimaryKey
    public long rowid;
    
    // Fields to be indexed for full-text search
    public String phoneNumber;
    public String message;
}
