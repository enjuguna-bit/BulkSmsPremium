package com.afriserve.smsmanager.data.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.repository.SmsSearchRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;

/**
 * Error recovery manager with exponential backoff and retry mechanisms
 * Handles sync failures and provides user-friendly error states
 */
@Singleton
public class ErrorRecoveryManager {
    
    private static final String TAG = "ErrorRecoveryManager";
    
    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;
    private final SmsSearchRepository searchRepository;
    private final Context context;
    
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // Error recovery state
    private final MutableLiveData<RecoveryState> _recoveryState = new MutableLiveData<>(RecoveryState.IDLE);
    public final LiveData<RecoveryState> recoveryState = _recoveryState;
    
    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY = 5000; // 5 seconds
    private static final long MAX_RETRY_DELAY = 300000; // 5 minutes
    
    // Retry tracking
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private long lastErrorTime = 0;
    private String lastErrorMessage = "";
    
    // Offline queue for pending operations
    private final java.util.Queue<PendingOperation> pendingOperations = new java.util.concurrent.ConcurrentLinkedQueue<>();
    
    @Inject
    public ErrorRecoveryManager(
            SmsRepository smsRepository,
            ConversationRepository conversationRepository,
            SmsSearchRepository searchRepository,
            @ApplicationContext Context context) {
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
        this.searchRepository = searchRepository;
        this.context = context;
        
        Log.d(TAG, "ErrorRecoveryManager initialized");
    }
    
    /**
     * Handle sync error with recovery strategy
     */
    public void handleSyncError(String errorMessage, Throwable error) {
        lastErrorTime = System.currentTimeMillis();
        lastErrorMessage = errorMessage;
        
        int currentRetryCount = retryCount.incrementAndGet();
        
        Log.e(TAG, "Sync error occurred (attempt " + currentRetryCount + "): " + errorMessage, error);
        
        if (currentRetryCount >= MAX_RETRY_ATTEMPTS) {
            _recoveryState.postValue(RecoveryState.FAILED);
            Log.e(TAG, "Max retry attempts reached, giving up");
            return;
        }
        
        // Calculate retry delay with exponential backoff
        long retryDelay = calculateRetryDelay(currentRetryCount);
        
        _recoveryState.postValue(RecoveryState.RETRYING);
        
        Log.d(TAG, "Scheduling retry in " + retryDelay + "ms");
        
        // Schedule retry with exponential backoff
        disposables.add(
            io.reactivex.rxjava3.core.Observable.timer(retryDelay, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ignored -> {
                        Log.d(TAG, "Executing retry attempt " + currentRetryCount);
                        executeRetry();
                    },
                    retryError -> Log.e(TAG, "Failed to schedule retry", retryError)
                )
        );
    }
    
