package com.afriserve.smsmanager.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
import androidx.room.Ignore;

/**
 * Entity to track synchronization status and metadata
 * Enables offline-first architecture with proper sync state management
 */
@Entity(tableName = "sync_status", indices = { @Index(value = { "entityType", "entityId" }, unique = true) })
public class SyncStatusEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Entity being tracked (sms, conversation, contact, etc.)
    public String entityType;

    // ID of the entity being tracked
    public String entityId;

    // Last sync timestamp
    public long lastSyncAt;

    // Last modified timestamp on server
    public long lastServerModifiedAt;

    // Sync status
    public SyncStatus status;

    // Conflict resolution data
    public String conflictData;

    // ETag for HTTP caching
    public String eTag;

    // Sync version for conflict resolution
    public long syncVersion;

    // Pending operations count
    public int pendingOperations;

    // Error information
    public String lastError;

    public enum SyncStatus {
        SYNCED, // Fully synced with server
        PENDING_UPLOAD, // Local changes pending upload
        PENDING_DOWNLOAD, // Server changes pending download
        CONFLICT, // Sync conflict needs resolution
        ERROR, // Sync error occurred
        OFFLINE // Device is offline
    }

    public SyncStatusEntity() {
        this.status = SyncStatus.OFFLINE;
        this.lastSyncAt = System.currentTimeMillis();
        this.syncVersion = 1;
        this.pendingOperations = 0;
    }

    @Ignore
    public SyncStatusEntity(String entityType, String entityId) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /**
     * Check if this entity needs sync
     */
    public boolean needsSync() {
        return status == SyncStatus.PENDING_UPLOAD ||
                status == SyncStatus.PENDING_DOWNLOAD ||
                status == SyncStatus.CONFLICT ||
                status == SyncStatus.ERROR;
    }

    /**
     * Check if this entity is up to date
     */
    public boolean isUpToDate() {
        return status == SyncStatus.SYNCED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SyncStatusEntity that = (SyncStatusEntity) o;
        return entityType.equals(that.entityType) && entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        return entityType.hashCode() + entityId.hashCode();
    }
}
