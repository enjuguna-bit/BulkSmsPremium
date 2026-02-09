package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.SyncStatusDao;
import com.afriserve.smsmanager.data.entity.SyncStatusEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.content.Context;

/**
 * Offline-first sync manager
 * Coordinates between local Room database and remote server
 * Implements conflict resolution and incremental sync
 */
@Singleton
public class OfflineFirstSyncManager {
    
    private static final String TAG = "OfflineFirstSyncManager";
    
    private final SyncStatusDao syncStatusDao;
    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Sync state
    private final MutableLiveData<SyncState> _syncState = new MutableLiveData<>(SyncState.IDLE);
    public final LiveData<SyncState> syncState = _syncState;
    
    // Network connectivity
    private final MutableLiveData<Boolean> _isNetworkAvailable = new MutableLiveData<>(false);
    public final LiveData<Boolean> isNetworkAvailable = _isNetworkAvailable;
    
    @Inject
    public OfflineFirstSyncManager(
        SyncStatusDao syncStatusDao,
        SmsRepository smsRepository,
        ConversationRepository conversationRepository,
        @ApplicationContext Context context
    ) {
        this.syncStatusDao = syncStatusDao;
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
        this.context = context;
        
        Log.d(TAG, "OfflineFirstSyncManager initialized");
    }
    
