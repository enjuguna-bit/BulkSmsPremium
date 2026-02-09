package com.afriserve.smsmanager.data.repository;

import android.util.Log;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagingSource;

import com.afriserve.smsmanager.data.dao.ConversationDao;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.contacts.ContactResolver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.content.Context;

/**
 * Repository for conversation data operations
 * Handles conversation threading and updates
 */
@Singleton
public class ConversationRepository {
    
    private static final String TAG = "ConversationRepository";
    
    private final ConversationDao conversationDao;
    private final SmsDao smsDao;
    private final ContactResolver contactResolver;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    
    // Error states
    private final MutableLiveData<String> _errorState = new MutableLiveData<>();
    public final LiveData<String> errorState = _errorState;
    
    @Inject
    public ConversationRepository(
        ConversationDao conversationDao,
        SmsDao smsDao,
        ContactResolver contactResolver,
        @ApplicationContext Context context
    ) {
        this.conversationDao = conversationDao;
        this.smsDao = smsDao;
        this.contactResolver = contactResolver;
        this.context = context.getApplicationContext();
    }
    
    /**
     * Get all conversations PagingSource
     */
    public PagingSource<Integer, ConversationEntity> getAllConversationsPaged() {
        return conversationDao.getAllConversationsPaged();
    }
    
    /**
     * Get active (non-archived) conversations PagingSource
     */
    public PagingSource<Integer, ConversationEntity> getActiveConversationsPaged() {
        return conversationDao.getActiveConversationsPaged();
    }
    
    /**
     * Get unread conversations PagingSource
     */
    public PagingSource<Integer, ConversationEntity> getUnreadConversationsPaged() {
        return conversationDao.getUnreadConversationsPaged();
    }
    
