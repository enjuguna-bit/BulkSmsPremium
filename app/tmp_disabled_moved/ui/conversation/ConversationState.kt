package com.bulksms.smsmanager.ui.conversation

import com.bulksms.smsmanager.data.entity.ConversationEntity
import com.bulksms.smsmanager.data.entity.SmsEntity

/**
 * Sealed class hierarchy for conversation UI state management
 * Provides comprehensive state information with loading and error states
 */
sealed class ConversationUiState {
    
    /**
     * Initial loading state
     */
    object Loading : ConversationUiState()
    
    /**
     * Successfully loaded conversation
     */
    data class Success(
        val conversation: ConversationEntity,
        val isRefreshing: Boolean = false
    ) : ConversationUiState()
    
    /**
     * Error state with detailed error information
     */
    data class Error(
        val errorType: ErrorType,
        val message: String,
        val canRetry: Boolean = true
    ) : ConversationUiState()
}

/**
 * Sealed class for individual operation states
 */
sealed class OperationState {
    
    /**
     * Idle state - no operation in progress
     */
    object Idle : OperationState()
    
    /**
     * Operation in progress
     */
    object Loading : OperationState()
    
    /**
     * Operation completed successfully
     */
    data class Success(
        val message: String
    ) : OperationState()
    
    /**
     * Operation failed
     */
    data class Error(
        val errorType: ErrorType,
        val message: String,
        val canRetry: Boolean = true
    ) : OperationState()
}

/**
 * Sealed class for message update events
 */
sealed class MessageUpdate {
    
    /**
     * New message sent
     */
    data class MessageSent(
        val message: SmsEntity
    ) : MessageUpdate()
    
    /**
     * Message deleted
     */
    data class MessageDeleted(
        val message: SmsEntity
    ) : MessageUpdate()
    
    /**
     * Message marked as read
     */
    data class MessageRead(
        val messageId: Long
    ) : MessageUpdate()
    
    /**
     * Message status updated
     */
    data class MessageStatusUpdated(
        val messageId: Long,
        val newStatus: String
    ) : MessageUpdate()
    
    /**
     * New message received
     */
    data class MessageReceived(
        val message: SmsEntity
    ) : MessageUpdate()
    
    /**
     * Conversation updated
     */
    data class ConversationUpdated(
        val conversation: ConversationEntity
    ) : MessageUpdate()
}

/**
 * Sealed class for error events
 */
sealed class ErrorEvent {
    
    /**
     * Conversation load failed
     */
    data class ConversationLoadFailed(
        val errorType: ErrorType,
        val cause: Throwable
    ) : ErrorEvent()
    
    /**
     * Send message failed
     */
    data class SendMessageFailed(
        val errorType: ErrorType,
        val cause: Throwable
    ) : ErrorEvent()
    
    /**
     * Delete message failed
     */
    data class DeleteMessageFailed(
        val errorType: ErrorType,
        val cause: Throwable
    ) : ErrorEvent()
    
    /**
     * Mark as read failed
     */
    data class MarkAsReadFailed(
        val errorType: ErrorType,
        val cause: Throwable
    ) : ErrorEvent()
    
    /**
     * Network error
     */
    data class NetworkError(
        val errorType: ErrorType,
        val cause: Throwable
    ) : ErrorEvent()
    
    /**
     * Permission error
     */
    data class PermissionError(
        val permission: String,
        val cause: Throwable
    ) : ErrorEvent()
}

/**
 * Error type enumeration
 */
enum class ErrorType {
    /**
     * Transient network or timeout errors - can be retried
     */
    TRANSIENT,
    
    /**
     * Permission related errors - user action required
     */
    PERMISSION,
    
    /**
     * Validation errors - invalid input data
     */
    VALIDATION,
    
    /**
     * Network connectivity issues
     */
    NETWORK,
    
    /**
     * Database errors
     */
    DATABASE,
    
    /**
     * Unknown or unexpected errors
     */
    UNKNOWN
}

/**
 * Extension functions for error type classification
 */
fun ErrorType.canRetry(): Boolean {
    return when (this) {
        ErrorType.TRANSIENT, ErrorType.NETWORK -> true
        ErrorType.PERMISSION, ErrorType.VALIDATION, ErrorType.DATABASE, ErrorType.UNKNOWN -> false
    }
}

fun ErrorType.isUserActionRequired(): Boolean {
    return when (this) {
        ErrorType.PERMISSION -> true
        ErrorType.TRANSIENT, ErrorType.VALIDATION, ErrorType.NETWORK, ErrorType.DATABASE, ErrorType.UNKNOWN -> false
    }
}

fun ErrorType.getDisplayMessage(): String {
    return when (this) {
        ErrorType.TRANSIENT -> "Temporary connection issue"
        ErrorType.PERMISSION -> "Permission required"
        ErrorType.VALIDATION -> "Invalid input"
        ErrorType.NETWORK -> "Network connection issue"
        ErrorType.DATABASE -> "Data storage issue"
        ErrorType.UNKNOWN -> "An error occurred"
    }
}
