package com.bulksms.smsmanager;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.bulksms.smsmanager.models.Recipient;
import com.bulksms.smsmanager.data.compliance.RateLimitManager;
import com.bulksms.smsmanager.data.compliance.ComplianceManager;
import com.bulksms.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.bulksms.smsmanager.data.persistence.UploadPersistenceService;
import com.bulksms.smsmanager.data.queue.SmsQueueManager;
import com.bulksms.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.bulksms.smsmanager.data.parser.ExcelParser;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

/**
 * ViewModel for Bulk SMS operations
 * Uses Hilt for dependency injection
 */
@HiltViewModel
public class BulkSmsViewModel extends ViewModel {
    private static final String TAG = "BulkSmsViewModel";
    private final BulkSmsService bulkSmsService;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final EnhancedDeliveryTracker deliveryTracker;
    private final UploadPersistenceService uploadPersistence;
    private final SmsQueueManager queueManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final MutableLiveData<List<Recipient>> recipientsLiveData;
    private final MutableLiveData<String> errorLiveData;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<Integer> progressLiveData;
    private final MutableLiveData<String> statusLiveData;
    private final MutableLiveData<EnhancedDeliveryTracker.DeliveryStatistics> deliveryStatsLiveData;
    private final MutableLiveData<RateLimitManager.RateLimitStats> rateLimitStatsLiveData;
    private final MutableLiveData<SmsQueueManager.QueueStatistics> queueStatsLiveData;
    private final MutableLiveData<UploadSession> activeSessionLiveData;
    private final MutableLiveData<Boolean> showResumePromptLiveData;
    
    // Send speed control
    private final MutableLiveData<Integer> sendSpeedLiveData = new MutableLiveData<>(400);
    private final MutableLiveData<Integer> selectedSimSlotLiveData = new MutableLiveData<>(0);
    
    // Campaign tracking
    private final MutableLiveData<String> currentBulkIdLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> sentCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> failedCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> queuedCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isSendingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPausedLiveData = new MutableLiveData<>(false);

    @Inject
    public BulkSmsViewModel(
        BulkSmsService bulkSmsService,
        RateLimitManager rateLimitManager,
        ComplianceManager complianceManager,
        EnhancedDeliveryTracker deliveryTracker,
        UploadPersistenceService uploadPersistence,
        SmsQueueManager queueManager
    ) {
        this.bulkSmsService = bulkSmsService;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        this.uploadPersistence = uploadPersistence;
        this.queueManager = queueManager;
        
        this.recipientsLiveData = new MutableLiveData<>();
        this.errorLiveData = new MutableLiveData<>();
        this.isLoadingLiveData = new MutableLiveData<>();
        this.progressLiveData = new MutableLiveData<>();
        this.statusLiveData = new MutableLiveData<>();
        this.deliveryStatsLiveData = new MutableLiveData<>();
        this.rateLimitStatsLiveData = new MutableLiveData<>();
        this.queueStatsLiveData = new MutableLiveData<>();
        this.activeSessionLiveData = new MutableLiveData<>();
        this.showResumePromptLiveData = new MutableLiveData<>();
        
        recipientsLiveData.setValue(new ArrayList<>());
        isLoadingLiveData.setValue(false);
        progressLiveData.setValue(0);
        statusLiveData.setValue("Ready");
        showResumePromptLiveData.setValue(false);
        
        // Initialize observers
        observeDeliveryStats();
        observeRateLimitStats();
        observeQueueStats();
        checkForActiveSession();
    }

    public LiveData<List<Recipient>> getRecipients() {
        return recipientsLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoadingLiveData;
    }

    public LiveData<Integer> getProgress() {
        return progressLiveData;
    }

    public LiveData<String> getStatus() {
        return statusLiveData;
    }
    
    public LiveData<EnhancedDeliveryTracker.DeliveryStatistics> getDeliveryStats() {
        return deliveryStatsLiveData;
    }
    
    public LiveData<RateLimitManager.RateLimitStats> getRateLimitStats() {
        return rateLimitStatsLiveData;
    }
    
    public LiveData<SmsQueueManager.QueueStatistics> getQueueStats() {
        return queueStatsLiveData;
    }
    
    public LiveData<UploadSession> getActiveSession() {
        return activeSessionLiveData;
    }
    
    public LiveData<Boolean> getShowResumePrompt() {
        return showResumePromptLiveData;
    }
    
    public LiveData<Integer> getSendSpeed() {
        return sendSpeedLiveData;
    }
    
    public LiveData<Integer> getSelectedSimSlot() {
        return selectedSimSlotLiveData;
    }
    
    public LiveData<String> getCurrentBulkId() {
        return currentBulkIdLiveData;
    }
    
