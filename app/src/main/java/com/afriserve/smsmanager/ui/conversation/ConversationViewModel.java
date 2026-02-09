package com.afriserve.smsmanager.ui.conversation;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltViewModel
public class ConversationViewModel extends ViewModel {
    
    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // UI State
    private final MutableLiveData<ConversationUiState> _uiState = new MutableLiveData<>();
    public final LiveData<ConversationUiState> uiState = _uiState;
    
    // Current conversation
    private final MutableLiveData<ConversationEntity> _conversation = new MutableLiveData<>();
    public final LiveData<ConversationEntity> conversation = _conversation;
    
    // Messages for current conversation
    private final MutableLiveData<java.util.List<SmsEntity>> _messages = new MutableLiveData<>();
    public final LiveData<java.util.List<SmsEntity>> messages = _messages;
    
    @Inject
    public ConversationViewModel(SmsRepository smsRepository, ConversationRepository conversationRepository) {
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
        
        // Initialize with empty messages list
        _messages.setValue(new java.util.ArrayList<>());
        
        _uiState.setValue(ConversationUiState.LOADING);
    }
    
    public void loadConversation(String phoneNumber) {
        loadConversation(phoneNumber, null);
    }

    public void loadConversation(String phoneNumber, Long threadId) {
        executor.execute(() -> {
            try {
                _uiState.postValue(ConversationUiState.LOADING);

                // Get or create conversation
                ConversationEntity conv = conversationRepository.getOrCreateConversation(threadId, phoneNumber);
                _conversation.postValue(conv);

                // Load messages for this thread/phone
                java.util.List<SmsEntity> messageList = threadId != null && threadId > 0
                    ? smsRepository.getMessagesByThreadId(threadId)
                    : smsRepository.getMessagesByPhoneNumber(phoneNumber);
                _messages.postValue(messageList);

                // Mark conversation as read
                conversationRepository.markConversationAsRead(phoneNumber).blockingAwait();

                _uiState.postValue(ConversationUiState.success("Conversation loaded"));

            } catch (Exception e) {
                _uiState.postValue(ConversationUiState.error("Failed to load conversation: " + e.getMessage()));
            }
        });
    }
    
    public void sendMessage(String messageText) {
        sendMessage(messageText, -1);
    }

    public void sendMessage(String messageText, int simSlot) {
        ConversationEntity currentConv = _conversation.getValue();
        if (currentConv == null || messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        
        executor.execute(() -> {
            try {
                _uiState.postValue(ConversationUiState.SENDING);
                
                // Create and send SMS
                SmsEntity sms = new SmsEntity();
                sms.phoneNumber = currentConv.phoneNumber;
                sms.message = messageText.trim();
                sms.status = "PENDING";
                sms.createdAt = System.currentTimeMillis();
                sms.isRead = true;
                sms.threadId = currentConv.threadId;
                
                smsRepository.sendSms(sms, simSlot).blockingAwait();
                
                // Update conversation
                conversationRepository.updateConversationWithNewMessage(
                    currentConv.threadId,
                    currentConv.phoneNumber,
                    sms.createdAt,
                    messageText.trim(),
                    "SENT",
                    false, // not incoming
                    System.currentTimeMillis()
                ).blockingAwait();
                
                // Reload messages
                java.util.List<SmsEntity> messageList = currentConv.threadId != null && currentConv.threadId > 0
                    ? smsRepository.getMessagesByThreadId(currentConv.threadId)
                    : smsRepository.getMessagesByPhoneNumber(currentConv.phoneNumber);
                _messages.postValue(messageList);
                
                _uiState.postValue(ConversationUiState.success("Message sent"));
                
            } catch (Exception e) {
                _uiState.postValue(ConversationUiState.error("Failed to send message: " + e.getMessage()));
            }
        });
    }
    
    public void deleteMessage(SmsEntity message) {
        executor.execute(() -> {
            try {
                smsRepository.deleteMessage(message).blockingAwait();
                
                // Reload messages
                ConversationEntity currentConv = _conversation.getValue();
                if (currentConv != null) {
                    java.util.List<SmsEntity> messageList = currentConv.threadId != null && currentConv.threadId > 0
                        ? smsRepository.getMessagesByThreadId(currentConv.threadId)
                        : smsRepository.getMessagesByPhoneNumber(currentConv.phoneNumber);
                    _messages.postValue(messageList);
                }
                
                _uiState.postValue(ConversationUiState.success("Message deleted"));
                
            } catch (Exception e) {
                _uiState.postValue(ConversationUiState.error("Failed to delete message: " + e.getMessage()));
            }
        });
    }
    
    public void markAsUnread(SmsEntity message) {
        executor.execute(() -> {
            try {
                smsRepository.markAsUnread(message.id).blockingAwait();
                _uiState.postValue(ConversationUiState.success("Marked as unread"));
            } catch (Exception e) {
                _uiState.postValue(ConversationUiState.error("Failed to mark as unread: " + e.getMessage()));
            }
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
    
    // UI State enum
    public enum ConversationUiState {
        LOADING("Loading"),
        SENDING("Sending"),
        SUCCESS("Success"),
        ERROR("Error");
        
        private final String message;
        
        ConversationUiState(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
        
        public static ConversationUiState success(String customMessage) {
            // Create a SUCCESS state with custom message
            // Note: For more flexibility, consider using a data class instead of enum
            return SUCCESS;
        }
        
        public static ConversationUiState error(String customMessage) {
            // Create an ERROR state with custom message
            // Note: For more flexibility, consider using a data class instead of enum
            return ERROR;
        }
    }
}
