package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

import android.content.Context;

/**
 * Unified sync coordinator
 * Manages all sync operations and prevents conflicts between sync managers
 */
@Singleton
public class SyncCoordinator {
    
    private static final String TAG = "SyncCoordinator";
    
    private final SmsSyncManager smsSyncManager;
    private final OfflineFirstSyncManager offlineFirstSyncManager;
    private final ConversationSyncManager conversationSyncManager;
    private final Context context;
    
    private final CompositeDisposable disposables = new CompositeDisposable();

    private Observer<SmsSyncManager.SyncState> smsSyncStateObserver;
    private Observer<OfflineFirstSyncManager.SyncState> offlineSyncStateObserver;
    private Observer<ConversationSyncManager.SyncState> conversationSyncStateObserver;
    
    // Sync state
    private final MutableLiveData<SyncState> _syncState = new MutableLiveData<>(SyncState.IDLE);
    public final LiveData<SyncState> syncState = _syncState;
    
    // Sync coordination
    private boolean isSyncInProgress = false;
    private long lastSyncTime = 0;
    private static final long MIN_SYNC_INTERVAL = 5000; // 5 seconds minimum between syncs
    
    @Inject
    public SyncCoordinator(
            SmsSyncManager smsSyncManager,
            OfflineFirstSyncManager offlineFirstSyncManager,
            ConversationSyncManager conversationSyncManager,
            @ApplicationContext Context context) {
        this.smsSyncManager = smsSyncManager;
        this.offlineFirstSyncManager = offlineFirstSyncManager;
        this.conversationSyncManager = conversationSyncManager;
        this.context = context;
        
        Log.d(TAG, "SyncCoordinator initialized");
        
        // Observe individual sync states
        observeSyncStates();
    }
    
    /**
     * Start coordinated sync
     */
    public void startSync() {
        if (isSyncInProgress) {
            Log.d(TAG, "Sync already in progress, skipping");
            return;
        }
        
        if (System.currentTimeMillis() - lastSyncTime < MIN_SYNC_INTERVAL) {
            Log.d(TAG, "Sync too recent, skipping");
            return;
        }
        
        isSyncInProgress = true;
        lastSyncTime = System.currentTimeMillis();
        _syncState.postValue(SyncState.SYNCING);
        
        Log.d(TAG, "Starting coordinated sync");
        
        // Coordinate sync operations
        disposables.add(
            offlineFirstSyncManager.initialize()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        conversationSyncManager.startSync();
                        isSyncInProgress = false;
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Coordinated sync completed");
                    },
                    error -> {
                        isSyncInProgress = false;
                        Log.e(TAG, "Coordinated sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }
    
    /**
     * Stop all sync operations
     */
    public void stopSync() {
        isSyncInProgress = false;
        _syncState.postValue(SyncState.STOPPED);
        
        // Stop individual sync managers
        smsSyncManager.stopRealTimeSync();
        conversationSyncManager.stopSync();
        
        Log.d(TAG, "All sync operations stopped");
    }
    
    /**
     * Force manual sync
     */
    public void forceSync() {
        Log.d(TAG, "Force sync requested");
        isSyncInProgress = false; // Allow immediate sync
        startSync();
    }
    
    /**
     * Enable/disable auto-sync for all managers
     */
    public void setAutoSyncEnabled(boolean enabled) {
        smsSyncManager.setAutoSyncEnabled(enabled);
        conversationSyncManager.setAutoSyncEnabled(enabled);
        
        Log.d(TAG, "Auto-sync " + (enabled ? "enabled" : "disabled") + " for all managers");
    }
    
    /**
     * Get sync statistics from all managers
     */
    public LiveData<SyncStatistics> getSyncStatistics() {
        MediatorLiveData<SyncStatistics> combinedStats = new MediatorLiveData<>();
        final OfflineFirstSyncManager.SyncStatistics[] offlineStatsRef = new OfflineFirstSyncManager.SyncStatistics[1];
        final ConversationSyncManager.SyncStatistics[] conversationStatsRef = new ConversationSyncManager.SyncStatistics[1];

        LiveData<OfflineFirstSyncManager.SyncStatistics> offlineStatsLiveData = offlineFirstSyncManager.getSyncStatistics();
        LiveData<ConversationSyncManager.SyncStatistics> conversationStatsLiveData = conversationSyncManager.syncStats;

        Runnable publishCombinedStats = () -> {
            OfflineFirstSyncManager.SyncStatistics offlineStats = offlineStatsRef[0];
            ConversationSyncManager.SyncStatistics conversationStats = conversationStatsRef[0];

            if (offlineStats == null && conversationStats == null) {
                return;
            }

            int offlineTotal = offlineStats != null ? offlineStats.totalNeedingSync : 0;
            int offlinePending = offlineStats != null ? offlineStats.pendingUploads : 0;
            int offlineConflicts = offlineStats != null ? offlineStats.conflicts : 0;
            boolean offlineUpToDate = offlineStats == null || offlineStats.isUpToDate;

            int conversationsSynced = conversationStats != null ? conversationStats.conversationsSynced : 0;
            int conversationsUpdated = conversationStats != null ? conversationStats.conversationsUpdated : 0;
            int conversationsDeleted = conversationStats != null ? conversationStats.conversationsDeleted : 0;

            SyncStatistics stats = new SyncStatistics();
            stats.totalEntities = offlineTotal + conversationsSynced;
            stats.syncedEntities = conversationsSynced;
            stats.pendingOperations = offlinePending + conversationsUpdated;
            stats.conflicts = offlineConflicts + conversationsDeleted;
            stats.isUpToDate = offlineUpToDate && conversationsSynced == 0 && conversationsUpdated == 0;
            combinedStats.postValue(stats);
        };

        combinedStats.addSource(offlineStatsLiveData, offlineStats -> {
            offlineStatsRef[0] = offlineStats;
            publishCombinedStats.run();
        });

        combinedStats.addSource(conversationStatsLiveData, conversationStats -> {
            conversationStatsRef[0] = conversationStats;
            publishCombinedStats.run();
        });

        return combinedStats;
    }
    
    /**
     * Observe individual sync states and coordinate responses
     */
    private void observeSyncStates() {
        smsSyncStateObserver = state -> {
            if (state == null) {
                return;
            }
            switch (state) {
                case SYNCING:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCING);
                    }
                    break;
                case ACTIVE:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCED);
                    }
                    break;
                case ERROR:
                    _syncState.postValue(SyncState.ERROR);
                    break;
                default:
                    break;
            }
        };
        smsSyncManager.syncState.observeForever(smsSyncStateObserver);