    /**
     * Initialize offline-first sync
     * Load data from Room immediately, then sync with server
     */
    public Completable initialize() {
        return Completable.fromAction(() -> {
            Log.d(TAG, "Initializing offline-first sync");
            _syncState.postValue(SyncState.INITIALIZING);
            
            // Load cached data immediately (handled by repositories)
            // Then start background sync
            performBackgroundSync();
            
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Perform background sync when network is available
     */
    private void performBackgroundSync() {
        disposables.add(
            getEntitiesNeedingSync()
                .flatMapCompletable(entities -> {
                    if (entities.isEmpty()) {
                        Log.d(TAG, "No entities need sync");
                        return Completable.complete();
                    }
                    
                    Log.d(TAG, "Syncing " + entities.size() + " entities");
                    return syncEntities(entities);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d(TAG, "Background sync completed");
                        _syncState.postValue(SyncState.SYNCED);
                    },
                    error -> {
                        Log.e(TAG, "Background sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }
    
    /**
     * Sync multiple entities
     */
    private Completable syncEntities(List<SyncStatusEntity> entities) {
        return Completable.fromAction(() -> {
            for (SyncStatusEntity entity : entities) {
                try {
                    syncSingleEntity(entity);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync entity: " + entity.entityType + ":" + entity.entityId, e);
                    // Mark as error but continue with other entities
                    markSyncError(entity, e.getMessage());
                }
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Sync a single entity based on its status
     */
    private void syncSingleEntity(SyncStatusEntity syncStatus) throws Exception {
        switch (syncStatus.entityType) {
            case "sms":
                syncSmsEntity(syncStatus);
                break;
            case "conversation":
                syncConversationEntity(syncStatus);
                break;
            default:
                Log.w(TAG, "Unknown entity type: " + syncStatus.entityType);
                break;
        }
    }
    
    /**
     * Sync SMS entity
     */
    private void syncSmsEntity(SyncStatusEntity syncStatus) throws Exception {
        switch (syncStatus.status) {
            case PENDING_UPLOAD:
                uploadSmsToServer(syncStatus);
                break;
            case PENDING_DOWNLOAD:
                downloadSmsFromServer(syncStatus);
                break;
            case CONFLICT:
                resolveSmsConflict(syncStatus);
                break;
            default:
                Log.w(TAG, "Unexpected sync status for SMS: " + syncStatus.status);
                break;
        }
    }
    
    /**
     * Sync conversation entity
     */
    private void syncConversationEntity(SyncStatusEntity syncStatus) throws Exception {
        switch (syncStatus.status) {
            case PENDING_UPLOAD:
                uploadConversationToServer(syncStatus);
                break;
            case PENDING_DOWNLOAD:
                downloadConversationFromServer(syncStatus);
                break;
            case CONFLICT:
                resolveConversationConflict(syncStatus);
                break;
            default:
                Log.w(TAG, "Unexpected sync status for conversation: " + syncStatus.status);
                break;
        }
    }
    
    /**
     * Upload SMS to server
     */
    private void uploadSmsToServer(SyncStatusEntity syncStatus) throws Exception {
        // Get SMS from local database
        SmsEntity sms = smsRepository.getSmsById(Long.parseLong(syncStatus.entityId)).blockingGet();
        if (sms == null) {
            Log.w(TAG, "SMS not found for upload: " + syncStatus.entityId);
            return;
        }
        
        // TODO: Implement actual server upload with network optimizations
        // - Use GZIP compression
        // - Include ETag for conditional requests
        // - Handle retry logic
        
        // Simulate server upload
        Thread.sleep(100); // Simulate network delay
        
        // Mark as synced
        markAsSynced(syncStatus);
        Log.d(TAG, "SMS uploaded to server: " + syncStatus.entityId);
    }
    
    /**
     * Download SMS from server
     */
    private void downloadSmsFromServer(SyncStatusEntity syncStatus) throws Exception {
        // TODO: Implement actual server download with network optimizations
        // - Use If-Modified-Since header
        // - Handle pagination for large datasets
        // - Apply incremental updates
        
        // Simulate server download
        Thread.sleep(100); // Simulate network delay
        
        // Mark as synced
        markAsSynced(syncStatus);
        Log.d(TAG, "SMS downloaded from server: " + syncStatus.entityId);
    }
    
    /**
     * Resolve SMS conflict
     */
    private void resolveSmsConflict(SyncStatusEntity syncStatus) throws Exception {
        // TODO: Implement conflict resolution strategy
        // - Last-write-wins
        // - Manual resolution
        // - Three-way merge
        
        // For now, use last-write-wins based on timestamp
        SmsEntity localSms = smsRepository.getSmsById(Long.parseLong(syncStatus.entityId)).blockingGet();
        if (localSms != null && localSms.createdAt > syncStatus.lastServerModifiedAt) {
            // Local version is newer, upload it
            uploadSmsToServer(syncStatus);
        } else {
            // Server version is newer, download it
            downloadSmsFromServer(syncStatus);
        }
        
        Log.d(TAG, "SMS conflict resolved: " + syncStatus.entityId);
    }
    
    /**
     * Upload conversation to server
     */
    private void uploadConversationToServer(SyncStatusEntity syncStatus) throws Exception {
        // Similar to SMS upload but for conversations
        Thread.sleep(50); // Simulate network delay
        markAsSynced(syncStatus);
        Log.d(TAG, "Conversation uploaded to server: " + syncStatus.entityId);
    }
    
    /**
     * Download conversation from server
     */
    private void downloadConversationFromServer(SyncStatusEntity syncStatus) throws Exception {
        // Similar to SMS download but for conversations
        Thread.sleep(50); // Simulate network delay
        markAsSynced(syncStatus);
        Log.d(TAG, "Conversation downloaded from server: " + syncStatus.entityId);
    }
    
    /**
     * Resolve conversation conflict
     */
    private void resolveConversationConflict(SyncStatusEntity syncStatus) throws Exception {
        // Similar to SMS conflict resolution but for conversations
        Thread.sleep(50); // Simulate network delay
        markAsSynced(syncStatus);
        Log.d(TAG, "Conversation conflict resolved: " + syncStatus.entityId);
    }
    
    /**
     * Mark entity as synced
     */
    private void markAsSynced(SyncStatusEntity syncStatus) throws Exception {
        syncStatus.status = SyncStatusEntity.SyncStatus.SYNCED;
        syncStatus.lastSyncAt = System.currentTimeMillis();
        syncStatus.lastError = null;
        syncStatus.pendingOperations = 0;
        
        syncStatusDao.updateSyncStatus(syncStatus).blockingAwait();
    }
    
    /**
     * Mark entity as having sync error
     */
    private void markSyncError(SyncStatusEntity syncStatus, String error) throws Exception {
        syncStatus.status = SyncStatusEntity.SyncStatus.ERROR;
        syncStatus.lastError = error;
        syncStatus.lastSyncAt = System.currentTimeMillis();
        
        syncStatusDao.updateSyncStatus(syncStatus).blockingAwait();
    }
    
    /**
     * Get entities that need sync
     */
    private Single<List<SyncStatusEntity>> getEntitiesNeedingSync() {
        return syncStatusDao.getEntitiesNeedingSync();
    }
    
    /**
     * Create or update sync status for an entity
     */
    public Completable updateSyncStatus(String entityType, String entityId, SyncStatusEntity.SyncStatus status) {
        return syncStatusDao.getSyncStatus(entityType, entityId)
            .flatMap(existingStatus -> {
                if (existingStatus == null) {
                    // Create new sync status
                    SyncStatusEntity newStatus = new SyncStatusEntity(entityType, entityId);
                    newStatus.status = status;
                    return syncStatusDao.insertSyncStatus(newStatus)
                        .map(id -> newStatus);
                } else {
                    // Update existing sync status
                    existingStatus.status = status;
                    existingStatus.lastSyncAt = System.currentTimeMillis();
                    return syncStatusDao.updateSyncStatus(existingStatus)
                        .andThen(Single.just(existingStatus));
                }
            })
            .ignoreElement()
            .subscribeOn(Schedulers.io());
    }

    /**
     * Get sync status for a specific entity
     */
    public Single<SyncStatusEntity> getSyncStatus(String entityType, String entityId) {
        return syncStatusDao.getSyncStatus(entityType, entityId);
    }
    
    /**
     * Force sync all entities
     */
    public Completable forceSyncAll() {
        _syncState.postValue(SyncState.SYNCING);
        
        return Completable.fromAction(() -> {
            // Mark all entities as needing download
            // This would typically be triggered by user request
            Log.d(TAG, "Force syncing all entities");
            
        }).subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .doOnComplete(() -> _syncState.postValue(SyncState.SYNCED))
          .doOnError(error -> _syncState.postValue(SyncState.ERROR));
    }
    
    /**
     * Get sync statistics
     */
    public LiveData<SyncStatistics> getSyncStatistics() {
        MutableLiveData<SyncStatistics> stats = new MutableLiveData<>();
        
        disposables.add(
            Single.zip(
                syncStatusDao.getTotalNeedingSyncCount(),
                syncStatusDao.getConflictCount(),
                syncStatusDao.getPendingUploadCount(),
                (total, conflicts, uploads) -> {
                    SyncStatistics syncStats = new SyncStatistics();
                    syncStats.totalNeedingSync = total;
                    syncStats.conflicts = conflicts;
                    syncStats.pendingUploads = uploads;
                    syncStats.isUpToDate = total == 0 && conflicts == 0;
                    return syncStats;
                }
            )
            .subscribe(
                stats::postValue,
                error -> Log.e(TAG, "Error getting sync statistics", error)
            )
        );
        
        return stats;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        disposables.clear();
        executor.shutdown();
        Log.d(TAG, "OfflineFirstSyncManager cleaned up");
    }
    
    /**
     * Sync state enum
     */
    public enum SyncState {
        IDLE,
        INITIALIZING,
        SYNCING,
        SYNCED,
        ERROR,
        OFFLINE
    }
    
    /**
     * Sync statistics data class
     */
    public static class SyncStatistics {
        public int totalNeedingSync;
        public int conflicts;
        public int pendingUploads;
        public boolean isUpToDate;
    }
}