    /**
     * Search conversations PagingSource
     */
    public PagingSource<Integer, ConversationEntity> searchConversations(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllConversationsPaged();
        }
        return conversationDao.searchConversations(query);
    }
    
    /**
     * Get conversation statistics
     */
    public LiveData<Integer> getUnreadConversationsCount() {
        // Convert Single to LiveData
        MutableLiveData<Integer> liveData = new MutableLiveData<>();
        conversationDao.getUnreadConversationsCount()
            .subscribeOn(Schedulers.io())
            .subscribe(
                liveData::postValue,
                error -> {
                    Log.e(TAG, "Failed to get unread conversations count", error);
                    _errorState.postValue("Failed to get statistics: " + error.getMessage());
                }
            );
        return liveData;
    }
    
    public LiveData<Integer> getTotalConversationsCount() {
        MutableLiveData<Integer> liveData = new MutableLiveData<>();
        conversationDao.getTotalConversationsCount()
            .subscribeOn(Schedulers.io())
            .subscribe(
                liveData::postValue,
                error -> {
                    Log.e(TAG, "Failed to get total conversations count", error);
                    _errorState.postValue("Failed to get statistics: " + error.getMessage());
                }
            );
        return liveData;
    }
    
    /**
     * Create or update conversation from SMS message
     */
    public Completable updateConversationFromMessage(SmsEntity message) {
        return Completable.fromAction(() -> {
            try {
                String phoneNumber = normalizePhoneNumber(message.phoneNumber);
                Long threadId = message.threadId;

                ConversationEntity conversation = getOrCreateConversation(threadId, phoneNumber);

                // Update existing conversation stats
                conversation.messageCount++;
                if (message.isUnread()) {
                    conversation.unreadCount++;
                }
                
                // Update conversation details
                conversation.lastMessageTime = message.getDate();
                conversation.lastMessagePreview = truncateMessage(message.getBody());
                conversation.lastMessageType = getMessageType(message);
                conversation.updatedAt = System.currentTimeMillis();
                conversation.threadId = threadId;
                
                // Resolve contact name and photo
                try {
                    if (phoneNumber != null) {
                        String contactName = contactResolver.getContactName(phoneNumber);
                        if (contactName != null && !contactName.trim().isEmpty()) {
                            if (!contactName.equals(phoneNumber)) {
                                conversation.contactName = contactName;
                            } else if (conversation.contactName == null || conversation.contactName.trim().isEmpty()) {
                                // Store phone number as display name when contact is not saved
                                conversation.contactName = PhoneNumberUtils.formatForDisplay(phoneNumber);
                            }
                        }
                        
                        Uri photoUri = contactResolver.getContactPhotoUri(phoneNumber);
                        if (photoUri != null) {
                            conversation.contactPhotoUri = photoUri.toString();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to resolve contact info for: " + phoneNumber, e);
                }
                
                // Save conversation
                if (conversation.id == 0) {
                    conversationDao.insertConversation(conversation).blockingAwait();
                } else {
                    conversationDao.updateConversation(conversation).blockingAwait();
                }
                
                Log.d(TAG, "Updated conversation for: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update conversation from message", e);
                _errorState.postValue("Failed to update conversation: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Mark conversation as read
     */
    public Completable markConversationAsRead(String phoneNumber) {
        return conversationDao.markConversationAsRead(normalizePhoneNumber(phoneNumber))
            .subscribeOn(Schedulers.io());
    }

    /**
     * Set unread count for a conversation (used for undo actions)
     */
    public Completable setConversationUnreadCount(String phoneNumber, int count) {
        return conversationDao.setConversationUnreadCount(normalizePhoneNumber(phoneNumber), Math.max(0, count))
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get or create conversation by phone number
     * This method retrieves an existing conversation or creates a new one
     */
    public ConversationEntity getOrCreateConversation(String phoneNumber) {
        return getOrCreateConversation(null, phoneNumber);
    }

    /**
     * Get or create conversation by threadId or phone number
     */
    public ConversationEntity getOrCreateConversation(Long threadId, String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        ConversationEntity existing = null;

        try {
            if (threadId != null && threadId > 0) {
                existing = conversationDao.getConversationByThreadId(threadId).blockingGet();
            }
        } catch (Exception e) {
            // ignore and fallback
        }

        if (existing == null) {
            try {
                if (normalized != null && !normalized.trim().isEmpty()) {
                    existing = conversationDao.getConversationByPhoneNumber(normalized).blockingGet();
                }
            } catch (Exception e) {
                // ignore and fallback
            }
        }

        if (existing != null) {
            // Backfill display name for thread-based conversations when we have a phone number
            try {
                if (normalized != null && (existing.contactName == null || existing.contactName.trim().isEmpty())) {
                    String contactName = contactResolver.getContactName(normalized);
                    if (contactName != null && !contactName.trim().isEmpty()) {
                        if (!contactName.equals(normalized)) {
                            existing.contactName = contactName;
                        } else {
                            existing.contactName = PhoneNumberUtils.formatForDisplay(normalized);
                        }
                    }
                    Uri photoUri = contactResolver.getContactPhotoUri(normalized);
                    if (photoUri != null) {
                        existing.contactPhotoUri = photoUri.toString();
                    }
                    conversationDao.updateConversation(existing).blockingAwait();
                }
            } catch (Exception ignored) {
            }
            return existing;
        }

        // Create new conversation if it doesn't exist
        String key = normalized != null ? normalized : ("thread:" + (threadId != null ? threadId : "unknown"));
        Log.d(TAG, "Creating new conversation for: " + key);
        ConversationEntity conversation = new ConversationEntity();
        conversation.phoneNumber = key;
        conversation.threadId = threadId;
        conversation.messageCount = 0;
        conversation.unreadCount = 0;
        conversation.createdAt = System.currentTimeMillis();
        conversation.updatedAt = System.currentTimeMillis();

        // Try to resolve contact info
        try {
            if (normalized != null) {
                String contactName = contactResolver.getContactName(normalized);
                if (contactName != null && !contactName.trim().isEmpty()) {
                    if (!contactName.equals(normalized)) {
                        conversation.contactName = contactName;
                    } else {
                        conversation.contactName = PhoneNumberUtils.formatForDisplay(normalized);
                    }
                }

                Uri photoUri = contactResolver.getContactPhotoUri(normalized);
                if (photoUri != null) {
                    conversation.contactPhotoUri = photoUri.toString();
                }
            }
        } catch (Exception contactError) {
            Log.w(TAG, "Failed to resolve contact info", contactError);
        }

        // Insert and return
        conversationDao.insertConversation(conversation).blockingAwait();
        return conversation;
    }
    
    /**
     * Update conversation with new message
     */
    public Completable updateConversationWithNewMessage(
        String phoneNumber,
        long messageTimestamp,
        String messagePreview,
        String messageType,
        boolean isIncoming,
        long timestamp
    ) {
        return Completable.fromAction(() -> {
            try {
                String normalized = normalizePhoneNumber(phoneNumber);
                ConversationEntity conversation = getOrCreateConversation(null, normalized);
                
                conversation.lastMessageTime = messageTimestamp;
                conversation.lastMessagePreview = truncateMessage(messagePreview);
                conversation.lastMessageType = messageType;
                conversation.messageCount++;
                if (isIncoming) {
                    conversation.unreadCount++;
                }
                conversation.updatedAt = timestamp;
                
                conversationDao.updateConversation(conversation).blockingAwait();
                Log.d(TAG, "Updated conversation with new message: " + normalized);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update conversation with new message", e);
                _errorState.postValue("Failed to update conversation: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Update conversation with new message using threadId (preferred)
     */
    public Completable updateConversationWithNewMessage(
        Long threadId,
        String phoneNumber,
        long messageTimestamp,
        String messagePreview,
        String messageType,
        boolean isIncoming,
        long timestamp
    ) {
        return Completable.fromAction(() -> {
            try {
                String normalized = normalizePhoneNumber(phoneNumber);
                ConversationEntity conversation = getOrCreateConversation(threadId, normalized);

                conversation.lastMessageTime = messageTimestamp;
                conversation.lastMessagePreview = truncateMessage(messagePreview);
                conversation.lastMessageType = messageType;
                conversation.messageCount++;
                if (isIncoming) {
                    conversation.unreadCount++;
                }
                conversation.updatedAt = timestamp;
                conversation.threadId = threadId;

                conversationDao.updateConversation(conversation).blockingAwait();
                Log.d(TAG, "Updated conversation with new message: " + normalized);

            } catch (Exception e) {
                Log.e(TAG, "Failed to update conversation with new message", e);
                _errorState.postValue("Failed to update conversation: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Pin/unpin conversation
     */
    public Completable updatePinStatus(long conversationId, boolean isPinned) {
        return conversationDao.updatePinStatus(conversationId, isPinned)
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Archive/unarchive conversation
     */
    public Completable updateArchiveStatus(long conversationId, boolean isArchived) {
        return conversationDao.updateArchiveStatus(conversationId, isArchived)
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Delete conversation
     */
    public Completable deleteConversation(ConversationEntity conversation) {
        return Completable.fromAction(() -> {
            try {
                List<SmsEntity> messages = new java.util.ArrayList<>();
                try {
                    if (conversation.threadId != null && conversation.threadId > 0) {
                        messages.addAll(smsDao.getMessagesByThreadId(conversation.threadId).blockingGet());
                    } else {
                        messages.addAll(smsDao.getRecentSmsByStatus("SENT", 1000).blockingGet());
                        messages.addAll(smsDao.getRecentSmsByStatus("DELIVERED", 1000).blockingGet());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not fetch messages for phone", e);
                }
                
                // Filter messages by phone number and delete
                List<SmsEntity> messagesToDelete = new java.util.ArrayList<>();
                for (SmsEntity msg : messages) {
                    if (conversation.threadId != null && conversation.threadId > 0) {
                        messagesToDelete.add(msg);
                    } else if (conversation.phoneNumber.equals(msg.phoneNumber)) {
                        messagesToDelete.add(msg);
                    }
                }
                
                if (!messagesToDelete.isEmpty()) {
                    smsDao.deleteSmsList(messagesToDelete).blockingAwait();
                }
                
                // Delete conversation
                conversationDao.deleteConversation(conversation).blockingAwait();
                
                Log.d(TAG, "Deleted conversation: " + conversation.phoneNumber);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete conversation", e);
                _errorState.postValue("Failed to delete conversation: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Sync all conversations from existing messages
     */
    public Completable syncConversationsFromMessages() {
        return Completable.fromAction(() -> {
            try {
                // Get all messages grouped by phone number
                // This would typically be a custom query in SmsDao
                // For now, we'll use a simplified approach
                
                Log.d(TAG, "Syncing conversations from messages...");
                
                // Get all messages and create conversations
                // Use the new getAllRecentSms method to get all messages regardless of status
                List<SmsEntity> allMessages = new java.util.ArrayList<>();
                try {
                    allMessages.addAll(smsDao.getAllRecentSms(10000).blockingGet());
                    Log.d(TAG, "Fetched " + allMessages.size() + " messages for conversation sync");
                } catch (Exception e) {
                    Log.w(TAG, "Could not fetch all messages", e);
                }
                
                java.util.Map<String, List<SmsEntity>> messagesByKey = new java.util.HashMap<>();
                for (SmsEntity message : allMessages) {
                    String key = buildConversationKey(message);
                    messagesByKey.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(message);
                }
                
                int syncedCount = 0;
                for (java.util.Map.Entry<String, List<SmsEntity>> entry : messagesByKey.entrySet()) {
                    String phoneNumber = entry.getKey();
                    List<SmsEntity> messages = entry.getValue();

                    if (messages.isEmpty()) {
                        continue;
                    }

                    ConversationEntity conversation = createConversationFromMessages(phoneNumber, messages);
                    conversation.updatedAt = System.currentTimeMillis();

                    ConversationEntity existing = null;
                    try {
                        if (conversation.threadId != null && conversation.threadId > 0) {
                            existing = conversationDao.getConversationByThreadId(conversation.threadId).blockingGet();
                        }
                    } catch (Exception ignored) {
                    }
                    if (existing == null) {
                        try {
                            if (conversation.phoneNumber != null && !conversation.phoneNumber.trim().isEmpty()) {
                                existing = conversationDao.getConversationByPhoneNumber(conversation.phoneNumber).blockingGet();
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (existing != null) {
                        conversation.id = existing.id;
                        conversation.isPinned = existing.isPinned;
                        conversation.isArchived = existing.isArchived;
                        conversation.createdAt = existing.createdAt;
                        if (conversation.contactName == null || conversation.contactName.trim().isEmpty()) {
                            conversation.contactName = existing.contactName;
                        }
                        if (conversation.contactPhotoUri == null || conversation.contactPhotoUri.trim().isEmpty()) {
                            conversation.contactPhotoUri = existing.contactPhotoUri;
                        }
                        conversationDao.updateConversation(conversation).blockingAwait();
                    } else {
                        conversationDao.insertConversation(conversation).blockingAwait();
                    }

                    syncedCount++;
                    Log.d(TAG, "Synced conversation for " + phoneNumber + " with " + messages.size() + " messages");
                }

                Log.d(TAG, "Synced " + syncedCount + " conversations");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync conversations", e);
                _errorState.postValue("Failed to sync conversations: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Helper methods
     */
    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized != null && !normalized.isEmpty()) {
            return normalized;
        }
        return phoneNumber != null ? phoneNumber.trim() : null;
    }
    
    private String truncateMessage(String message) {
        if (message == null) return "";
        return message.length() > 50 ? message.substring(0, 47) + "..." : message;
    }
    
    private String getMessageType(SmsEntity message) {
        if (message.boxType == 2) { // Sent
            return "SENT";
        } else if (message.boxType == 1) { // Inbox
            return "INBOX";
        } else {
            return "OTHER";
        }
    }
    
    private ConversationEntity createConversationFromMessages(String phoneNumber, List<SmsEntity> messages) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.phoneNumber = phoneNumber;
        if (messages != null && !messages.isEmpty()) {
            conversation.threadId = messages.get(0).threadId;
        }
        
        // Sort messages by date
        messages.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        
        // Get latest message
        SmsEntity latestMessage = messages.get(0);
        conversation.lastMessageTime = latestMessage.getDate();
        conversation.lastMessagePreview = truncateMessage(latestMessage.getBody());
        conversation.lastMessageType = getMessageType(latestMessage);
        
        // Count messages and unread
        conversation.messageCount = messages.size();
        conversation.unreadCount = (int) messages.stream().filter(SmsEntity::isUnread).count();
        
        // Resolve contact info (even when conversation key is thread-based)
        try {
            String resolvedPhone = null;
            if (messages != null) {
                for (SmsEntity msg : messages) {
                    if (msg != null && msg.phoneNumber != null && !msg.phoneNumber.trim().isEmpty()) {
                        String candidate = PhoneNumberUtils.normalizePhoneNumber(msg.phoneNumber);
                        if (candidate != null && !candidate.trim().isEmpty()) {
                            resolvedPhone = candidate;
                            break;
                        }
                    }
                }
            }

            String lookup = resolvedPhone;
            if (lookup == null && phoneNumber != null && !phoneNumber.startsWith("thread:")) {
                lookup = phoneNumber;
            }

            if (lookup != null) {
                String contactName = contactResolver.getContactName(lookup);
                if (contactName != null && !contactName.trim().isEmpty()) {
                    if (!contactName.equals(lookup)) {
                        conversation.contactName = contactName;
                    } else {
                        conversation.contactName = PhoneNumberUtils.formatForDisplay(lookup);
                    }
                }

                Uri photoUri = contactResolver.getContactPhotoUri(lookup);
                if (photoUri != null) {
                    conversation.contactPhotoUri = photoUri.toString();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve contact info during sync", e);
        }
        
        return conversation;
    }

    private String buildConversationKey(SmsEntity message) {
        if (message == null) return "unknown";
        if (message.threadId != null && message.threadId > 0) {
            return "thread:" + message.threadId;
        }
        String phone = normalizePhoneNumber(message.phoneNumber);
        return phone != null ? phone : "unknown";
    }
    
    /**
     * Cleanup resources - properly shutdown ExecutorService
     * Call this when the repository is no longer needed (e.g., app shutdown)
     */
    public void shutdown() {
        try {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                Log.d(TAG, "ConversationRepository ExecutorService shutdown completed");
                
                // Wait for tasks to complete if needed
                if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, "ExecutorService did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while waiting for ExecutorService shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if ExecutorService is shutdown
     */
    public boolean isShutdown() {
        return executor != null && executor.isShutdown();
    }
    
    // Flow methods for coroutines support in OptimizedInboxViewModel
    
    /**
     * Get all conversations as Flow for coroutines
     */
    public Flowable<PagingSource<Integer, ConversationEntity>> getAllConversationsPagedFlow() {
        return Flowable.fromCallable(() -> getAllConversationsPaged())
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get active conversations as Flow for coroutines
     */
    public Flowable<PagingSource<Integer, ConversationEntity>> getActiveConversationsPagedFlow() {
        return Flowable.fromCallable(() -> getActiveConversationsPaged())
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Get unread conversations as Flow for coroutines
     */
    public Flowable<PagingSource<Integer, ConversationEntity>> getUnreadConversationsPagedFlow() {
        return Flowable.fromCallable(() -> getUnreadConversationsPaged())
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Search conversations as Flow for coroutines
     */
    public Flowable<PagingSource<Integer, ConversationEntity>> searchConversationsFlow(String query) {
        return Flowable.fromCallable(() -> searchConversations(query))
            .subscribeOn(Schedulers.io());
    }
    
    /**
     * Mark conversation as read as Flow for coroutines
     */
    public Flowable<Void> markConversationAsReadFlow(String conversationId) {
        return Flowable.fromCallable(() -> {
            // conversationDao.markConversationAsRead expects a phone number string
            conversationDao.markConversationAsRead(conversationId).blockingAwait();
            return (Void) null;
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Delete conversation as Flow for coroutines
     */
    public Flowable<Void> deleteConversationFlow(ConversationEntity conversation) {
        return Flowable.fromCallable(() -> {
            conversationDao.deleteConversation(conversation).blockingAwait();
            return (Void) null;
        }).subscribeOn(Schedulers.io());
    }
}