        offlineSyncStateObserver = state -> {
            if (state == null) {
                return;
            }
            switch (state) {
                case INITIALIZING:
                case SYNCING:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCING);
                    }
                    break;
                case SYNCED:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCED);
                    }
                    break;
                case ERROR:
                    _syncState.postValue(SyncState.ERROR);
                    break;
                default:
                    break;
            }
        };
        offlineFirstSyncManager.syncState.observeForever(offlineSyncStateObserver);

        conversationSyncStateObserver = state -> {
            if (state == null) {
                return;
            }
            switch (state) {
                case SYNCING:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCING);
                    }
                    break;
                case SYNCED:
                    if (!isSyncInProgress) {
                        _syncState.postValue(SyncState.SYNCED);
                    }
                    break;
                case ERROR:
                    _syncState.postValue(SyncState.ERROR);
                    break;
                default:
                    break;
            }
        };
        conversationSyncManager.syncState.observeForever(conversationSyncStateObserver);
    }
    
    /**
     * Handle network connectivity changes
     */
    public void onNetworkConnectivityChanged(boolean isNetworkAvailable) {
        if (isNetworkAvailable) {
            Log.d(TAG, "Network available, resuming sync");
            startSync();
        } else {
            Log.d(TAG, "Network unavailable, pausing sync");
            stopSync();
        }
    }
    
    /**
     * Handle app lifecycle events
     */
    public void onAppBackgrounded() {
        Log.d(TAG, "App backgrounded, reducing sync frequency");
        setAutoSyncEnabled(false);
    }
    
    public void onAppForegrounded() {
        Log.d(TAG, "App foregrounded, resuming normal sync");
        setAutoSyncEnabled(true);
        startSync();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (smsSyncStateObserver != null) {
            smsSyncManager.syncState.removeObserver(smsSyncStateObserver);
            smsSyncStateObserver = null;
        }
        if (offlineSyncStateObserver != null) {
            offlineFirstSyncManager.syncState.removeObserver(offlineSyncStateObserver);
            offlineSyncStateObserver = null;
        }
        if (conversationSyncStateObserver != null) {
            conversationSyncManager.syncState.removeObserver(conversationSyncStateObserver);
            conversationSyncStateObserver = null;
        }

        disposables.clear();
        stopSync();
        Log.d(TAG, "SyncCoordinator cleaned up");
    }
    
    /**
     * Check if sync is currently in progress
     */
    public boolean isSyncInProgress() {
        return isSyncInProgress;
    }
    
    /**
     * Get last sync time
     */
    public long getLastSyncTime() {
        return lastSyncTime;
    }
    
    /**
     * Sync state enum
     */
    public enum SyncState {
        IDLE,
        SYNCING,
        SYNCED,
        ERROR,
        STOPPED
    }
    
    /**
     * Combined sync statistics
     */
    public static class SyncStatistics {
        public int totalEntities;
        public int syncedEntities;
        public int pendingOperations;
        public int conflicts;
        public boolean isUpToDate;
    }
}
