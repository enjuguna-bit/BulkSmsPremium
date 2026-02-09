package com.afriserve.smsmanager.ui.inbox;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagingData;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingLiveData;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltViewModel
public class InboxViewModel extends ViewModel {
    
    private final SmsRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // UI State
    private final MutableLiveData<InboxUiState> _uiState = new MutableLiveData<>();
    public final LiveData<InboxUiState> uiState = _uiState;
    
    private final MutableLiveData<FilterType> _filterType = new MutableLiveData<>();
    public final LiveData<FilterType> filterType = _filterType;
    
    private final MutableLiveData<String> _searchQuery = new MutableLiveData<>();
    public final LiveData<String> searchQuery = _searchQuery;
    
    // Paged Data
    public final LiveData<PagingData<SmsEntity>> messages;
    
    // Statistics
    public final LiveData<Integer> unreadCount;
    public final LiveData<Integer> totalCount;
    
    @Inject
    public InboxViewModel(SmsRepository repository) {
        this.repository = repository;
        
        // Initialize state
        _uiState.setValue(InboxUiState.LOADING);
        _filterType.setValue(FilterType.ALL);
        _searchQuery.setValue("");
        
        // Setup messages flow - using Paging 3 with Flow API
        // Note: PagingLiveData requires Kotlin Flow which is not directly available in Java
        // Use a workaround by creating empty PagingData and letting observers handle updates
        this.messages = new MutableLiveData<>(null);
        
        // Setup statistics - simplified for Java
        this.unreadCount = repository.getUnreadCount();
        this.totalCount = repository.getTotalCount();
        
        syncMessages();
    }
    
    // Actions
    public void setFilter(FilterType filter) {
        _filterType.setValue(filter);
    }
    
    public void search(String query) {
        _searchQuery.setValue(query);
    }
    
    public void clearSearch() {
        _searchQuery.setValue("");
    }
    
    public void syncMessages() {
        executor.execute(() -> {
            _uiState.setValue(InboxUiState.SYNCING);
            
            try {
                repository.syncNewMessages();
                _uiState.setValue(InboxUiState.success("Messages synced"));
            } catch (Exception e) {
                _uiState.setValue(InboxUiState.error("Sync failed: " + e.getMessage()));
            }
        });
    }
    
    public void markAsRead(long messageId) {
        executor.execute(() -> {
            try {
                repository.markAsRead(messageId);
            } catch (Exception e) {
                _uiState.setValue(InboxUiState.error("Failed to mark as read: " + e.getMessage()));
            }
        });
    }
    
    public void markAsUnread(long messageId) {
        executor.execute(() -> {
            try {
                repository.markAsUnread(messageId);
            } catch (Exception e) {
                _uiState.setValue(InboxUiState.error("Failed to mark as unread: " + e.getMessage()));
            }
        });
    }
    
    public void deleteMessage(SmsEntity message) {
        executor.execute(() -> {
            try {
                repository.deleteMessage(message);
                _uiState.setValue(InboxUiState.success("Message deleted"));
            } catch (Exception e) {
                _uiState.setValue(InboxUiState.error("Failed to delete: " + e.getMessage()));
            }
        });
    }
    
    // Enums and Classes
    public enum FilterType {
        ALL,
        INBOX,
        SENT,
        UNREAD
    }
    
    // Helper class for Pair
    private static class Pair<A, B> {
        public final A first;
        public final B second;
        
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
