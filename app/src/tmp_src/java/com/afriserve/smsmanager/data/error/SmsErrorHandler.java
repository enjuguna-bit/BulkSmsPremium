package com.afriserve.smsmanager.data.error;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afriserve.smsmanager.R;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Centralized error handling for SMS operations
 * Provides user-friendly error messages and logging
 */
@Singleton
public class SmsErrorHandler {
    
    private static final String TAG = "SmsErrorHandler";
    
    private final Context context;
    
    @Inject
    public SmsErrorHandler(@ApplicationContext Context context) {
        this.context = context;
    }
    
    /**
     * Handle SMS sending errors and return user-friendly messages
     */
    @NonNull
    public ErrorInfo handleSmsError(@NonNull Exception exception, @Nullable String phoneNumber) {
        Log.e(TAG, "SMS error occurred", exception);
        
        String errorType = classifyError(exception);
        String userMessage = getUserFriendlyMessage(errorType, exception);
        String technicalMessage = exception.getMessage();
        
        return new ErrorInfo(errorType, userMessage, technicalMessage, phoneNumber);
    }
    
    /**
     * Classify the type of error for better handling
     */
    @NonNull
    private String classifyError(@NonNull Exception exception) {
        String className = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        
        if (message != null) {
            if (message.contains("No service") || message.contains("Service not available")) {
                return "NO_SERVICE";
            } else if (message.contains("Airplane mode") || message.contains("FLIGHT_MODE")) {
                return "AIRPLANE_MODE";
            } else if (message.contains("SMS limit") || message.contains("Too many SMS")) {
                return "RATE_LIMIT";
            } else if (message.contains("Invalid number") || message.contains("Invalid address")) {
                return "INVALID_NUMBER";
            } else if (message.contains("Permission denied") || message.contains("Security exception")) {
                return "PERMISSION_DENIED";
            } else if (message.contains("Network error") || message.contains("Connection failed")) {
                return "NETWORK_ERROR";
            } else if (message.contains("Memory") || message.contains("Out of memory")) {
                return "MEMORY_ERROR";
            }
        }
        
        switch (className) {
            case "SecurityException":
                return "PERMISSION_DENIED";
            case "IllegalArgumentException":
                return "INVALID_NUMBER";
            case "NullPointerException":
                return "NULL_POINTER";
            case "OutOfMemoryError":
                return "MEMORY_ERROR";
            default:
                return "UNKNOWN_ERROR";
        }
    }
    
    /**
     * Get user-friendly error message
     */
    @NonNull
    private String getUserFriendlyMessage(@NonNull String errorType, @NonNull Exception exception) {
        switch (errorType) {
            case "NO_SERVICE":
                return context.getString(R.string.error_no_service);
            case "AIRPLANE_MODE":
                return context.getString(R.string.error_airplane_mode);
            case "RATE_LIMIT":
                return context.getString(R.string.error_rate_limit);
            case "INVALID_NUMBER":
                return context.getString(R.string.error_invalid_number);
            case "PERMISSION_DENIED":
                return context.getString(R.string.error_permission_denied);
            case "NETWORK_ERROR":
                return context.getString(R.string.error_network);
            case "MEMORY_ERROR":
                return context.getString(R.string.error_memory);
            default:
                return context.getString(R.string.error_unknown);
        }
    }
    
    /**
     * Check if error is recoverable (can be retried)
     */
    public boolean isRecoverableError(@NonNull String errorType) {
        switch (errorType) {
            case "NO_SERVICE":
            case "NETWORK_ERROR":
            case "RATE_LIMIT":
                return true;
            case "AIRPLANE_MODE":
            case "INVALID_NUMBER":
            case "PERMISSION_DENIED":
            case "MEMORY_ERROR":
            case "UNKNOWN_ERROR":
            default:
                return false;
        }
    }
    
    /**
     * Get suggested action for the user
     */
    @NonNull
    public String getSuggestedAction(@NonNull String errorType) {
        switch (errorType) {
            case "NO_SERVICE":
                return context.getString(R.string.action_check_signal);
            case "AIRPLANE_MODE":
                return context.getString(R.string.action_disable_airplane);
            case "RATE_LIMIT":
                return context.getString(R.string.action_wait_retry);
            case "INVALID_NUMBER":
                return context.getString(R.string.action_check_number);
            case "PERMISSION_DENIED":
                return context.getString(R.string.action_grant_permissions);
            case "NETWORK_ERROR":
                return context.getString(R.string.action_check_connection);
            case "MEMORY_ERROR":
                return context.getString(R.string.action_free_memory);
            default:
                return context.getString(R.string.action_contact_support);
        }
    }
    
    /**
     * Error information data class
     */
    public static class ErrorInfo {
        @NonNull public final String errorType;
        @NonNull public final String userMessage;
        @Nullable public final String technicalMessage;
        @Nullable public final String phoneNumber;
        
        public ErrorInfo(@NonNull String errorType, @NonNull String userMessage, 
                        @Nullable String technicalMessage, @Nullable String phoneNumber) {
            this.errorType = errorType;
            this.userMessage = userMessage;
            this.technicalMessage = technicalMessage;
            this.phoneNumber = phoneNumber;
        }
        
        @NonNull
        public String getDetailedMessage() {
            StringBuilder sb = new StringBuilder(userMessage);
            if (technicalMessage != null) {
                sb.append(" (").append(technicalMessage).append(")");
            }
            return sb.toString();
        }
    }
}