    /**
     * Execute retry operation
     */
    private void executeRetry() {
        _recoveryState.postValue(RecoveryState.RETRYING);
        
        // Try to sync pending operations first
        if (!pendingOperations.isEmpty()) {
            executePendingOperations();
        } else {
            // Try regular sync
            disposables.add(
                smsRepository.syncNewMessages()
                    .andThen(conversationRepository.syncConversationsFromMessages())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        () -> {
                            retryCount.set(0); // Reset retry count on success
                            _recoveryState.postValue(RecoveryState.RECOVERED);
                            Log.d(TAG, "Sync retry successful");
                        },
                        error -> {
                            Log.e(TAG, "Sync retry failed", error);
                            handleSyncError("Sync retry failed: " + error.getMessage(), error);
                        }
                    )
            );
        }
    }
    
    /**
     * Execute pending operations from offline queue
     */
    private void executePendingOperations() {
        if (pendingOperations.isEmpty()) {
            _recoveryState.postValue(RecoveryState.IDLE);
            return;
        }
        
        PendingOperation operation = pendingOperations.poll();
        if (operation == null) {
            _recoveryState.postValue(RecoveryState.IDLE);
            return;
        }
        
        Log.d(TAG, "Executing pending operation: " + operation.type);
        
        disposables.add(
            operation.execute()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        Log.d(TAG, "Pending operation completed: " + operation.type);
                        executePendingOperations(); // Continue with next operation
                    },
                    error -> {
                        Log.e(TAG, "Pending operation failed: " + operation.type, error);
                        // Re-add to queue for later retry
                        pendingOperations.offer(operation);
                        _recoveryState.postValue(RecoveryState.FAILED);
                    }
                )
        );
    }
    
    /**
     * Calculate retry delay with exponential backoff
     */
    private long calculateRetryDelay(int attempt) {
        long delay = BASE_RETRY_DELAY * (1L << (attempt - 1)); // 2^(attempt-1) * base delay
        return Math.min(delay, MAX_RETRY_DELAY);
    }
    
    /**
     * Add operation to pending queue
     */
    public void addPendingOperation(OperationType type, io.reactivex.rxjava3.core.Completable operation) {
        pendingOperations.offer(new PendingOperation(type, operation));
        Log.d(TAG, "Added pending operation: " + type);
    }
    
    /**
     * Clear all pending operations
     */
    public void clearPendingOperations() {
        pendingOperations.clear();
        Log.d(TAG, "Cleared all pending operations");
    }
    
    /**
     * Get retry statistics
     */
    public RecoveryStatistics getRecoveryStatistics() {
        RecoveryStatistics stats = new RecoveryStatistics();
        stats.retryCount = retryCount.get();
        stats.lastErrorTime = lastErrorTime;
        stats.lastErrorMessage = lastErrorMessage;
        stats.pendingOperationsCount = pendingOperations.size();
        stats.isRecovering = _recoveryState.getValue() == RecoveryState.RETRYING;
        return stats;
    }
    
    /**
     * Force recovery attempt
     */
    public void forceRecovery() {
        Log.d(TAG, "Force recovery requested");
        retryCount.set(0); // Reset retry count
        executeRetry();
    }
    
    /**
     * Handle network connectivity changes
     */
    public void onNetworkAvailable() {
        if (_recoveryState.getValue() == RecoveryState.FAILED) {
            Log.d(TAG, "Network available, attempting recovery");
            forceRecovery();
        }
    }
    
    /**
     * Handle user-initiated retry
     */
    public void onUserRetry() {
        Log.d(TAG, "User-initiated retry");
        retryCount.set(0); // Reset retry count
        executeRetry();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        disposables.clear();
        clearPendingOperations();
        retryCount.set(0);
        _recoveryState.postValue(RecoveryState.IDLE);
        Log.d(TAG, "ErrorRecoveryManager cleaned up");
    }
    
    /**
     * Operation types for pending operations
     */
    public enum OperationType {
        SMS_SYNC,
        CONVERSATION_SYNC,
        SEARCH_INDEX_UPDATE,
        MESSAGE_SEND,
        MESSAGE_DELETE
    }
    
    /**
     * Recovery state enum
     */
    public enum RecoveryState {
        IDLE,      // No errors, normal operation
        RETRYING,  // Currently retrying after error
        RECOVERED, // Successfully recovered from error
        FAILED     // Failed to recover after max retries
    }
    
    /**
     * Pending operation wrapper
     */
    private static class PendingOperation {
        final OperationType type;
        final io.reactivex.rxjava3.core.Completable operation;
        
        PendingOperation(OperationType type, io.reactivex.rxjava3.core.Completable operation) {
            this.type = type;
            this.operation = operation;
        }
        
        io.reactivex.rxjava3.core.Completable execute() {
            return operation;
        }
    }
    
    /**
     * Recovery statistics
     */
    public static class RecoveryStatistics {
        public int retryCount;
        public long lastErrorTime;
        public String lastErrorMessage;
        public int pendingOperationsCount;
        public boolean isRecovering;
        
        public boolean hasRecentError() {
            return lastErrorTime > 0 && (System.currentTimeMillis() - lastErrorTime) < 300000; // 5 minutes
        }
        
        public long getTimeSinceLastError() {
            return lastErrorTime > 0 ? System.currentTimeMillis() - lastErrorTime : -1;
        }
    }
}