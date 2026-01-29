package com.bulksms.smsmanager.data.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bulksms.smsmanager.data.repository.SmsRepository;
import com.bulksms.smsmanager.data.repository.ConversationRepository;
import com.bulksms.smsmanager.data.repository.SmsSearchRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.content.Context;

/**
 * Real-time SMS sync manager
 * Coordinates real-time sync using ContentObserver
 */
@Singleton
public class SmsSyncManager implements SmsContentObserver.OnSmsChangeListener {
    
    private static final String TAG = "SmsSyncManager";
    
    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;
    private final SmsSearchRepository searchRepository;
    private final SmsContentObserver contentObserver;
    private final Context context;
    
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Sync state
    private final MutableLiveData<SyncState> _syncState = new MutableLiveData<>(SyncState.IDLE);
    public final LiveData<SyncState> syncState = _syncState;
    
    // Sync statistics
    private final MutableLiveData<SyncStats> _syncStats = new MutableLiveData<>();
    public final LiveData<SyncStats> syncStats = _syncStats;
    
    // Auto-sync enabled
    private boolean autoSyncEnabled = true;
    
    @Inject
    public SmsSyncManager(
        SmsRepository smsRepository,
        ConversationRepository conversationRepository,
        SmsSearchRepository searchRepository,
        SmsContentObserver contentObserver,
        @ApplicationContext Context context
    ) {
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
        this.searchRepository = searchRepository;
        this.contentObserver = contentObserver;
        this.context = context;
        
        // Set up content observer
        contentObserver.setOnSmsChangeListener(this);
        
        // Subscribe to content observer events
        disposables.add(
            contentObserver.getChangeEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    this::handleSmsChangeEvent,
                    error -> Log.e(TAG, "Error in SMS change events", error)
                )
        );
        
