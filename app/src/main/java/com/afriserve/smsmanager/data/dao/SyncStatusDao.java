package com.afriserve.smsmanager.data.dao;

import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.afriserve.smsmanager.data.entity.SyncStatusEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Data Access Object for sync status management
 * Supports offline-first architecture with comprehensive sync tracking
 */
@Dao
public interface SyncStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insertSyncStatus(SyncStatusEntity syncStatus);

    @Update
    Completable updateSyncStatus(SyncStatusEntity syncStatus);

    @Delete
    Completable deleteSyncStatus(SyncStatusEntity syncStatus);

    @Query("SELECT * FROM sync_status WHERE entityType = :entityType AND entityId = :entityId")
    Single<SyncStatusEntity> getSyncStatus(String entityType, String entityId);

    @Query("SELECT * FROM sync_status WHERE entityType = :entityType AND entityId = :entityId")
    LiveData<SyncStatusEntity> getSyncStatusLive(String entityType, String entityId);

    @Query("SELECT * FROM sync_status WHERE status = 'PENDING_UPLOAD' ORDER BY lastSyncAt ASC")
    PagingSource<Integer, SyncStatusEntity> getPendingUploadsPaged();

    @Query("SELECT * FROM sync_status WHERE status = 'CONFLICT' ORDER BY lastSyncAt ASC")
    Single<List<SyncStatusEntity>> getConflicts();

    @Query("SELECT * FROM sync_status WHERE status = 'CONFLICT' ORDER BY lastSyncAt ASC")
    LiveData<List<SyncStatusEntity>> getConflictsLive();

    @Query("SELECT * FROM sync_status WHERE status IN ('PENDING_UPLOAD', 'PENDING_DOWNLOAD', 'CONFLICT', 'ERROR') ORDER BY lastSyncAt ASC")
    Single<List<SyncStatusEntity>> getEntitiesNeedingSync();

    @Query("SELECT * FROM sync_status WHERE status IN ('PENDING_UPLOAD', 'PENDING_DOWNLOAD', 'CONFLICT', 'ERROR') ORDER BY lastSyncAt ASC")
    LiveData<List<SyncStatusEntity>> getEntitiesNeedingSyncLive();

    @Query("SELECT COUNT(*) FROM sync_status WHERE status = 'PENDING_UPLOAD'")
    Single<Integer> getPendingUploadCount();

    @Query("SELECT COUNT(*) FROM sync_status WHERE status = 'CONFLICT'")
    Single<Integer> getConflictCount();

    @Query("SELECT COUNT(*) FROM sync_status WHERE status IN ('PENDING_UPLOAD', 'PENDING_DOWNLOAD', 'CONFLICT', 'ERROR')")
    Single<Integer> getTotalNeedingSyncCount();

    @Query("SELECT COUNT(*) FROM sync_status WHERE entityType = :entityType AND status = 'PENDING_UPLOAD'")
    Single<Integer> getPendingUploadCountByType(String entityType);

    @Query("UPDATE sync_status SET status = :status WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateSyncStatus(String entityType, String entityId, SyncStatusEntity.SyncStatus status);

    @Query("UPDATE sync_status SET lastSyncAt = :timestamp WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateLastSyncTime(String entityType, String entityId, long timestamp);

    @Query("UPDATE sync_status SET lastServerModifiedAt = :timestamp WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateLastServerModifiedTime(String entityType, String entityId, long timestamp);

    @Query("UPDATE sync_status SET eTag = :eTag WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateETag(String entityType, String entityId, String eTag);

    @Query("UPDATE sync_status SET syncVersion = syncVersion + 1 WHERE entityType = :entityType AND entityId = :entityId")
    Completable incrementSyncVersion(String entityType, String entityId);

    @Query("UPDATE sync_status SET pendingOperations = pendingOperations + 1 WHERE entityType = :entityType AND entityId = :entityId")
    Completable incrementPendingOperations(String entityType, String entityId);

    @Query("UPDATE sync_status SET pendingOperations = pendingOperations - 1 WHERE entityType = :entityType AND entityId = :entityId AND pendingOperations > 0")
    Completable decrementPendingOperations(String entityType, String entityId);

    @Query("UPDATE sync_status SET lastError = :error WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateLastError(String entityType, String entityId, String error);

    @Query("UPDATE sync_status SET conflictData = :conflictData WHERE entityType = :entityType AND entityId = :entityId")
    Completable updateConflictData(String entityType, String entityId, String conflictData);

    @Query("DELETE FROM sync_status WHERE entityType = :entityType AND entityId = :entityId")
    Completable deleteSyncStatus(String entityType, String entityId);

    @Query("DELETE FROM sync_status WHERE status = 'SYNCED' AND lastSyncAt < :cutoffTime")
    Completable deleteOldSyncedStatus(long cutoffTime);

    @Query("SELECT * FROM sync_status WHERE entityType = :entityType ORDER BY lastSyncAt DESC LIMIT :limit")
    Single<List<SyncStatusEntity>> getRecentSyncStatusByType(String entityType, int limit);

    @Query("SELECT * FROM sync_status WHERE lastError IS NOT NULL ORDER BY lastSyncAt DESC LIMIT :limit")
    Single<List<SyncStatusEntity>> getRecentErrors(int limit);

    /**
     * Get sync statistics
     */
    @Query("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'SYNCED' THEN 1 ELSE 0 END) as synced, " +
            "SUM(CASE WHEN status = 'PENDING_UPLOAD' THEN 1 ELSE 0 END) as pendingUpload, " +
            "SUM(CASE WHEN status = 'PENDING_DOWNLOAD' THEN 1 ELSE 0 END) as pendingDownload, " +
            "SUM(CASE WHEN status = 'CONFLICT' THEN 1 ELSE 0 END) as conflicts, " +
            "SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) as errors " +
            "FROM sync_status WHERE entityType = :entityType")
    Single<SyncStats> getSyncStats(String entityType);

    /**
     * Sync statistics data class
     */
    class SyncStats {
        public int total;
        public int synced;
        public int pendingUpload;
        public int pendingDownload;
        public int conflicts;
        public int errors;
    }
}
