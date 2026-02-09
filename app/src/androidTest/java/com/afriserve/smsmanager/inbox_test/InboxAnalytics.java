package com.afriserve.smsmanager.ui.inbox;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.LoadState;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import java.util.HashMap;
import java.util.Map;

/**
 * Analytics helper for tracking inbox performance and errors
 */
public class InboxAnalytics {
    
    private static final String TAG = "InboxAnalytics";
    
    public enum ErrorType {
        LOAD_STATE_ERROR("load_state_error"),
        CONVERSATION_OPEN_ERROR("conversation_open_error"),
        SYNC_ERROR("sync_error"),
        DATABASE_ERROR("database_error"),
        NETWORK_ERROR("network_error");
        
        private final String value;
        
        ErrorType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    public enum LoadStateType {
        REFRESH("refresh"),
        PREPEND("prepend"),
        APPEND("append");
        
        private final String value;
        
        LoadStateType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    private final Context context;
    
    public InboxAnalytics(Context context) {
        this.context = context;
    }
    
    /**
     * Track load state errors with detailed context
     */
    public void trackLoadStateError(@NonNull LoadStateType loadStateType, 
                                   @NonNull LoadState.Error errorState,
                                   @Nullable String additionalContext) {
        Throwable error = errorState.getError();
        
        Map<String, String> parameters = new HashMap<>();
        parameters.put("load_state_type", loadStateType.getValue());
        parameters.put("error_class", error.getClass().getSimpleName());
        parameters.put("error_message", error.getMessage() != null ? error.getMessage() : "Unknown error");
        
        if (additionalContext != null) {
            parameters.put("context", additionalContext);
        }
        
        // Log detailed error information
        Log.e(TAG, "LoadState Error [" + loadStateType.getValue() + "]: " + 
              error.getMessage(), error);
        
        // In a real implementation, you would send this to your analytics service
        // For example: FirebaseAnalytics.getInstance(context).logEvent("load_state_error", parameters);
        
        // For now, we'll just log it
        logAnalyticsEvent(ErrorType.LOAD_STATE_ERROR.getValue(), parameters);
    }
    
    /**
     * Track conversation open errors
     */
    public void trackConversationOpenError(@Nullable ConversationEntity conversation, 
                                         @NonNull Throwable error) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("error_class", error.getClass().getSimpleName());
        parameters.put("error_message", error.getMessage() != null ? error.getMessage() : "Unknown error");
        
        if (conversation != null) {
            parameters.put("conversation_id", String.valueOf(conversation.id));
            parameters.put("phone_number", conversation.phoneNumber != null ? conversation.phoneNumber : "null");
            parameters.put("message_count", String.valueOf(conversation.messageCount));
        } else {
            parameters.put("conversation_id", "null");
        }
        
        Log.e(TAG, "Conversation Open Error: " + error.getMessage(), error);
        logAnalyticsEvent(ErrorType.CONVERSATION_OPEN_ERROR.getValue(), parameters);
    }
    
    /**
     * Track sync errors
     */
    public void trackSyncError(@NonNull Throwable error, @Nullable String syncType) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("error_class", error.getClass().getSimpleName());
        parameters.put("error_message", error.getMessage() != null ? error.getMessage() : "Unknown error");
        
        if (syncType != null) {
            parameters.put("sync_type", syncType);
        }
        
        Log.e(TAG, "Sync Error: " + error.getMessage(), error);
        logAnalyticsEvent(ErrorType.SYNC_ERROR.getValue(), parameters);
    }
    
    /**
     * Track successful operations
     */
    public void trackLoadStateSuccess(@NonNull LoadStateType loadStateType, int itemCount) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("load_state_type", loadStateType.getValue());
        parameters.put("item_count", String.valueOf(itemCount));
        
        Log.d(TAG, "LoadState Success [" + loadStateType.getValue() + "]: " + itemCount + " items");
        logAnalyticsEvent("load_state_success", parameters);
    }
    
    /**
     * Track user interactions
     */
    public void trackUserInteraction(@NonNull String action, @Nullable Map<String, String> parameters) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        
        Log.d(TAG, "User Interaction: " + action);
        logAnalyticsEvent("user_interaction_" + action, parameters);
    }
    
    /**
     * Track performance metrics
     */
    public void trackPerformance(@NonNull String operation, long durationMs) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("operation", operation);
        parameters.put("duration_ms", String.valueOf(durationMs));
        
        if (durationMs > 5000) { // Log slow operations
            Log.w(TAG, "Slow Operation [" + operation + "]: " + durationMs + "ms");
        }
        
        logAnalyticsEvent("performance", parameters);
    }
    
    /**
     * Generic analytics event logging
     */
    private void logAnalyticsEvent(@NonNull String eventName, @NonNull Map<String, String> parameters) {
        // In a real implementation, this would send to your analytics service
        // For now, we'll just log to console for debugging
        
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Analytics Event: ").append(eventName);
        
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            logMessage.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        
        Log.d(TAG, logMessage.toString());
    }
    
    /**
     * Get user-friendly error message for common errors
     */
    @NonNull
    public String getUserFriendlyErrorMessage(@NonNull Throwable error) {
        String errorMessage = error.getMessage();
        
        if (errorMessage == null) {
            return "An unexpected error occurred. Please try again.";
        }
        
        // Common error patterns
        if (errorMessage.toLowerCase().contains("network") || 
            errorMessage.toLowerCase().contains("connection")) {
            return "Network error. Please check your internet connection and try again.";
        }
        
        if (errorMessage.toLowerCase().contains("timeout")) {
            return "Request timed out. Please try again.";
        }
        
        if (errorMessage.toLowerCase().contains("permission")) {
            return "Permission denied. Please check app permissions and try again.";
        }
        
        if (errorMessage.toLowerCase().contains("database") || 
            errorMessage.toLowerCase().contains("sql")) {
            return "Data storage error. Please restart the app and try again.";
        }
        
        // Return the original message if it's reasonably short
        if (errorMessage.length() <= 100) {
            return errorMessage;
        }
        
        return "An error occurred. Please try again.";
    }
}