        Log.d(TAG, "SmsSyncManager initialized");
    }
    
    /**
     * Start real-time sync
     */
    public void startRealTimeSync() {
        if (!autoSyncEnabled) {
            Log.d(TAG, "Auto-sync is disabled");
            return;
        }
        
        try {
            contentObserver.register();
            _syncState.postValue(SyncState.ACTIVE);
            Log.d(TAG, "Real-time sync started");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start real-time sync", e);
            _syncState.postValue(SyncState.ERROR);
        }
    }
    
    /**
     * Stop real-time sync
     */
    public void stopRealTimeSync() {
        try {
            contentObserver.unregister();
            _syncState.postValue(SyncState.STOPPED);
            Log.d(TAG, "Real-time sync stopped");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop real-time sync", e);
        }
    }
    
    /**
     * Enable/disable auto-sync
     */
    public void setAutoSyncEnabled(boolean enabled) {
        this.autoSyncEnabled = enabled;
        
        if (enabled && _syncState.getValue() == SyncState.STOPPED) {
            startRealTimeSync();
        } else if (!enabled && _syncState.getValue() == SyncState.ACTIVE) {
            stopRealTimeSync();
        }
        
        Log.d(TAG, "Auto-sync " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Force manual sync
     */
    public void forceSync() {
        Log.d(TAG, "Force sync requested");
        performFullSync();
    }
    
    /**
     * Perform initial sync on app startup
     */
    public void performInitialSync() {
        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            performFullSyncInternal()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    stats -> {
                        _syncStats.postValue(stats);
                        _syncState.postValue(SyncState.ACTIVE);
                        Log.d(TAG, "Initial sync completed: " + stats);
                    },
                    error -> {
                        Log.e(TAG, "Initial sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }
    
    /**
     * Handle SMS content change events
     */
    @Override
    public void onSmsChanged(SmsContentObserver.SmsChangeEvent event) {
        Log.d(TAG, "SMS changed: " + event.changeType + " at " + event.timestamp);
        
        // Update sync state
        _syncState.postValue(SyncState.SYNCING);
    }
    
    /**
     * Handle sync request from ContentObserver
     */
    @Override
    public void onSyncRequested() {
        if (!autoSyncEnabled) {
            Log.d(TAG, "Sync requested but auto-sync is disabled");
            return;
        }
        
        Log.d(TAG, "Sync requested");
        
        disposables.add(
            performIncrementalSync()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    stats -> {
                        _syncStats.postValue(stats);
                        _syncState.postValue(SyncState.ACTIVE);
                        Log.d(TAG, "Incremental sync completed: " + stats);
                    },
                    error -> {
                        Log.e(TAG, "Incremental sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }
    
    /**
     * Perform full sync (SMS + conversations + search index)
     */
    private io.reactivex.rxjava3.core.Single<SyncStats> performFullSyncInternal() {
        return io.reactivex.rxjava3.core.Single.zip(
            // Sync SMS messages
            smsRepository.syncNewMessages()
                .andThen(io.reactivex.rxjava3.core.Single.just(new SyncStats.SmsSyncResult(0, 0)))
                .onErrorReturn(error -> new SyncStats.SmsSyncResult(0, 1)),
            
            // Sync conversations
            conversationRepository.syncConversationsFromMessages()
                .andThen(io.reactivex.rxjava3.core.Single.just(new SyncStats.ConversationSyncResult(0)))
                .onErrorReturn(error -> new SyncStats.ConversationSyncResult(1)),
            
            // Build search index
            searchRepository.buildSearchIndex()
                .andThen(io.reactivex.rxjava3.core.Single.just(new SyncStats.SearchSyncResult(0)))
                .onErrorReturn(error -> new SyncStats.SearchSyncResult(1)),
            
            (smsResult, conversationResult, searchResult) -> 
                new SyncStats(smsResult, conversationResult, searchResult)
        ).subscribeOn(Schedulers.io());
    }
    
    /**
     * Perform incremental sync (only SMS messages)
     */
    private io.reactivex.rxjava3.core.Single<SyncStats> performIncrementalSync() {
        return smsRepository.syncNewMessages()
            .andThen(
                io.reactivex.rxjava3.core.Single.zip(
                    // Update conversations
                    conversationRepository.syncConversationsFromMessages()
                        .andThen(io.reactivex.rxjava3.core.Single.just(new SyncStats.ConversationSyncResult(0)))
                        .onErrorReturn(error -> new SyncStats.ConversationSyncResult(1)),
                    
                    // Update search index incrementally
                    io.reactivex.rxjava3.core.Single.just(new SyncStats.SearchSyncResult(0))
                        .onErrorReturn(error -> new SyncStats.SearchSyncResult(1)),
                    
                    (conversationResult, searchResult) -> 
                        new SyncStats(new SyncStats.SmsSyncResult(0, 0), conversationResult, searchResult)
                )
            )
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Perform full sync (public method)
     */
    public void performFullSync() {
        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            performFullSyncInternal()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    stats -> {
                        _syncStats.postValue(stats);
                        _syncState.postValue(SyncState.ACTIVE);
                        Log.d(TAG, "Full sync completed: " + stats);
                    },
                    error -> {
                        Log.e(TAG, "Full sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }
    
    /**
     * Get current sync state
     */
    public SyncState getCurrentSyncState() {
        return _syncState.getValue();
    }
    
    /**
     * Check if auto-sync is enabled
     */
    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }
    
    /**
     * Check if real-time sync is active
     */
    public boolean isRealTimeSyncActive() {
        return contentObserver.isRegistered() && _syncState.getValue() == SyncState.ACTIVE;
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        stopRealTimeSync();
        disposables.clear();
        Log.d(TAG, "SmsSyncManager cleaned up");
    }
    
    /**
     * Sync state enum
     */
    public enum SyncState {
        IDLE,
        ACTIVE,
        SYNCING,
        STOPPED,
        ERROR
    }
    
    /**
     * Sync statistics data class
     */
    public static class SyncStats {
        public final SmsSyncResult smsResult;
        public final ConversationSyncResult conversationResult;
        public final SearchSyncResult searchResult;
        public final long timestamp;
        
        public SyncStats(SmsSyncResult smsResult, ConversationSyncResult conversationResult, SearchSyncResult searchResult) {
            this.smsResult = smsResult;
            this.conversationResult = conversationResult;
            this.searchResult = searchResult;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean hasErrors() {
            return smsResult.errorCount > 0 || 
                   conversationResult.errorCount > 0 || 
                   searchResult.errorCount > 0;
        }
        
        @Override
        public String toString() {
            return "SyncStats{" +
                    "sms=" + smsResult +
                    ", conversation=" + conversationResult +
                    ", search=" + searchResult +
                    ", timestamp=" + timestamp +
                    '}';
        }
        
        public static class SmsSyncResult {
            public final int syncedCount;
            public final int errorCount;
            
            public SmsSyncResult(int syncedCount, int errorCount) {
                this.syncedCount = syncedCount;
                this.errorCount = errorCount;
            }
            
            @Override
            public String toString() {
                return "SmsSyncResult{synced=" + syncedCount + ", errors=" + errorCount + "}";
            }
        }
        
        public static class ConversationSyncResult {
            public final int syncedCount;
            public final int errorCount;
            
            public ConversationSyncResult(int errorCount) {
                this.syncedCount = 0; // Not tracked in current implementation
                this.errorCount = errorCount;
            }
            
            @Override
            public String toString() {
                return "ConversationSyncResult{errors=" + errorCount + "}";
            }
        }
        
        public static class SearchSyncResult {
            public final int indexedCount;
            public final int errorCount;
            
            public SearchSyncResult(int errorCount) {
                this.indexedCount = 0; // Not tracked in current implementation
                this.errorCount = errorCount;
            }
            
            @Override
            public String toString() {
                return "SearchSyncResult{errors=" + errorCount + "}";
            }
        }
    }
    
    /**
     * Handle SMS change events from content observer
     */
    private void handleSmsChangeEvent(Object event) {
        try {
            Log.d(TAG, "SMS change event received: " + event);
            
            // Trigger a sync when SMS changes are detected
            if (autoSyncEnabled) {
                performFullSync();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS change event", e);
        }
    }
}
