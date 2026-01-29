package com.bulksms.smsmanager.data.repository;

import android.util.Log;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PagingSource;

import com.bulksms.smsmanager.data.dao.ConversationDao;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.contacts.ContactResolver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
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
                
                // Try to get existing conversation
                ConversationEntity conversation = null;
                try {
                    conversation = conversationDao.getConversationByPhoneNumber(phoneNumber).blockingGet();
                } catch (Exception e) {
                    // Conversation doesn't exist yet
                }
                
                if (conversation == null) {
                    // Create new conversation
                    conversation = new ConversationEntity();
                    conversation.phoneNumber = phoneNumber;
                    conversation.messageCount = 1;
                    conversation.unreadCount = message.isUnread() ? 1 : 0;
                } else {
                    // Update existing conversation
                    conversation.messageCount++;
                    if (message.isUnread()) {
                        conversation.unreadCount++;
                    }
                }
                
                // Update conversation details
                conversation.lastMessageTime = message.getDate();
                conversation.lastMessagePreview = truncateMessage(message.getBody());
                conversation.lastMessageType = getMessageType(message);
                conversation.updatedAt = System.currentTimeMillis();
                
                // Resolve contact name and photo
                try {
                    String contactName = contactResolver.getContactName(phoneNumber);
                    if (!contactName.equals(phoneNumber)) {
                        conversation.contactName = contactName;
                    }
                    
                    Uri photoUri = contactResolver.getContactPhotoUri(phoneNumber);
                    if (photoUri != null) {
                        conversation.contactPhotoUri = photoUri.toString();
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
     * Get or create conversation by phone number
     * This method retrieves an existing conversation or creates a new one
     */
    public ConversationEntity getOrCreateConversation(String phoneNumber) {
        String normalized = normalizePhoneNumber(phoneNumber);
        
        try {
            // Try to get existing conversation
            return conversationDao.getConversationByPhoneNumber(normalized).blockingGet();
        } catch (Exception e) {
            // Create new conversation if it doesn't exist
            Log.d(TAG, "Creating new conversation for: " + normalized);
            ConversationEntity conversation = new ConversationEntity();
            conversation.phoneNumber = normalized;
            conversation.messageCount = 0;
            conversation.unreadCount = 0;
            conversation.createdAt = System.currentTimeMillis();
            conversation.updatedAt = System.currentTimeMillis();
            
            // Try to resolve contact info
            try {
                String contactName = contactResolver.getContactName(normalized);
                if (!contactName.equals(normalized)) {
                    conversation.contactName = contactName;
                }
                
                Uri photoUri = contactResolver.getContactPhotoUri(normalized);
                if (photoUri != null) {
                    conversation.contactPhotoUri = photoUri.toString();
                }
            } catch (Exception contactError) {
                Log.w(TAG, "Failed to resolve contact info", contactError);
            }
            
            // Insert and return
            conversationDao.insertConversation(conversation).blockingAwait();
            return conversation;
        }
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
                ConversationEntity conversation = getOrCreateConversation(normalized);
                
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
                // Delete all messages in conversation by querying and deleting them
                // Get all messages for this phone number
                List<SmsEntity> messages = new java.util.ArrayList<>();
                try {
                    messages.addAll(smsDao.getRecentSmsByStatus("SENT", 1000).blockingGet());
                    messages.addAll(smsDao.getRecentSmsByStatus("DELIVERED", 1000).blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Could not fetch messages for phone", e);
                }
                
                // Filter messages by phone number and delete
                List<SmsEntity> messagesToDelete = new java.util.ArrayList<>();
                for (SmsEntity msg : messages) {
                    if (conversation.phoneNumber.equals(msg.phoneNumber)) {
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
                
                // Clear existing conversations
                conversationDao.deleteAllConversations().blockingAwait();
                
                // Get all messages and create conversations
                // In a real implementation, this would be more efficient with proper SQL queries
                // Get all SMS with any status to process them
                List<SmsEntity> allMessages = new java.util.ArrayList<>();
                try {
                    allMessages.addAll(smsDao.getRecentSmsByStatus("DELIVERED", 10000).blockingGet());
                    allMessages.addAll(smsDao.getRecentSmsByStatus("SENT", 10000).blockingGet());
                    allMessages.addAll(smsDao.getRecentSmsByStatus("PENDING", 10000).blockingGet());
                } catch (Exception e) {
                    Log.w(TAG, "Could not fetch all messages", e);
                }
                
                java.util.Map<String, List<SmsEntity>> messagesByPhone = new java.util.HashMap<>();
                for (SmsEntity message : allMessages) {
                    String phone = normalizePhoneNumber(message.phoneNumber);
                    messagesByPhone.computeIfAbsent(phone, k -> new java.util.ArrayList<>()).add(message);
                }
                
                // Create conversations
                List<ConversationEntity> conversations = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, List<SmsEntity>> entry : messagesByPhone.entrySet()) {
                    String phoneNumber = entry.getKey();
                    List<SmsEntity> messages = entry.getValue();
                    
                    if (!messages.isEmpty()) {
                        ConversationEntity conversation = createConversationFromMessages(phoneNumber, messages);
                        conversations.add(conversation);
                    }
                }
                
                // Insert all conversations
                if (!conversations.isEmpty()) {
                    conversationDao.insertConversations(conversations).blockingAwait();
                }
                
                Log.d(TAG, "Synced " + conversations.size() + " conversations");
                
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
        if (phoneNumber == null) return "";
        return phoneNumber.replaceAll("[^0-9+]", "");
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
        
        // Resolve contact info
        try {
            String contactName = contactResolver.getContactName(phoneNumber);
            if (!contactName.equals(phoneNumber)) {
                conversation.contactName = contactName;
            }
            
            Uri photoUri = contactResolver.getContactPhotoUri(phoneNumber);
            if (photoUri != null) {
                conversation.contactPhotoUri = photoUri.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve contact info during sync", e);
        }
        
        return conversation;
    }
}
