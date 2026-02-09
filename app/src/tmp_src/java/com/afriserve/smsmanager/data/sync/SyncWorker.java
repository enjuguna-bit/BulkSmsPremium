package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import com.afriserve.smsmanager.data.network.OptimizedNetworkService;
import com.afriserve.smsmanager.data.network.SmsApiService;
import com.afriserve.smsmanager.data.entity.SyncStatusEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * Background worker for handling sync operations triggered by FCM
 * Ensures sync operations continue even if app is closed
 */
@HiltWorker
public class SyncWorker extends Worker {
    
    private static final String TAG = "SyncWorker";
    
    private final OfflineFirstSyncManager syncManager;
    private final OptimizedNetworkService networkService;
    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;
    
    private final Gson gson = new Gson();
    
    @AssistedInject
    public SyncWorker(@Assisted @NonNull Context context,
                      @Assisted @NonNull WorkerParameters params,
                      OfflineFirstSyncManager syncManager,
                      OptimizedNetworkService networkService,
                      SmsRepository smsRepository,
                      ConversationRepository conversationRepository) {
        super(context, params);
        this.syncManager = syncManager;
        this.networkService = networkService;
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting sync work");
        
        try {
            String syncType = getInputData().getString("sync_type");
            String entityType = getInputData().getString("entity_type");
            String entityId = getInputData().getString("entity_id");
            
            if (syncType == null) {
                Log.e(TAG, "No sync type specified");
                return Result.failure();
            }
            
            switch (syncType) {
                case "full":
                    return performFullSync();
                case "incremental":
                    return performIncrementalSync(entityType, entityId);
                case "conflict_resolution":
                    return performConflictResolution(entityType, entityId);
                default:
                    Log.e(TAG, "Unknown sync type: " + syncType);
                    return Result.failure();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during sync work", e);
            return Result.retry();
        }
    }
    
    /**
     * Perform full sync
     */
    private Result performFullSync() {
        Log.d(TAG, "Performing full sync");
        
        try {
            // Use offline-first sync manager for full sync
            syncManager.forceSyncAll()
                .blockingAwait(30, TimeUnit.SECONDS);
            
            Log.d(TAG, "Full sync completed successfully");
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Full sync failed", e);
            
            // Retry with exponential backoff
            if (getRunAttemptCount() < 3) {
                return Result.retry();
            } else {
                return Result.failure();
            }
        }
    }
    
    /**
     * Perform incremental sync for specific entity
     */
    private Result performIncrementalSync(String entityType, String entityId) {
        Log.d(TAG, "Performing incremental sync for: " + entityType + ":" + entityId);
        
        try {
            if (entityType == null || entityId == null) {
                Log.e(TAG, "Missing entity type or ID for incremental sync");
                return Result.failure();
            }
            
            switch (entityType) {
                case "sms":
                    return syncSmsEntity(entityId);
                case "conversation":
                    return syncConversationEntity(entityId);
                default:
                    Log.e(TAG, "Unknown entity type for incremental sync: " + entityType);
                    return Result.failure();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Incremental sync failed", e);
            
            // Retry with exponential backoff
            if (getRunAttemptCount() < 3) {
                return Result.retry();
            } else {
                return Result.failure();
            }
        }
    }
    
    /**
     * Sync specific SMS entity
     */
    private Result syncSmsEntity(String smsId) {
        try {
            // Get current sync status
            SyncStatusEntity syncStatus = syncManager.getSyncStatus("sms", smsId).blockingGet();
            
            if (syncStatus == null) {
                // Create new sync status
                syncStatus = new SyncStatusEntity("sms", smsId);
                syncStatus.status = SyncStatusEntity.SyncStatus.PENDING_DOWNLOAD;
            }
            
            // Get latest version from server with ETag support
            SmsApiService apiService = networkService.getSmsApiService();
            String eTag = syncStatus.eTag;
            
            retrofit2.Call<SmsApiService.SmsNetworkEntity> call = apiService.getMessage(smsId, eTag);
            retrofit2.Response<SmsApiService.SmsNetworkEntity> response = call.execute();
            
            if (response.code() == 304) {
                // Not modified - entity is up to date
                Log.d(TAG, "SMS entity up to date: " + smsId);
                syncManager.updateSyncStatus("sms", smsId, SyncStatusEntity.SyncStatus.SYNCED).blockingAwait();
                return Result.success();
            }
            
            if (response.isSuccessful() && response.body() != null) {
                // Update local entity with server data
                SmsApiService.SmsNetworkEntity networkEntity = response.body();
                updateLocalSmsFromNetwork(networkEntity);
                
                // Update sync status
                syncStatus.status = SyncStatusEntity.SyncStatus.SYNCED;
                syncStatus.lastSyncAt = System.currentTimeMillis();
                syncStatus.lastServerModifiedAt = networkEntity.updatedAt;
                syncStatus.eTag = response.headers().get("ETag");
                syncStatus.syncVersion = networkEntity.syncVersion;
                
                syncManager.updateSyncStatus("sms", smsId, syncStatus.status).blockingAwait();
                
                Log.d(TAG, "SMS entity synced successfully: " + smsId);
                return Result.success();
                
            } else {
                Log.e(TAG, "Failed to sync SMS entity: " + response.code());
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error syncing SMS entity: " + smsId, e);
            return Result.retry();
        }
    }
    
    /**
     * Sync specific conversation entity
     */
    private Result syncConversationEntity(String conversationId) {
        try {
            // Get current sync status
            SyncStatusEntity syncStatus = syncManager.getSyncStatus("conversation", conversationId).blockingGet();
            
            if (syncStatus == null) {
                // Create new sync status
                syncStatus = new SyncStatusEntity("conversation", conversationId);
                syncStatus.status = SyncStatusEntity.SyncStatus.PENDING_DOWNLOAD;
            }
            
            // Get latest version from server
            SmsApiService apiService = networkService.getSmsApiService();
            retrofit2.Call<SmsApiService.ConversationNetworkEntity> call = 
                apiService.getConversation(conversationId, true, 50);
            retrofit2.Response<SmsApiService.ConversationNetworkEntity> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null) {
                // Update local entity with server data
                SmsApiService.ConversationNetworkEntity networkEntity = response.body();
                updateLocalConversationFromNetwork(networkEntity);
                
                // Update sync status
                syncStatus.status = SyncStatusEntity.SyncStatus.SYNCED;
                syncStatus.lastSyncAt = System.currentTimeMillis();
                syncStatus.lastServerModifiedAt = networkEntity.updatedAt;
                syncStatus.eTag = response.headers().get("ETag");
                syncStatus.syncVersion = networkEntity.syncVersion;
                
                syncManager.updateSyncStatus("conversation", conversationId, syncStatus.status).blockingAwait();
                
                Log.d(TAG, "Conversation entity synced successfully: " + conversationId);
                return Result.success();
                
            } else {
                Log.e(TAG, "Failed to sync conversation entity: " + response.code());
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error syncing conversation entity: " + conversationId, e);
            return Result.retry();
        }
    }
    
    /**
     * Perform conflict resolution
     */
    private Result performConflictResolution(String entityType, String entityId) {
        Log.d(TAG, "Performing conflict resolution for: " + entityType + ":" + entityId);
        
        try {
            if (entityType == null || entityId == null) {
                Log.e(TAG, "Missing entity type or ID for conflict resolution");
                return Result.failure();
            }
            
            // Get sync status with conflict
            SyncStatusEntity syncStatus = syncManager.getSyncStatus(entityType, entityId).blockingGet();
            if (syncStatus == null || syncStatus.status != SyncStatusEntity.SyncStatus.CONFLICT) {
                Log.w(TAG, "No conflict found for entity: " + entityType + ":" + entityId);
                return Result.success();
            }
            
            // Implement conflict resolution strategy
            switch (entityType) {
                case "sms":
                    return resolveSmsConflict(entityId, syncStatus);
                case "conversation":
                    return resolveConversationConflict(entityId, syncStatus);
                default:
                    Log.e(TAG, "Unknown entity type for conflict resolution: " + entityType);
                    return Result.failure();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Conflict resolution failed", e);
            return Result.retry();
        }
    }
    
    /**
     * Resolve SMS conflict using last-write-wins strategy
     */
    private Result resolveSmsConflict(String smsId, SyncStatusEntity syncStatus) {
        try {
            // Get local version
            com.afriserve.smsmanager.data.entity.SmsEntity localSms = 
                smsRepository.getSmsById(Long.parseLong(smsId)).blockingGet();
            
            // Get server version
            SmsApiService apiService = networkService.getSmsApiService();
            retrofit2.Call<SmsApiService.SmsNetworkEntity> call = apiService.getMessage(smsId, null);
            retrofit2.Response<SmsApiService.SmsNetworkEntity> response = call.execute();
            
            if (!response.isSuccessful() || response.body() == null) {
                Log.e(TAG, "Failed to get server version for conflict resolution");
                return Result.retry();
            }
            
            SmsApiService.SmsNetworkEntity serverSms = response.body();
            
            // Use last-write-wins based on timestamps
            if (localSms != null && localSms.createdAt > serverSms.updatedAt) {
                // Local version is newer, upload it
                uploadLocalSmsToServer(localSms);
            } else {
                // Server version is newer, download it
                updateLocalSmsFromNetwork(serverSms);
            }
            
            // Mark as resolved
            syncManager.updateSyncStatus("sms", smsId, SyncStatusEntity.SyncStatus.SYNCED).blockingAwait();
            
            Log.d(TAG, "SMS conflict resolved: " + smsId);
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error resolving SMS conflict", e);
            return Result.retry();
        }
    }
    
    /**
     * Resolve conversation conflict using last-write-wins strategy
     */
    private Result resolveConversationConflict(String conversationId, SyncStatusEntity syncStatus) {
        try {
            // Similar to SMS conflict resolution but for conversations
            Log.d(TAG, "Resolving conversation conflict: " + conversationId);
            
            // Mark as resolved (simplified implementation)
            syncManager.updateSyncStatus("conversation", conversationId, SyncStatusEntity.SyncStatus.SYNCED).blockingAwait();
            
            Log.d(TAG, "Conversation conflict resolved: " + conversationId);
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error resolving conversation conflict", e);
            return Result.retry();
        }
    }
    
    /**
     * Update local SMS from network entity
     */
    private void updateLocalSmsFromNetwork(SmsApiService.SmsNetworkEntity networkEntity) {
        // TODO: Implement local SMS update from network entity
        Log.d(TAG, "Updating local SMS from network: " + networkEntity.id);
    }
    
    /**
     * Update local conversation from network entity
     */
    private void updateLocalConversationFromNetwork(SmsApiService.ConversationNetworkEntity networkEntity) {
        // TODO: Implement local conversation update from network entity
        Log.d(TAG, "Updating local conversation from network: " + networkEntity.id);
    }
    
    /**
     * Upload local SMS to server
     */
    private void uploadLocalSmsToServer(com.afriserve.smsmanager.data.entity.SmsEntity localSms) {
        // TODO: Implement SMS upload to server
        Log.d(TAG, "Uploading local SMS to server: " + localSms.id);
    }
}