    public LiveData<Integer> getSentCount() {
        return sentCountLiveData;
    }
    
    public LiveData<Integer> getFailedCount() {
        return failedCountLiveData;
    }
    
    public LiveData<Integer> getQueuedCount() {
        return queuedCountLiveData;
    }
    
    public LiveData<Boolean> getIsSending() {
        return isSendingLiveData;
    }
    
    public LiveData<Boolean> getIsPaused() {
        return isPausedLiveData;
    }

    /**
     * Import recipients from CSV/Excel file with enhanced parsing
     */
    public void importRecipientsFromCsv(Uri uri) {
        executor.execute(() -> {
            try {
                isLoadingLiveData.postValue(true);
                statusLiveData.postValue("Importing recipients...");
                
                String fileName = uri.getLastPathSegment();
                if (fileName == null) {
                    fileName = "unknown_file";
                }
                
                // Check file type support
                if (!bulkSmsService.isFileTypeSupported(fileName)) {
                    errorLiveData.postValue("Unsupported file type: " + fileName + 
                        ". Please use CSV, XLSX, or XLS files.");
                    return;
                }
                
                // Parse file with smart mapping
                ExcelParser.ParseResult result = bulkSmsService.parseImportFile(uri, fileName);
                
                // Create upload session
                UploadSession session = new UploadSession();
                session.fileId = "upload_" + System.currentTimeMillis();
                session.fileName = fileName;
                session.recipients = result.recipients;
                session.totalRecords = result.recipients.size();
                session.validRecords = result.recipients.size();
                session.invalidRecords = 0;
                session.processingStatus = "processed";
                
                // Save session
                uploadPersistence.saveCurrentUpload(session);
                
                recipientsLiveData.postValue(result.recipients);
                activeSessionLiveData.postValue(session);
                
                // Update status with mapping info
                String fileType = bulkSmsService.getFileTypeDisplayName(fileName);
                statusLiveData.postValue("Imported " + result.recipients.size() + 
                    " recipients from " + fileType + " file");
                
                // Log mapping information
                Log.d(TAG, "Column mapping: " + result.mapping.toString());
                
            } catch (Exception e) {
                errorLiveData.postValue("Failed to import: " + e.getMessage());
                statusLiveData.postValue("Import failed");
                Log.e(TAG, "Import error", e);
            } finally {
                isLoadingLiveData.postValue(false);
            }
        });
    }

