package com.afriserve.smsmanager.ui.inbox;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingLiveData;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;
import com.afriserve.smsmanager.data.sync.SmsSyncManager;
import com.afriserve.smsmanager.data.sync.OfflineFirstSyncManager;
import com.afriserve.smsmanager.data.repository.SyncResult;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltViewModel
public class SimpleInboxViewModel extends ViewModel {
    
    private final SmsRepository repository;
    private final ConversationRepository conversationRepository;
    private final SmsSearchRepository searchRepository;
    private final SmsSyncManager syncManager;
    private final OfflineFirstSyncManager offlineFirstSyncManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // UI State
    private final MutableLiveData<InboxUiState> _uiState = new MutableLiveData<>(InboxUiState.LOADING);
    public final LiveData<InboxUiState> uiState = _uiState;
    
    // Error State
    private final MutableLiveData<String> _errorState = new MutableLiveData<>();
    public final LiveData<String> errorState = _errorState;
    
    // Sync Result
    private final MutableLiveData<SyncResult> _syncResult = new MutableLiveData<>();
    public final LiveData<SyncResult> syncResult = _syncResult;
    
    // Search State
    private final MutableLiveData<SmsSearchRepository.SearchState> _searchState = 
        new MutableLiveData<>(SmsSearchRepository.SearchState.IDLE);
    public final LiveData<SmsSearchRepository.SearchState> searchState = _searchState;
    
    // Paged Data - using Kotlin helper for Paging 3 interop
    private final MutableLiveData<PagingData<ConversationEntity>> _messages = new MutableLiveData<>();
    public final LiveData<PagingData<ConversationEntity>> messages = _messages;
    
    // Current filter and search
    private FilterType currentFilter = FilterType.ALL;
    private String currentSearchQuery = "";
    
    // Current messages LiveData observer (to prevent memory leaks)
    private LiveData<PagingData<ConversationEntity>> currentMessagesLiveData;
    private Observer<PagingData<ConversationEntity>> messagesObserver;
    
    // Observer references for cleanup
    private Observer<String> errorStateObserver;
    private Observer<SyncResult> syncResultObserver;
    private Observer<SmsSearchRepository.SearchState> searchStateObserver;
    private Observer<SmsSyncManager.SyncState> syncStateObserver;
    private Observer<OfflineFirstSyncManager.SyncState> offlineSyncStateObserver;
    
    // Statistics
    public final LiveData<Integer> unreadCount;
    public final LiveData<Integer> totalCount;
    
    @Inject
    public SimpleInboxViewModel(
        SmsRepository repository,
        ConversationRepository conversationRepository,
        SmsSearchRepository searchRepository,
        SmsSyncManager syncManager,
        OfflineFirstSyncManager offlineFirstSyncManager
    ) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.searchRepository = searchRepository;
        this.syncManager = syncManager;
        this.offlineFirstSyncManager = offlineFirstSyncManager;
        
        // Setup messages using Kotlin helper for Paging 3
        updateMessagesSource();
        
        // Setup statistics - use conversation counts instead of message counts
        this.unreadCount = conversationRepository.getUnreadConversationsCount();
        this.totalCount = conversationRepository.getTotalConversationsCount();
        
        // Observe repository error states
        errorStateObserver = error -> {
            if (error != null) {
                _errorState.postValue(error);
                _uiState.postValue(InboxUiState.error(error));
            }
        };
        repository.errorState.observeForever(errorStateObserver);
        
        // Observe sync results
        syncResultObserver = result -> {
            if (result != null) {
                _syncResult.postValue(result);
                if (result instanceof SyncResult.Success) {
                    SyncResult.Success success = (SyncResult.Success) result;
                    _uiState.postValue(InboxUiState.success(
                        "Synced " + success.syncedCount + " messages"));
                } else if (result instanceof SyncResult.Error) {
                    SyncResult.Error error = (SyncResult.Error) result;
                    _uiState.postValue(InboxUiState.error(error.message));
                }
            }
        };
        repository.syncResult.observeForever(syncResultObserver);
        
        // Observe search state
        searchStateObserver = state -> {
            _searchState.postValue(state);
        };
        searchRepository.searchState.observeForever(searchStateObserver);
        
