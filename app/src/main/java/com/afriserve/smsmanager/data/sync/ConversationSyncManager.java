package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.content.Context;

import java.util.List;

/**
 * Conversation-specific sync manager
 * Coordinates conversation sync operations and integrates with existing sync infrastructure
 */
@Singleton
public class ConversationSyncManager {
    
    private static final String TAG = "ConversationSyncManager";
    
    private final ConversationRepository conversationRepository;
    private final SmsRepository smsRepository;
    private final SmsSearchRepository searchRepository;
    private final Context context;
    
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Sync state
    private final MutableLiveData<SyncState> _syncState = new MutableLiveData<>(SyncState.IDLE);
    public final LiveData<SyncState> syncState = _syncState;
    
    // Sync statistics
    private final MutableLiveData<SyncStatistics> _syncStats = new MutableLiveData<>();
    public final LiveData<SyncStatistics> syncStats = _syncStats;
    
    // Auto-sync enabled
    private boolean autoSyncEnabled = true;

    @Inject
    public ConversationSyncManager(
            ConversationRepository conversationRepository,
            SmsRepository smsRepository,
            SmsSearchRepository searchRepository,
            @ApplicationContext Context context) {
        this.conversationRepository = conversationRepository;
        this.smsRepository = smsRepository;
        this.searchRepository = searchRepository;
        this.context = context;
        
        Log.d(TAG, "ConversationSyncManager initialized");
    }

    /**
     * Start conversation sync
     */
    public void startSync() {
        if (!autoSyncEnabled) {
            Log.d(TAG, "Auto-sync is disabled");
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        // Sync conversations from existing messages
        disposables.add(
            conversationRepository.syncConversationsFromMessages()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation sync completed");
                    },
                    error -> {
                        Log.e(TAG, "Conversation sync failed", error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }

    /**
     * Stop conversation sync
     */
    public void stopSync() {
        _syncState.postValue(SyncState.STOPPED);
        Log.d(TAG, "Conversation sync stopped");
    }

    /**
     * Enable/disable auto-sync
     */
    public void setAutoSyncEnabled(boolean enabled) {
        this.autoSyncEnabled = enabled;
        Log.d(TAG, "Auto-sync " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Force manual sync
     */
    public void forceSync() {
        Log.d(TAG, "Force conversation sync requested");
        startSync();
    }

    /**
     * Sync conversations for a specific phone number
     */
    public void syncConversationForPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            Completable.defer(() -> {
                    List<SmsEntity> messages = smsRepository.getMessagesByPhoneNumber(phoneNumber);
                    if (messages.isEmpty()) {
                        ConversationEntity conv = new ConversationEntity();
                        conv.phoneNumber = phoneNumber;
                        return conversationRepository.deleteConversation(conv);
                    }
                    
                    // Create or update conversation
                    return conversationRepository.updateConversationFromMessage(
                        messages.get(0) // Use latest message
                    );
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation sync completed for: " + phoneNumber);
                    },
                    error -> {
                        Log.e(TAG, "Conversation sync failed for: " + phoneNumber, error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }

    /**
     * Sync conversations for a specific thread ID
     */
    public void syncConversationForThreadId(long threadId) {
        if (threadId <= 0) {
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            Completable.defer(() -> {
                    List<SmsEntity> messages = smsRepository.getMessagesByThreadId(threadId);
                    if (messages.isEmpty()) {
                        ConversationEntity emptyConversation = new ConversationEntity();
                        emptyConversation.threadId = threadId;
                        return conversationRepository.deleteConversation(emptyConversation);
                    }
                    
                    // Create or update conversation using thread ID
                    ConversationEntity conversation = new ConversationEntity();
                    conversation.threadId = threadId;
                    conversation.phoneNumber = "thread:" + threadId;
                    
                    return conversationRepository.updateConversationWithNewMessage(
                        threadId,
                        conversation.phoneNumber,
                        messages.get(0).getDate(),
                        messages.get(0).getBody(),
                        "INBOX",
                        true, // incoming
                        System.currentTimeMillis()
                    );
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation sync completed for thread: " + threadId);
                    },
                    error -> {
                        Log.e(TAG, "Conversation sync failed for thread: " + threadId, error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }

    /**
     * Update conversation with new message
     */
    public void updateConversationWithMessage(SmsEntity message) {
        if (message == null) {
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            conversationRepository.updateConversationFromMessage(message)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation updated with message: " + message.id);
                    },
                    error -> {
                        Log.e(TAG, "Failed to update conversation with message: " + message.id, error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }

    /**
     * Mark conversation as read
     */
    public void markConversationAsRead(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            conversationRepository.markConversationAsRead(phoneNumber)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation marked as read: " + phoneNumber);
                    },
                    error -> {
                        Log.e(TAG, "Failed to mark conversation as read: " + phoneNumber, error);
                        _syncState.postValue(SyncState.ERROR);
                    }
                )
        );
    }

    /**
     * Delete conversation
     */
    public void deleteConversation(ConversationEntity conversation) {
        if (conversation == null) {
            return;
        }

        _syncState.postValue(SyncState.SYNCING);
        
        disposables.add(
            conversationRepository.deleteConversation(conversation)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        _syncState.postValue(SyncState.SYNCED);
                        Log.d(TAG, "Conversation deleted: " + conversation.id);
                    },
                    error -> {
                        Log.e(TAG, "Failed to delete conversation: " + conversation.id, error);
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
     * Cleanup resources
     */
    public void cleanup() {
        disposables.clear();
        Log.d(TAG, "ConversationSyncManager cleaned up");
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
     * Sync statistics data class
     */
    public static class SyncStatistics {
        public int conversationsSynced;
        public int conversationsUpdated;
        public int conversationsDeleted;
        public long syncDuration;
        
        public SyncStatistics() {
            this.conversationsSynced = 0;
            this.conversationsUpdated = 0;
            this.conversationsDeleted = 0;
            this.syncDuration = 0;
        }
    }
}
