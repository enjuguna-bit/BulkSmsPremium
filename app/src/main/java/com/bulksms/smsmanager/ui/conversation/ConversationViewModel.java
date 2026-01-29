package com.bulksms.smsmanager.ui.conversation;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.entity.ConversationEntity;
import com.bulksms.smsmanager.data.repository.SmsRepository;
import com.bulksms.smsmanager.data.repository.ConversationRepository;
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
        executor.execute(() -> {
            try {
                _uiState.setValue(ConversationUiState.LOADING);
                
                // Get or create conversation
                ConversationEntity conv = conversationRepository.getOrCreateConversation(phoneNumber);
                _conversation.postValue(conv);
                
                // Load messages for this phone number
                java.util.List<SmsEntity> messageList = smsRepository.getMessagesByPhoneNumber(phoneNumber);
                _messages.postValue(messageList);
                
                // Mark conversation as read
                conversationRepository.markConversationAsRead(phoneNumber);
                
                _uiState.setValue(ConversationUiState.success("Conversation loaded"));
                
            } catch (Exception e) {
                _uiState.setValue(ConversationUiState.error("Failed to load conversation: " + e.getMessage()));
            }
        });
    }
    
    public void sendMessage(String messageText) {
        ConversationEntity currentConv = _conversation.getValue();
        if (currentConv == null || messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        
        executor.execute(() -> {
            try {
                _uiState.setValue(ConversationUiState.SENDING);
                
                // Create and send SMS
                SmsEntity sms = new SmsEntity();
                sms.phoneNumber = currentConv.phoneNumber;
                sms.message = messageText.trim();
                sms.status = "PENDING";
                sms.createdAt = System.currentTimeMillis();
                sms.isRead = true;
                
                smsRepository.sendSms(sms);
                
                // Update conversation
                conversationRepository.updateConversationWithNewMessage(
                    currentConv.phoneNumber,
                    sms.createdAt,
                    messageText.trim(),
                    "SENT",
                    false, // not incoming
                    System.currentTimeMillis()
                );
                
                // Reload messages
                java.util.List<SmsEntity> messageList = smsRepository.getMessagesByPhoneNumber(currentConv.phoneNumber);
                _messages.postValue(messageList);
                
                _uiState.setValue(ConversationUiState.success("Message sent"));
                
            } catch (Exception e) {
                _uiState.setValue(ConversationUiState.error("Failed to send message: " + e.getMessage()));
            }
        });
    }
    
    public void deleteMessage(SmsEntity message) {
        executor.execute(() -> {
            try {
                smsRepository.deleteMessage(message);
                
                // Reload messages
                ConversationEntity currentConv = _conversation.getValue();
                if (currentConv != null) {
                    java.util.List<SmsEntity> messageList = smsRepository.getMessagesByPhoneNumber(currentConv.phoneNumber);
                    _messages.postValue(messageList);
                }
                
                _uiState.setValue(ConversationUiState.success("Message deleted"));
                
            } catch (Exception e) {
                _uiState.setValue(ConversationUiState.error("Failed to delete message: " + e.getMessage()));
            }
        });
    }
    
    public void markAsUnread(SmsEntity message) {
        executor.execute(() -> {
            try {
                smsRepository.markAsUnread(message.id);
                _uiState.setValue(ConversationUiState.success("Marked as unread"));
            } catch (Exception e) {
                _uiState.setValue(ConversationUiState.error("Failed to mark as unread: " + e.getMessage()));
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