        // Observe sync manager state
        syncStateObserver = state -> {
            if (state == SmsSyncManager.SyncState.SYNCING) {
                _uiState.postValue(InboxUiState.SYNCING);
            } else if (state == SmsSyncManager.SyncState.ERROR) {
                _uiState.postValue(InboxUiState.error("Sync error occurred"));
            } else if (state == SmsSyncManager.SyncState.ACTIVE) {
                _uiState.postValue(InboxUiState.success("Real-time sync active"));
            }
        };
        syncManager.syncState.observeForever(syncStateObserver);
        
        // Start real-time sync
        syncManager.startRealTimeSync();
        
        // Initialize offline-first sync manager
        initializeOfflineFirstSync();
        
        // Perform initial incremental sync in background for faster startup
        executor.execute(() -> {
            try {
                Log.d("SimpleInboxViewModel", "Performing incremental conversation sync");
                // Use incremental sync for faster startup
                conversationRepository.syncConversationsFromMessages()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        () -> Log.d("SimpleInboxViewModel", "Incremental conversation sync completed"),
                        error -> {
                            Log.e("SimpleInboxViewModel", "Incremental conversation sync failed, falling back to full sync", error);
                            // Fallback to full sync if incremental fails
                            performFullSync();
                        }
                    );
            } catch (Exception e) {
                Log.e("SimpleInboxViewModel", "Failed to start incremental conversation sync", e);
                performFullSync();
            }
        });
        
        // Load cached conversations immediately for instant UI display
        loadCachedConversations();
    }
    
    public void syncMessages() {
        _uiState.postValue(InboxUiState.SYNCING);
        
        // Try incremental sync first for better performance
        disposables.add(
            repository.syncRecentMessages()
                .andThen(conversationRepository.syncConversationsFromMessages())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Incremental sync completed successfully");
                        _uiState.postValue(InboxUiState.success("Messages updated"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Incremental sync failed, trying full sync", error);
                        // Fallback to full sync
                        performFullSync();
                    }
                )
        );
    }
    
    /**
     * Perform full sync as fallback
     */
    private void performFullSync() {
        disposables.add(
            repository.syncNewMessages()
                .andThen(conversationRepository.syncConversationsFromMessages())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Full sync completed");
                        _uiState.postValue(InboxUiState.success("Full sync completed"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Full sync failed", error);
                        _errorState.postValue("Sync failed: " + error.getMessage());
                        _uiState.postValue(InboxUiState.error("Sync failed: " + error.getMessage()));
                    }
                )
        );
    }
    
    public void markAsRead(String phoneNumber) {
        disposables.add(
            conversationRepository.markConversationAsRead(phoneNumber)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Marked conversation as read: " + phoneNumber),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to mark conversation as read", error);
                        _errorState.postValue("Failed to mark as read: " + error.getMessage());
                    }
                )
        );
    }

    public void setUnreadCount(String phoneNumber, int count) {
        disposables.add(
            conversationRepository.setConversationUnreadCount(phoneNumber, count)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Set unread count for conversation: " + phoneNumber),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to set unread count", error);
                        _errorState.postValue("Failed to update unread count: " + error.getMessage());
                    }
                )
        );
    }

    public void setArchived(long conversationId, boolean archived) {
        disposables.add(
            conversationRepository.updateArchiveStatus(conversationId, archived)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Updated archive status: " + conversationId + " -> " + archived),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to update archive status", error);
                        _errorState.postValue("Failed to update archive status: " + error.getMessage());
                    }
                )
        );
    }

    public void setPinned(long conversationId, boolean pinned) {
        disposables.add(
            conversationRepository.updatePinStatus(conversationId, pinned)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Updated pin status: " + conversationId + " -> " + pinned),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to update pin status", error);
                        _errorState.postValue("Failed to update pin status: " + error.getMessage());
                    }
                )
        );
    }
    
    public void markAsUnread(long conversationId) {
        // Legacy method kept for compatibility; prefer setUnreadCount(phoneNumber, count)
        Log.d("SimpleInboxViewModel", "Mark conversation as unread called without phone number: " + conversationId);
    }
    
    public void deleteConversation(ConversationEntity conversation) {
        disposables.add(
            conversationRepository.deleteConversation(conversation)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Conversation deleted: " + conversation.id);
                        _uiState.postValue(InboxUiState.success("Conversation deleted"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to delete conversation", error);
                        _errorState.postValue("Failed to delete conversation: " + error.getMessage());
                        _uiState.postValue(InboxUiState.error("Failed to delete conversation"));
                    }
                )
        );
    }
    
    public void search(String query) {
        if (query == null) {
            query = "";
        }
        currentSearchQuery = query.trim();
        updateMessagesSource();
        
        // Trigger search suggestions if query is substantial
        if (query.length() >= 2) {
            disposables.add(
                searchRepository.getSearchSuggestions(query)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        suggestions -> {
                            // Handle suggestions (could be exposed via LiveData)
                            Log.d("SimpleInboxViewModel", "Search suggestions: " + suggestions.size());
                        },
                        error -> {
                            Log.e("SimpleInboxViewModel", "Failed to get search suggestions", error);
                        }
                    )
            );
        }
    }
    
    public void clearSearch() {
        currentSearchQuery = "";
        updateMessagesSource();
    }
    
    public void setFilter(FilterType filter) {
        currentFilter = filter;
        updateMessagesSource();
    }
    
    private void updateMessagesSource() {
        Log.d("SimpleInboxViewModel", "Updating messages source - filter: " + currentFilter + ", search: '" + currentSearchQuery + "'");
        
        // Remove previous observer to prevent memory leaks
        if (currentMessagesLiveData != null && messagesObserver != null) {
            currentMessagesLiveData.removeObserver(messagesObserver);
        }
        
        // Convert PagingSource to LiveData<PagingData> using Paging 3 helper
        // IMPORTANT: The pagingSourceFactory MUST return a NEW instance each time
        // Do NOT create a single PagingSource instance and reuse it
        PagingConfig pagingConfig = new PagingConfig(20, 5, false);
        Pager<Integer, ConversationEntity> pager = new Pager<>(pagingConfig, () -> {
            Log.d("SimpleInboxViewModel", "Creating new PagingSource...");
            // Create a fresh PagingSource instance each time this is called
            if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                // Use conversation search
                return conversationRepository.searchConversations(currentSearchQuery);
            } else {
                // Use conversation repository based on filter
                return switch (currentFilter) {
                    case INBOX -> conversationRepository.getActiveConversationsPaged();
                    case SENT -> conversationRepository.getActiveConversationsPaged(); // Sent messages are part of active conversations
                    case UNREAD -> conversationRepository.getUnreadConversationsPaged();
                    default -> conversationRepository.getAllConversationsPaged();
                };
            }
        });
        
        // Use PagingLiveData to convert Pager to LiveData for Java compatibility
        currentMessagesLiveData = PagingLiveData.cachedIn(
            PagingLiveData.getLiveData(pager),
            ViewModelKt.getViewModelScope(this)
        );
        Log.d("SimpleInboxViewModel", "Created LiveData from Pager");
        
        // Create observer and observe the new LiveData
        messagesObserver = pagingData -> {
            Log.d("SimpleInboxViewModel", "PagingData received: " + (pagingData != null ? "non-null" : "null"));
            if (pagingData != null) {
                _messages.postValue(pagingData);
                Log.d("SimpleInboxViewModel", "Posted PagingData to _messages LiveData");
            }
        };
        
        currentMessagesLiveData.observeForever(messagesObserver);
        Log.d("SimpleInboxViewModel", "Observer registered and messages source updated");
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Remove all observeForever observers to prevent memory leaks
        if (repository != null && errorStateObserver != null) {
            repository.errorState.removeObserver(errorStateObserver);
        }
        if (repository != null && syncResultObserver != null) {
            repository.syncResult.removeObserver(syncResultObserver);
        }
        if (searchRepository != null && searchStateObserver != null) {
            searchRepository.searchState.removeObserver(searchStateObserver);
        }
        if (syncManager != null && syncStateObserver != null) {
            syncManager.syncState.removeObserver(syncStateObserver);
        }
        if (offlineFirstSyncManager != null && offlineSyncStateObserver != null) {
            offlineFirstSyncManager.syncState.removeObserver(offlineSyncStateObserver);
        }
        
        // Clean up messages observer
        if (currentMessagesLiveData != null && messagesObserver != null) {
            currentMessagesLiveData.removeObserver(messagesObserver);
        }
        
        // Stop real-time sync
        if (syncManager != null) {
            syncManager.stopRealTimeSync();
        }
        
        // Clean up offline-first sync manager
        if (offlineFirstSyncManager != null) {
            offlineFirstSyncManager.cleanup();
        }
        
        // Dispose RxJava subscriptions
        disposables.clear();
        
        // Shutdown executor
        executor.shutdown();
    }
    
    /**
     * Force manual sync
     */
    public void forceSync() {
        if (syncManager != null) {
            syncManager.forceSync();
        }
    }
    
    /**
     * Toggle auto-sync
     */
    public void setAutoSyncEnabled(boolean enabled) {
        if (syncManager != null) {
            syncManager.setAutoSyncEnabled(enabled);
        }
    }
    
    /**
     * Get sync state
     */
    public LiveData<SmsSyncManager.SyncState> getSyncState() {
        return syncManager != null ? syncManager.syncState : new MutableLiveData<>(SmsSyncManager.SyncState.IDLE);
    }
    
    /**
     * Get search statistics
     */
    public void getSearchStats() {
        if (searchRepository != null) {
            disposables.add(
                searchRepository.getSearchStats()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        stats -> {
                            Log.d("SimpleInboxViewModel", "Search stats: " + stats.indexCoverage + "% coverage");
                        },
                        error -> {
                            Log.e("SimpleInboxViewModel", "Failed to get search stats", error);
                        }
                    )
            );
        }
    }
    
    /**
     * Load cached conversations immediately for instant UI display
     */
    private void loadCachedConversations() {
        executor.execute(() -> {
            try {
                Log.d("SimpleInboxViewModel", "Loading cached conversations for instant display");
                // This will trigger immediate display of cached data
                updateMessagesSource();
                
                // Then perform background sync
                syncManager.performInitialSync();
                
            } catch (Exception e) {
                Log.e("SimpleInboxViewModel", "Failed to load cached conversations", e);
            }
        });
    }
    
    /**
     * Initialize offline-first sync manager
     */
    private void initializeOfflineFirstSync() {
        disposables.add(
            offlineFirstSyncManager.initialize()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Offline-first sync initialized");
                        _uiState.postValue(InboxUiState.success("Offline sync ready"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to initialize offline-first sync", error);
                        _errorState.postValue("Offline sync initialization failed: " + error.getMessage());
                    }
                )
        );
        
        // Observe offline-first sync state
        offlineSyncStateObserver = state -> {
            switch (state) {
                case SYNCING:
                    _uiState.postValue(InboxUiState.SYNCING);
                    break;
                case SYNCED:
                    _uiState.postValue(InboxUiState.success("Offline sync completed"));
                    break;
                case ERROR:
                    _uiState.postValue(InboxUiState.error("Offline sync error"));
                    break;
                case IDLE:
                    // No action needed for idle state
                    break;
            }
        };
        offlineFirstSyncManager.syncState.observeForever(offlineSyncStateObserver);
    }
    
    /**
     * Force sync using offline-first manager
     */
    public void forceOfflineFirstSync() {
        _uiState.postValue(InboxUiState.SYNCING);
        
        disposables.add(
            offlineFirstSyncManager.forceSyncAll()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Force sync completed");
                        _uiState.postValue(InboxUiState.success("Force sync completed"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Force sync failed", error);
                        _errorState.postValue("Force sync failed: " + error.getMessage());
                        _uiState.postValue(InboxUiState.error("Force sync failed"));
                    }
                )
        );
    }
    
    /**
     * Get sync statistics from offline-first manager
     */
    public LiveData<OfflineFirstSyncManager.SyncStatistics> getSyncStatistics() {
        return offlineFirstSyncManager.getSyncStatistics();
    }
    
    // Enums and Classes
    public enum FilterType {
        ALL,
        INBOX,
        SENT,
        UNREAD
    }
}
