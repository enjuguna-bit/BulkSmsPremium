package com.bulksms.smsmanager.ui.inbox;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.Observer;
import androidx.paging.PagingData;
import androidx.paging.PagingSource;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingLiveData;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.repository.SmsRepository;
import com.bulksms.smsmanager.data.repository.SyncResult;
import com.bulksms.smsmanager.data.repository.ConversationRepository;
import com.bulksms.smsmanager.data.repository.SmsSearchRepository;
import com.bulksms.smsmanager.data.sync.SmsSyncManager;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltViewModel
public class SimpleInboxViewModel extends ViewModel {
    
    private final SmsRepository repository;
    private final ConversationRepository conversationRepository;
    private final SmsSearchRepository searchRepository;
    private final SmsSyncManager syncManager;
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
    private final MutableLiveData<PagingData<SmsEntity>> _messages = new MutableLiveData<>();
    public final LiveData<PagingData<SmsEntity>> messages = _messages;
    
    // Current filter and search
    private FilterType currentFilter = FilterType.ALL;
    private String currentSearchQuery = "";
    
    // Current messages LiveData observer (to prevent memory leaks)
    private LiveData<PagingData<SmsEntity>> currentMessagesLiveData;
    private Observer<PagingData<SmsEntity>> messagesObserver;
    
    // Statistics
    public final LiveData<Integer> unreadCount;
    public final LiveData<Integer> totalCount;
    
    @Inject
    public SimpleInboxViewModel(
        SmsRepository repository,
        ConversationRepository conversationRepository,
        SmsSearchRepository searchRepository,
        SmsSyncManager syncManager
    ) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.searchRepository = searchRepository;
        this.syncManager = syncManager;
        
        // Setup messages using Kotlin helper for Paging 3
        updateMessagesSource();
        
        // Setup statistics
        this.unreadCount = repository.getUnreadCount();
        this.totalCount = repository.getTotalCount();
        
        // Observe repository error states
        repository.errorState.observeForever(error -> {
            if (error != null) {
                _errorState.postValue(error);
                _uiState.postValue(InboxUiState.error(error));
            }
        });
        
        // Observe sync results
        repository.syncResult.observeForever(result -> {
            if (result != null) {
                _syncResult.postValue(result);
                if (result instanceof SyncResult.Success) {
                    SyncResult.Success success = (SyncResult.Success) result;
                    _uiState.postValue(InboxUiState.success(
                        "Synced " + success.syncedCount + " messages"));
                } else if (result instanceof SyncResult.Error) {
                    SyncResult.Error error = (SyncResult.Error) result;
                    _errorState.postValue(error.message);
                    _uiState.postValue(InboxUiState.error(error.message));
                }
            }
        });
        
        // Observe search state
        searchRepository.searchState.observeForever(state -> {
            _searchState.postValue(state);
        });
        
        // Observe sync manager state
        syncManager.syncState.observeForever(state -> {
            if (state == SmsSyncManager.SyncState.SYNCING) {
                _uiState.postValue(InboxUiState.SYNCING);
            } else if (state == SmsSyncManager.SyncState.ERROR) {
                _uiState.postValue(InboxUiState.error("Sync failed"));
            } else if (state == SmsSyncManager.SyncState.ACTIVE) {
                _uiState.postValue(InboxUiState.success("Real-time sync active"));
            }
        });
        
        // Start real-time sync
        syncManager.startRealTimeSync();
        
        // Perform initial sync
        syncManager.performInitialSync();
    }
    
    public void syncMessages() {
        _uiState.postValue(InboxUiState.SYNCING);
        
        disposables.add(
            repository.syncNewMessages()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        // Success handled by repository syncResult observer
                        Log.d("SimpleInboxViewModel", "Sync completed successfully");
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Sync failed", error);
                        _errorState.postValue("Sync failed: " + error.getMessage());
                        _uiState.postValue(InboxUiState.error("Sync failed: " + error.getMessage()));
                    }
                )
        );
    }
    
    public void markAsRead(long messageId) {
        disposables.add(
            repository.markAsRead(messageId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Marked as read: " + messageId),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to mark as read", error);
                        _errorState.postValue("Failed to mark as read: " + error.getMessage());
                    }
                )
        );
    }
    
    public void markAsUnread(long messageId) {
        disposables.add(
            repository.markAsUnread(messageId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> Log.d("SimpleInboxViewModel", "Marked as unread: " + messageId),
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to mark as unread", error);
                        _errorState.postValue("Failed to mark as unread: " + error.getMessage());
                    }
                )
        );
    }
    
    public void deleteMessage(SmsEntity message) {
        disposables.add(
            repository.deleteMessage(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d("SimpleInboxViewModel", "Message deleted: " + message.id);
                        _uiState.postValue(InboxUiState.success("Message deleted"));
                    },
                    error -> {
                        Log.e("SimpleInboxViewModel", "Failed to delete message", error);
                        _errorState.postValue("Failed to delete message: " + error.getMessage());
                        _uiState.postValue(InboxUiState.error("Failed to delete message"));
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
        // Remove previous observer to prevent memory leaks
        if (currentMessagesLiveData != null && messagesObserver != null) {
            currentMessagesLiveData.removeObserver(messagesObserver);
        }
        
        // Convert PagingSource to LiveData<PagingData> using Paging 3 helper
        // IMPORTANT: The pagingSourceFactory MUST return a NEW instance each time
        // Do NOT create a single PagingSource instance and reuse it
        PagingConfig pagingConfig = new PagingConfig(20, 5, false);
        Pager<Integer, SmsEntity> pager = new Pager<>(pagingConfig, () -> {
            // Create a fresh PagingSource instance each time this is called
            if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
                // Use advanced search repository
                return switch (currentFilter) {
                    case INBOX -> searchRepository.searchMessagesByType(currentSearchQuery, 1);
                    case SENT -> searchRepository.searchMessagesByType(currentSearchQuery, 2);
                    case UNREAD -> searchRepository.searchUnreadMessages(currentSearchQuery);
                    default -> searchRepository.searchMessages(currentSearchQuery);
                };
            } else {
                // Use regular repository
                return switch (currentFilter) {
                    case INBOX -> repository.getInboxMessagesPaged();
                    case SENT -> repository.getSentMessagesPaged();
                    case UNREAD -> repository.getUnreadMessagesPaged();
                    default -> repository.getAllMessagesPaged();
                };
            }
        });
        
        // Use PagingLiveData to convert Pager to LiveData for Java compatibility
        currentMessagesLiveData = PagingLiveData.getLiveData(pager);
        
        // Create observer and observe the new LiveData
        messagesObserver = pagingData -> {
            if (pagingData != null) {
                _messages.postValue(pagingData);
            }
        };
        
        currentMessagesLiveData.observeForever(messagesObserver);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Stop real-time sync
        if (syncManager != null) {
            syncManager.stopRealTimeSync();
        }
        
        // Clean up observer to prevent memory leaks
        if (currentMessagesLiveData != null && messagesObserver != null) {
            currentMessagesLiveData.removeObserver(messagesObserver);
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
    
    // Enums and Classes
    public enum FilterType {
        ALL,
        INBOX,
        SENT,
        UNREAD
    }
}