    /**
     * Enhanced send bulk SMS with queue management and tracking
     */
    public void sendBulkSms(String template, int simSlot, String campaignName) {
        List<Recipient> recipients = recipientsLiveData.getValue();
        if (recipients == null || recipients.isEmpty()) {
            errorLiveData.postValue("No recipients to send to");
            return;
        }

        // Validate message
        BulkSmsService.ValidationResult result = bulkSmsService.validateMessage(template);
        if (!result.isValid()) {
            errorLiveData.postValue(result.getErrorMessage());
            return;
        }

        // Generate unique bulk ID
        String bulkId = "blk_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        currentBulkIdLiveData.postValue(bulkId);

        // Reset counters
        sentCountLiveData.postValue(0);
        failedCountLiveData.postValue(0);
        queuedCountLiveData.postValue(0);
        isSendingLiveData.postValue(true);
        isPausedLiveData.postValue(false);
        
        isLoadingLiveData.postValue(true);
        statusLiveData.postValue("Starting campaign...");
        
        disposables.add(
            bulkSmsService.sendBulkSmsAsync(
                recipients,
                template,
                simSlot,
                campaignName,
                (current, total) -> {
                    int progress = (int) ((current * 100.0) / total);
                    progressLiveData.postValue(progress);
                    statusLiveData.postValue("Sending " + current + "/" + total);
                    
                    // Update counters based on delivery stats
                    EnhancedDeliveryTracker.DeliveryStatistics stats = deliveryTracker.getDeliveryStatistics();
                    sentCountLiveData.postValue(stats.sentCount);
                    failedCountLiveData.postValue(stats.failedCount);
                }
            )
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                () -> {
                    statusLiveData.postValue("Campaign sent successfully!");
                    isLoadingLiveData.postValue(false);
                    isSendingLiveData.postValue(false);
                    
                    // Mark upload session as completed
                    UploadSession session = activeSessionLiveData.getValue();
                    if (session != null) {
                        uploadPersistence.markUploadCompleted(
                            session.fileId, 
                            sentCountLiveData.getValue(), 
                            failedCountLiveData.getValue()
                        );
                    }
                    
                    // Refresh statistics
                    refreshDeliveryStats();
                    refreshQueueStats();
                },
                error -> {
                    errorLiveData.postValue("Failed to send: " + error.getMessage());
                    statusLiveData.postValue("Send failed");
                    isLoadingLiveData.postValue(false);
                    isSendingLiveData.postValue(false);
                }
            )
        );
    }
    
    /**
     * Check for active upload session
     */
    private void checkForActiveSession() {
        uploadPersistence.loadCurrentUpload(session -> {
            if (session != null && session.isActive) {
                activeSessionLiveData.postValue(session);
                recipientsLiveData.postValue(session.recipients);
                showResumePromptLiveData.postValue(true);
            }
        });
    }
    
    /**
     * Resume active session
     */
    public void resumeSession() {
        UploadSession session = activeSessionLiveData.getValue();
        if (session != null) {
            recipientsLiveData.postValue(session.recipients);
            showResumePromptLiveData.postValue(false);
            statusLiveData.postValue("Session resumed: " + session.fileName);
        }
    }
    
    /**
     * Discard active session
     */
    public void discardSession() {
        uploadPersistence.clearCurrentUpload();
        activeSessionLiveData.postValue(null);
        showResumePromptLiveData.postValue(false);
        recipientsLiveData.postValue(new ArrayList<>());
        statusLiveData.postValue("Session discarded");
    }
    
    /**
     * Set send speed
     */
    public void setSendSpeed(int speedMs) {
        sendSpeedLiveData.postValue(speedMs);
    }
    
    /**
     * Set selected SIM slot
     */
    public void setSelectedSimSlot(int simSlot) {
        selectedSimSlotLiveData.postValue(simSlot);
    }
    
    /**
     * Toggle pause/resume sending
     */
    public void togglePause() {
        Boolean currentPaused = isPausedLiveData.getValue();
        boolean newPaused = currentPaused == null ? true : !currentPaused;
        isPausedLiveData.postValue(newPaused);
        statusLiveData.postValue(newPaused ? "Sending paused" : "Sending resumed");
    }
    
    /**
     * Stop sending
     */
    public void stopSending() {
        isSendingLiveData.postValue(false);
        isPausedLiveData.postValue(false);
        statusLiveData.postValue("Sending stopped");
    }
    
    /**
     * Run queue now
     */
    public void runQueueNow() {
        disposables.add(
            queueManager.runQueueNow()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        statusLiveData.postValue("Queue processed");
                        refreshQueueStats();
                    },
                    error -> errorLiveData.postValue("Failed to process queue: " + error.getMessage())
                )
        );
    }
    
    /**
     * Clear exhausted messages
     */
    public void clearExhaustedMessages() {
        disposables.add(
            queueManager.clearExhaustedMessages()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        statusLiveData.postValue("Exhausted messages cleared");
                        refreshQueueStats();
                    },
                    error -> errorLiveData.postValue("Failed to clear exhausted: " + error.getMessage())
                )
        );
    }
    
    /**
     * Refresh queue statistics
     */
    private void refreshQueueStats() {
        SmsQueueManager.QueueStatistics stats = queueManager.getQueueStatistics();
        queueStatsLiveData.postValue(stats);
        queuedCountLiveData.postValue(stats.pendingCount + stats.failedCount);
    }

    /**
     * Add a single recipient manually
     */
    public void addRecipient(String name, String phone, Double amount) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients == null) {
            currentRecipients = new ArrayList<>();
        }
        
        Recipient recipient = new Recipient(name, phone, amount, false, null);
        currentRecipients.add(recipient);
        recipientsLiveData.postValue(currentRecipients);
    }

    /**
     * Clear all recipients
     */
    public void clearRecipients() {
        recipientsLiveData.postValue(new ArrayList<>());
        statusLiveData.postValue("Recipients cleared");
    }

    /**
     * Get available SIM slots
     */
    public List<BulkSmsService.SimSlotInfo> getAvailableSimSlots() {
        return bulkSmsService.getAvailableSimSlots();
    }

    /**
     * Validate message content
     */
    public BulkSmsService.ValidationResult validateMessage(String message) {
        return bulkSmsService.validateMessage(message);
    }

    /**
     * Analyze message for optimization suggestions
     */
    public BulkSmsService.MessageAnalysis analyzeMessage(String message) {
        return bulkSmsService.analyzeMessage(message);
    }

    /**
     * Estimate send time
     */
    public String estimateSendTime(int recipientCount, int sendSpeedMs) {
        long estimatedMs = bulkSmsService.estimateSendTime(recipientCount, sendSpeedMs);
        return formatDuration(estimatedMs);
    }
    
    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Dispose RxJava subscriptions
        disposables.clear();
        // Unregister delivery tracker
        deliveryTracker.unregisterReceivers();
        // Shutdown queue manager
        queueManager.shutdown();
        // Shutdown executor
        executor.shutdown();
    }
    
    /**
     * Observe delivery statistics
     */
    private void observeDeliveryStats() {
        disposables.add(
            io.reactivex.rxjava3.core.Observable.interval(0, 5, java.util.concurrent.TimeUnit.SECONDS)
                .map(__ -> deliveryTracker.getDeliveryStatistics())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    stats -> deliveryStatsLiveData.postValue(stats),
                    error -> errorLiveData.postValue("Failed to get delivery stats: " + error.getMessage())
                )
        );
    }
    
    /**
     * Observe rate limit statistics
     */
    private void observeRateLimitStats() {
        disposables.add(
            rateLimitManager.getRateLimitStatus()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    status -> {
                        RateLimitManager.RateLimitStats stats = rateLimitManager.getStats();
                        rateLimitStatsLiveData.postValue(stats);
                    },
                    error -> errorLiveData.postValue("Failed to get rate limit stats: " + error.getMessage())
                )
        );
    }
    
    /**
     * Observe queue statistics
     */
    private void observeQueueStats() {
        disposables.add(
            io.reactivex.rxjava3.core.Observable.interval(30, java.util.concurrent.TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    tick -> {
                        try {
                            SmsQueueManager.QueueStatistics stats = queueManager.getQueueStatistics();
                            queueStatsLiveData.postValue(stats);
                            queuedCountLiveData.postValue(stats.pendingCount + stats.failedCount);
                        } catch (Exception e) {
                            Log.e(TAG, "Error getting queue stats", e);
                            // Don't post error to avoid spamming the UI
                        }
                    },
                    error -> {
                        Log.e(TAG, "Queue stats observable error", error);
                        // Only show critical errors
                        if (error instanceof RuntimeException) {
                            errorLiveData.postValue("Queue stats error: " + error.getMessage());
                        }
                    }
                )
        );
    }
    
    /**
     * Refresh delivery statistics
     */
    private void refreshDeliveryStats() {
        EnhancedDeliveryTracker.DeliveryStatistics stats = deliveryTracker.getDeliveryStatistics();
        deliveryStatsLiveData.postValue(stats);
    }
    
    /**
     * Check compliance for all recipients
     */
    public void checkCompliance() {
        List<Recipient> recipients = recipientsLiveData.getValue();
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        
        isLoadingLiveData.postValue(true);
        statusLiveData.postValue("Checking compliance...");
        
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromIterable(recipients)
                .flatMapSingle(recipient -> 
                    complianceManager.checkCompliance(recipient.getPhone(), "MARKETING")
                        .map(result -> new RecipientComplianceResult(recipient, result))
                )
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    results -> {
                        int compliantCount = 0;
                        int nonCompliantCount = 0;
                        
                        for (RecipientComplianceResult result : results) {
                            if (result.complianceResult.isCompliant()) {
                                compliantCount++;
                            } else {
                                nonCompliantCount++;
                            }
                        }
                        
                        statusLiveData.postValue(
                            "Compliance check: " + compliantCount + " compliant, " + nonCompliantCount + " non-compliant"
                        );
                        isLoadingLiveData.postValue(false);
                    },
                    error -> {
                        errorLiveData.postValue("Compliance check failed: " + error.getMessage());
                        isLoadingLiveData.postValue(false);
                    }
                )
        );
    }
    
    /**
     * Add number to opt-out list
     */
    public void addToOptOut(String phoneNumber, String reason) {
        disposables.add(
            complianceManager.addToOptOut(phoneNumber, reason)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        statusLiveData.postValue("Number added to opt-out list");
                        // Remove from recipients if present
                        removeRecipientByPhone(phoneNumber);
                    },
                    error -> errorLiveData.postValue("Failed to add to opt-out: " + error.getMessage())
                )
        );
    }
    
    /**
     * Remove recipient by phone number
     */
    private void removeRecipientByPhone(String phoneNumber) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients != null) {
            List<Recipient> updatedRecipients = new ArrayList<>();
            for (Recipient recipient : currentRecipients) {
                if (!phoneNumber.equals(recipient.getPhone())) {
                    updatedRecipients.add(recipient);
                }
            }
            recipientsLiveData.postValue(updatedRecipients);
        }
    }
    
    /**
     * Recipient compliance result
     */
    private static class RecipientComplianceResult {
        public final Recipient recipient;
        public final ComplianceManager.ComplianceResult complianceResult;
        
        public RecipientComplianceResult(Recipient recipient, ComplianceManager.ComplianceResult complianceResult) {
            this.recipient = recipient;
            this.complianceResult = complianceResult;
        }
    }
}
