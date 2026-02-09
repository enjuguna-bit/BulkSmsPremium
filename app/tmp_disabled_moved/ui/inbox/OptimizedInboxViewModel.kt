package com.bulksms.smsmanager.ui.inbox

import android.util.Log
import kotlinx.coroutines.flow.*
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.rxjava3.asFlow
import com.bulksms.smsmanager.data.entity.ConversationEntity
import com.bulksms.smsmanager.data.repository.ConversationRepository
import com.bulksms.smsmanager.data.repository.SmsRepository
import com.bulksms.smsmanager.data.repository.SmsSearchRepository
import com.bulksms.smsmanager.data.sync.SmsSyncManager
import com.bulksms.smsmanager.data.repository.SyncResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Optimized Inbox ViewModel using coroutines for background operations
 * Shows cached messages immediately while syncing in background
 */
@HiltViewModel
class OptimizedInboxViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val smsRepository: SmsRepository,
    private val searchRepository: SmsSearchRepository,
    private val syncManager: SmsSyncManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.LOADING)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()
    
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()
    
    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()
    
    // Current filter and search state
    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Paged conversations flow - automatically cached and shows data immediately
    val conversations: Flow<PagingData<ConversationEntity>> = combine(
        _filterType,
        _searchQuery
    ) { filter, search ->
        Pair(filter, search)
    }.flatMapLatest { (filter, search) ->
        when {
            search.isNotBlank() -> {
                Log.d("OptimizedInboxVM", "Searching conversations: $search")
                conversationRepository.searchConversationsFlow(search)
                    .asFlow()
                    .flatMapLatest { pagingSource ->
                        flow { emit(PagingData.from(pagingSource.load())) }
                    }
            }
            else -> {
                Log.d("OptimizedInboxVM", "Loading conversations with filter: $filter")
                when (filter) {
                    FilterType.INBOX -> conversationRepository.getActiveConversationsPagedFlow()
                        .asFlow()
                        .flatMapLatest { pagingSource ->
                            flow { emit(PagingData.from(pagingSource.load())) }
                        }
                    FilterType.SENT -> conversationRepository.getActiveConversationsPagedFlow()
                        .asFlow()
                        .flatMapLatest { pagingSource ->
                            flow { emit(PagingData.from(pagingSource.load())) }
                        }
                    FilterType.UNREAD -> conversationRepository.getUnreadConversationsPagedFlow()
                        .asFlow()
                        .flatMapLatest { pagingSource ->
                            flow { emit(PagingData.from(pagingSource.load())) }
                        }
                    FilterType.ALL -> conversationRepository.getAllConversationsPagedFlow()
                        .asFlow()
                        .flatMapLatest { pagingSource ->
                            flow { emit(PagingData.from(pagingSource.load())) }
                        }
                }
            }
        }
    }.cachedIn(viewModelScope)
    
    // Statistics LiveData for compatibility
    val unreadCount: LiveData<Int> = liveData {
        emitSource(conversationRepository.getUnreadConversationsCount())
    }
    val totalCount: LiveData<Int> = liveData {
        emitSource(conversationRepository.getTotalConversationsCount())
    }
    
    init {
        viewModelScope.launch {
            try {
                // Load cached conversations immediately
                Log.d("OptimizedInboxVM", "Loading cached conversations immediately")
                _uiState.value = InboxUiState.success("Loading cached messages...")
                
                // Start background sync operations
                launch(Dispatchers.IO) {
                    try {
                        // Start real-time sync
                        syncManager.startRealTimeSync()
                        
                        // Perform initial sync in background
                        syncManager.performInitialSync()
                        
                        // Sync conversations from existing messages
                        conversationRepository.syncConversationsFromMessages()
                            .collect { result ->
                                when (result) {
                                    is com.bulksms.smsmanager.data.repository.SyncResult.Success -> {
                                        _syncResult.value = result
                                        _uiState.value = InboxUiState.success("Synced ${result.syncedCount} messages")
                                    }
                                    is com.bulksms.smsmanager.data.repository.SyncResult.Error -> {
                                        _syncResult.value = result
                                        _errorState.value = result.message
                                        _uiState.value = InboxUiState.error(result.message)
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        Log.e("OptimizedInboxVM", "Background sync failed", e)
                        _errorState.value = "Background sync failed: ${e.message}"
                    }
                }
                
                // Observe sync manager state
                syncManager.syncState.collect { state ->
                    _uiState.value = when (state) {
                        SmsSyncManager.SyncState.SYNCING -> InboxUiState.SYNCING
                        SmsSyncManager.SyncState.ERROR -> InboxUiState.error("Sync failed")
                        SmsSyncManager.SyncState.ACTIVE -> InboxUiState.success("Real-time sync active")
                        else -> InboxUiState.success("Ready")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "ViewModel initialization failed", e)
                _errorState.value = "Initialization failed: ${e.message}"
                _uiState.value = InboxUiState.error("Initialization failed")
            }
        }
    }
    
    fun setFilter(filter: FilterType) {
        viewModelScope.launch {
            _filterType.value = filter
            Log.d("OptimizedInboxVM", "Filter changed to: $filter")
        }
    }
    
    fun search(query: String) {
        viewModelScope.launch {
            val trimmedQuery = query.trim()
            _searchQuery.value = trimmedQuery
            Log.d("OptimizedInboxVM", "Search query: '$trimmedQuery'")
            
            // Trigger search suggestions if query is substantial
            if (trimmedQuery.length >= 2) {
                launch(Dispatchers.IO) {
                    try {
                        searchRepository.getSearchSuggestions(trimmedQuery)
                            .collect { suggestions ->
                                Log.d("OptimizedInboxVM", "Search suggestions: ${suggestions.size}")
                            }
                    } catch (e: Exception) {
                        Log.e("OptimizedInboxVM", "Failed to get search suggestions", e)
                    }
                }
            }
        }
    }
    
    fun clearSearch() {
        viewModelScope.launch {
            _searchQuery.value = ""
            Log.d("OptimizedInboxVM", "Search cleared")
        }
    }
    
    fun syncMessages() {
        viewModelScope.launch {
            try {
                _uiState.value = InboxUiState.SYNCING
                
                // Perform sync in background
                withContext(Dispatchers.IO) {
                    smsRepository.syncNewMessages()
                        .andThen(conversationRepository.syncConversationsFromMessages())
                        .collect { result ->
                            when (result) {
                                is com.bulksms.smsmanager.data.repository.SyncResult.Success -> {
                                    _syncResult.value = result
                                    _uiState.value = InboxUiState.success("Synced ${result.syncedCount} messages")
                                }
                                is com.bulksms.smsmanager.data.repository.SyncResult.Error -> {
                                    _syncResult.value = result
                                    _errorState.value = result.message
                                    _uiState.value = InboxUiState.error(result.message)
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Manual sync failed", e)
                _errorState.value = "Sync failed: ${e.message}"
                _uiState.value = InboxUiState.error("Sync failed")
            }
        }
    }
    
    fun markAsRead(conversationId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    conversationRepository.markConversationAsRead(conversationId.toString())
                        .collect {
                            Log.d("OptimizedInboxVM", "Marked conversation as read: $conversationId")
                        }
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Failed to mark as read", e)
                _errorState.value = "Failed to mark as read: ${e.message}"
            }
        }
    }
    
    fun markAsUnread(conversationId: Long) {
        viewModelScope.launch {
            Log.d("OptimizedInboxVM", "Mark conversation as unread not yet implemented: $conversationId")
        }
    }
    
    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    conversationRepository.deleteConversation(conversation)
                        .collect {
                            Log.d("OptimizedInboxVM", "Conversation deleted: ${conversation.id}")
                            _uiState.value = InboxUiState.success("Conversation deleted")
                        }
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Failed to delete conversation", e)
                _errorState.value = "Failed to delete conversation: ${e.message}"
                _uiState.value = InboxUiState.error("Failed to delete conversation")
            }
        }
    }
    
    fun forceSync() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.forceSync()
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Force sync failed", e)
                _errorState.value = "Force sync failed: ${e.message}"
            }
        }
    }
    
    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.setAutoSyncEnabled(enabled)
                }
                Log.d("OptimizedInboxVM", "Auto-sync enabled: $enabled")
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Failed to set auto-sync", e)
            }
        }
    }
    
    fun getSyncState(): StateFlow<SmsSyncManager.SyncState> = syncManager.syncState
    
    fun debugDatabase() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Log.d("OptimizedInboxVM", "=== DATABASE DEBUG INFO ===")
                    // Add debug queries here if needed
                    Log.d("OptimizedInboxVM", "=== END DATABASE DEBUG ===")
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Database debug failed", e)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    syncManager.stopRealTimeSync()
                }
            } catch (e: Exception) {
                Log.e("OptimizedInboxVM", "Failed to stop real-time sync", e)
            }
        }
        Log.d("OptimizedInboxVM", "ViewModel cleared")
    }
    
    enum class FilterType {
        ALL,
        INBOX,
        SENT,
        UNREAD
    }
}
