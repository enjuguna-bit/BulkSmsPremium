package com.afriserve.smsmanager;

import android.app.Application;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.work.WorkInfo;
import com.afriserve.smsmanager.models.Recipient;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.dao.ScheduledCampaignDao;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.entity.ScheduledCampaignEntity;
import com.afriserve.smsmanager.data.compliance.RateLimitManager;
import com.afriserve.smsmanager.data.compliance.ComplianceManager;
import com.afriserve.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService;
import com.afriserve.smsmanager.data.queue.SmsQueueManager;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import com.afriserve.smsmanager.data.templates.TemplateManager;
import com.afriserve.smsmanager.data.templates.SmsTemplate;
import com.afriserve.smsmanager.utils.FileUtils;
import com.afriserve.smsmanager.worker.BulkSmsSendingWorker;
import com.afriserve.smsmanager.worker.BulkSmsWorkManager;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.core.Observable;
import javax.inject.Inject;

/**
 * ViewModel for Bulk SMS operations
 * Uses Hilt for dependency injection
 */
@HiltViewModel
public class BulkSmsViewModel extends ViewModel {
    private static final String TAG = "BulkSmsViewModel";
    private static final int PREVIEW_COUNT = 5;
    private static final int VARIABLE_SAMPLE_COUNT = 25;
    private static final long PREVIEW_DEBOUNCE_MS = 180L;
    private static final long TEMPLATE_SAVE_DEBOUNCE_MS = 600L;
    
    // Dependencies
    private final Application application;
    private final BulkSmsService bulkSmsService;
    private final CampaignDao campaignDao;
    private final ScheduledCampaignDao scheduledCampaignDao;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final EnhancedDeliveryTracker deliveryTracker;
    private final UploadPersistenceService uploadPersistence;
    private final SmsQueueManager queueManager;
    private final TemplateManager templateManager;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService previewExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> deliveryTrackingFuture;
    private ScheduledFuture<?> previewFuture;
    private ScheduledFuture<?> saveTemplateFuture;
    private final Object previewLock = new Object();
    private final Object saveLock = new Object();
    private volatile int previewSequence = 0;
    private volatile int variableSequence = 0;
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    // LiveData for UI
    private final MutableLiveData<List<Recipient>> recipientsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> statusLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> sentCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> failedCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> queuedCountLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<EnhancedDeliveryTracker.DeliveryStatistics> deliveryStatsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<TemplateVariableExtractor.TemplateVariable>> templateVariablesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<TemplateVariableExtractor.MessagePreview>> messagePreviewsLiveData = new MutableLiveData<>();
    private final MutableLiveData<ExcelParser.ColumnMapping> detectedColumnsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<ComplianceManager.ComplianceResult>> complianceResultsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPausedLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isSendingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> sendSpeedLiveData = new MutableLiveData<>(300);
    private final MutableLiveData<Integer> simSlotLiveData = new MutableLiveData<>(1);
    private final MutableLiveData<UploadSession> activeSessionLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resumePromptLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<com.afriserve.smsmanager.data.compliance.RateLimitManager.RateLimitStats> rateLimitStatsLiveData = new MutableLiveData<>();
    private final MutableLiveData<com.afriserve.smsmanager.data.queue.SmsQueueManager.QueueStatistics> queueStatsLiveData = new MutableLiveData<>();
    
    // Current data
    private List<Recipient> currentData = new ArrayList<>();
    private String currentTemplate = "";
    private UploadSession currentSession;
    private UploadSession pendingSession;
    private LiveData<List<WorkInfo>> workInfoLiveData;
    private Observer<List<WorkInfo>> workObserver;
    
    @Inject
    public BulkSmsViewModel(
        Application application,
        BulkSmsService bulkSmsService,
        CampaignDao campaignDao,
        ScheduledCampaignDao scheduledCampaignDao,
        RateLimitManager rateLimitManager,
        ComplianceManager complianceManager,
        EnhancedDeliveryTracker deliveryTracker,
        UploadPersistenceService uploadPersistence,
        SmsQueueManager queueManager,
        TemplateManager templateManager
    ) {
        this.application = application;
        this.bulkSmsService = bulkSmsService;
        this.campaignDao = campaignDao;
        this.scheduledCampaignDao = scheduledCampaignDao;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        this.uploadPersistence = uploadPersistence;
        this.queueManager = queueManager;
        this.templateManager = templateManager;
        
        // Initialize with empty data
        recipientsLiveData.postValue(new ArrayList<>());
        templateVariablesLiveData.postValue(new ArrayList<>());
        messagePreviewsLiveData.postValue(new ArrayList<>());
        complianceResultsLiveData.postValue(new ArrayList<>());
        
        // Setup delivery tracking
        setupDeliveryTracking();
        checkForResumeSession();
    }
    
    private void setupDeliveryTracking() {
        // Poll delivery statistics periodically and post to LiveData
        deliveryTrackingFuture = executor.scheduleAtFixedRate(() -> {
            try {
                EnhancedDeliveryTracker.DeliveryStatistics stats = deliveryTracker.getDeliveryStatistics();
                deliveryStatsLiveData.postValue(stats);

                // Also poll queue statistics and rate limit stats
                try {
                    com.afriserve.smsmanager.data.queue.SmsQueueManager.QueueStatistics qstats = queueManager.getQueueStatistics();
                    queueStatsLiveData.postValue(qstats);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get queue stats", e);
                }

                try {
                    com.afriserve.smsmanager.data.compliance.RateLimitManager.RateLimitStats rstats = rateLimitManager.getStats();
                    rateLimitStatsLiveData.postValue(rstats);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get rate limit stats", e);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error polling delivery statistics", e);
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void checkForResumeSession() {
        uploadPersistence.loadCurrentUpload(session -> {
            if (session != null && session.isActive) {
                pendingSession = session;
                resumePromptLiveData.postValue(true);
            }
        });
    }
    
    // Public LiveData getters
    public LiveData<List<Recipient>> getRecipients() { return recipientsLiveData; }
    public LiveData<List<Recipient>> getRecipientsRaw() { return recipientsLiveData; }
    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<Boolean> getIsLoadingRaw() { return isLoadingLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<String> getStatus() { return statusLiveData; }
    public LiveData<String> getStatusRaw() { return statusLiveData; }
    public LiveData<Integer> getProgress() { return progressLiveData; }
    public LiveData<Integer> getSentCount() { return sentCountLiveData; }
    public LiveData<Integer> getSentCountRaw() { return sentCountLiveData; }
    public LiveData<Integer> getFailedCount() { return failedCountLiveData; }
    public LiveData<Integer> getFailedCountRaw() { return failedCountLiveData; }
    public LiveData<Integer> getQueuedCount() { return queuedCountLiveData; }
    public LiveData<EnhancedDeliveryTracker.DeliveryStatistics> getDeliveryStats() { return deliveryStatsLiveData; }
    public LiveData<List<TemplateVariableExtractor.TemplateVariable>> getTemplateVariables() { return templateVariablesLiveData; }
    public LiveData<List<TemplateVariableExtractor.MessagePreview>> getMessagePreviews() { return messagePreviewsLiveData; }
    public LiveData<ExcelParser.ColumnMapping> getDetectedColumns() { return detectedColumnsLiveData; }
    public LiveData<List<ComplianceManager.ComplianceResult>> getComplianceResults() { return complianceResultsLiveData; }
    public LiveData<Boolean> getIsPaused() { return isPausedLiveData; }
    public LiveData<Boolean> getIsSending() { return isSendingLiveData; }
    public LiveData<Integer> getSendSpeed() { return sendSpeedLiveData; }
    public LiveData<Integer> getSimSlot() { return simSlotLiveData; }
    public LiveData<Integer> getSelectedSimSlot() { return simSlotLiveData; }
    public LiveData<UploadSession> getActiveSession() { return activeSessionLiveData; }
    public LiveData<Boolean> getResumePrompt() { return resumePromptLiveData; }
    public LiveData<com.afriserve.smsmanager.data.compliance.RateLimitManager.RateLimitStats> getRateLimitStats() { return rateLimitStatsLiveData; }
    public LiveData<Boolean> getShowResumePrompt() { return resumePromptLiveData; }
    public LiveData<com.afriserve.smsmanager.data.queue.SmsQueueManager.QueueStatistics> getQueueStats() { return queueStatsLiveData; }
    
    // File upload and processing
    public void uploadFile(Uri fileUri) {
        isLoadingLiveData.postValue(true);
        errorLiveData.postValue(null);
        final String fileName = FileUtils.getFileNameFromUri(application, fileUri);

        disposables.add(
            io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    // Use existing parseImportFile which returns an ExcelParser.ParseResult
                    return bulkSmsService.parseImportFile(fileUri, fileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                res -> {
                    currentData = res.recipients;
                    recipientsLiveData.postValue(res.recipients);
                    queuedCountLiveData.postValue(res.recipients.size());
                    isLoadingLiveData.postValue(false);
                    statusLiveData.postValue("File uploaded successfully: " + res.recipients.size() + " recipients");
                    detectedColumnsLiveData.postValue(res.mapping);
                    updateTemplateVariables();
                    initSessionFromImport(fileName, res);
                },
                error -> {
                    isLoadingLiveData.postValue(false);
                    errorLiveData.postValue("File upload failed: " + error.getMessage());
                    detectedColumnsLiveData.postValue(null);
                    Log.e(TAG, "File upload error", error);
                }
            )
        );
    }

    private void initSessionFromImport(String fileName, ExcelParser.ParseResult res) {
        UploadSession session = new UploadSession();
        session.fileId = UUID.randomUUID().toString();
        session.fileName = fileName;
        session.recipients = res.recipients;
        session.totalRecords = res.recipients != null ? res.recipients.size() : 0;
        session.validRecords = session.totalRecords;
        session.invalidRecords = 0;
        session.processingStatus = "ready";
        session.template = currentTemplate;
        session.sendSpeed = sendSpeedLiveData.getValue() != null ? sendSpeedLiveData.getValue() : 300;
        session.simSlot = simSlotLiveData.getValue() != null ? simSlotLiveData.getValue() : 0;
        session.campaignName = "Bulk Campaign";
        session.campaignType = "MARKETING";

        session.columnMapping = new HashMap<>();
        if (res.mapping != null) {
            if (res.mapping.name != null) session.columnMapping.put("name", res.mapping.name);
            if (res.mapping.phone != null) session.columnMapping.put("phone", res.mapping.phone);
            if (res.mapping.amount != null) session.columnMapping.put("amount", res.mapping.amount);
        }

        currentSession = session;
        pendingSession = null;
        resumePromptLiveData.postValue(false);
        uploadPersistence.saveCurrentUploadSync(session);
        activeSessionLiveData.postValue(session);
    }
    
    // Legacy method for CSV import compatibility
    public void importRecipientsFromCsv(Uri fileUri) {
        uploadFile(fileUri);
    }
    
    // Template operations
    public void updateMessageTemplate(String template) {
        this.currentTemplate = template;
        updateMessagePreviews();
        scheduleTemplateSave(template);
    }
    
    // Legacy method for compatibility
    public void updateMessagePreview(String template) {
        updateMessageTemplate(template);
    }
    
    private void updateTemplateVariables() {
        final int sequence = ++variableSequence;
        final List<Recipient> sample = getRecipientSample(VARIABLE_SAMPLE_COUNT);
        previewExecutor.execute(() -> {
            List<Map<String, String>> data = convertRecipientsToRowList(sample, VARIABLE_SAMPLE_COUNT);
            if (data.isEmpty()) {
                if (sequence == variableSequence) {
                    templateVariablesLiveData.postValue(new ArrayList<>());
                }
                return;
            }
            List<TemplateVariableExtractor.TemplateVariable> variables =
                TemplateVariableExtractor.extractVariables(data);
            if (sequence == variableSequence) {
                templateVariablesLiveData.postValue(variables);
            }
        });
    }
    
    private void updateMessagePreviews() {
        final String template = currentTemplate != null ? currentTemplate : "";
        if (template.trim().isEmpty()) {
            synchronized (previewLock) {
                previewSequence++;
                if (previewFuture != null) {
                    previewFuture.cancel(false);
                    previewFuture = null;
                }
            }
            messagePreviewsLiveData.postValue(new ArrayList<>());
            return;
        }

        final int sequence;
        synchronized (previewLock) {
            if (previewFuture != null) {
                previewFuture.cancel(false);
            }
            sequence = ++previewSequence;
            final List<Recipient> sample = getRecipientSample(PREVIEW_COUNT);
            previewFuture = previewExecutor.schedule(() -> {
                List<Map<String, String>> data = convertRecipientsToRowList(sample, PREVIEW_COUNT);
                List<TemplateVariableExtractor.MessagePreview> previews =
                    TemplateVariableExtractor.generatePreview(template, data, PREVIEW_COUNT);
                if (sequence == previewSequence) {
                    messagePreviewsLiveData.postValue(previews);
                }
            }, PREVIEW_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }
    
    public void clearTemplateData() {
        synchronized (previewLock) {
            previewSequence++;
            if (previewFuture != null) {
                previewFuture.cancel(false);
                previewFuture = null;
            }
        }
        synchronized (saveLock) {
            if (saveTemplateFuture != null) {
                saveTemplateFuture.cancel(false);
                saveTemplateFuture = null;
            }
        }
        templateVariablesLiveData.postValue(new ArrayList<>());
        messagePreviewsLiveData.postValue(new ArrayList<>());
        detectedColumnsLiveData.postValue(null);
        currentData = new ArrayList<>();
        currentTemplate = "";
    }

    // Helper to convert Recipient objects into a simple row map expected by parsers
    private List<Map<String, String>> convertRecipientsToRowList(List<Recipient> recipients) {
        return convertRecipientsToRowList(recipients, Integer.MAX_VALUE);
    }

    private List<Map<String, String>> convertRecipientsToRowList(List<Recipient> recipients, int maxCount) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (recipients == null || recipients.isEmpty()) return rows;

        int count = Math.min(maxCount, recipients.size());
        for (int i = 0; i < count; i++) {
            Recipient r = recipients.get(i);
            Map<String, String> row = new HashMap<>();
            row.put("Name", r.getName() != null ? r.getName() : "");
            row.put("PhoneNumber", r.getPhone() != null ? r.getPhone() : "");
            row.put("Phone", r.getPhone() != null ? r.getPhone() : "");
            row.put("Mobile", r.getPhone() != null ? r.getPhone() : "");
            row.put("Amount", r.getAmount() != null ? String.valueOf(r.getAmount()) : "");
            if (r.getFields() != null) row.putAll(r.getFields());
            rows.add(row);
        }

        return rows;
    }

    private List<Recipient> getRecipientSample(int maxCount) {
        List<Recipient> sample = new ArrayList<>();
        if (currentData == null || currentData.isEmpty()) {
            return sample;
        }
        int count = Math.min(maxCount, currentData.size());
        for (int i = 0; i < count; i++) {
            sample.add(currentData.get(i));
        }
        return sample;
    }

    private void scheduleTemplateSave(String template) {
        final UploadSession session = currentSession;
        if (session == null) {
            return;
        }
        synchronized (saveLock) {
            if (saveTemplateFuture != null) {
                saveTemplateFuture.cancel(false);
            }
            saveTemplateFuture = previewExecutor.schedule(() -> {
                session.template = template;
                uploadPersistence.saveCurrentUpload(session);
            }, TEMPLATE_SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }
    
    // Compliance checking
    public void checkCompliance() {
        if (currentData.isEmpty()) {
            complianceResultsLiveData.postValue(new ArrayList<>());
            return;
        }
        
        isLoadingLiveData.postValue(true);
        
        disposables.add(
            Observable.fromIterable(currentData)
                .flatMapSingle(recipient -> complianceManager.checkCompliance(recipient.getPhone(), "MARKETING")
                    .onErrorReturnItem(new ComplianceManager.ComplianceResult()))
                .toList()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    results -> {
                        complianceResultsLiveData.postValue(results);
                        isLoadingLiveData.postValue(false);

                        int compliantCount = 0;
                        int nonCompliantCount = 0;
                        for (ComplianceManager.ComplianceResult r : results) {
                            if (r.isCompliant()) compliantCount++; else nonCompliantCount++;
                        }

                        statusLiveData.postValue("Compliance check: " + compliantCount + " compliant, " + nonCompliantCount + " non-compliant");
                    },
                    error -> {
                        isLoadingLiveData.postValue(false);
                        errorLiveData.postValue("Compliance check failed: " + error.getMessage());
                        Log.e(TAG, "Compliance check error", error);
                    }
                )
        );
    }
    
    // Sending operations
    public void startSending() {
        if (currentData == null || currentData.isEmpty()) {
            errorLiveData.postValue("No recipients to send to");
            return;
        }
        
        if (currentTemplate == null || currentTemplate.trim().isEmpty()) {
            errorLiveData.postValue("No message template provided");
            return;
        }
        
        UploadSession session = prepareSessionForSend(true);

        isSendingLiveData.postValue(true);
        isPausedLiveData.postValue(false);
        statusLiveData.postValue("Starting bulk SMS sending...");
        progressLiveData.postValue(0);
        sentCountLiveData.postValue(session.sentCount);
        failedCountLiveData.postValue(session.failedCount);
        queuedCountLiveData.postValue(currentData.size());

        enqueueWork(session, true);
    }
    
    // Legacy method for compatibility with BulkSmsFragment
    public void startBulkSend(String template, int simSlot) {
        this.currentTemplate = template;
        setSimSlot(simSlot);
        startSending();
    }

    /**
     * Schedule bulk SMS sending at a future time.
     */
    public void scheduleSending(long scheduledAtMillis) {
        if (currentData == null || currentData.isEmpty()) {
            errorLiveData.postValue("No recipients to send to");
            return;
        }
        if (currentTemplate == null || currentTemplate.trim().isEmpty()) {
            errorLiveData.postValue("No message template provided");
            return;
        }

        long now = System.currentTimeMillis();
        if (scheduledAtMillis <= now) {
            errorLiveData.postValue("Schedule time must be in the future");
            return;
        }

        UploadSession session = prepareSessionForSend(true);
        session.scheduledAt = scheduledAtMillis;
        session.processingStatus = "scheduled";
        uploadPersistence.saveCurrentUploadSync(session);

        try {
            if (session.campaignId <= 0) {
                CampaignEntity campaign = new CampaignEntity();
                campaign.name = session.campaignName != null ? session.campaignName : "Bulk Campaign";
                campaign.description = "Scheduled bulk SMS campaign with " + session.totalRecords + " recipients";
                campaign.status = "DRAFT";
                campaign.recipientCount = session.totalRecords;
                campaign.createdAt = now;
                campaign.updatedAt = now;
                campaign.scheduledAt = scheduledAtMillis;
                long campaignId = campaignDao.insertCampaign(campaign).blockingGet();
                session.campaignId = campaignId;
            } else {
                CampaignEntity existing = campaignDao.getCampaignById(session.campaignId).blockingGet();
                if (existing != null) {
                    existing.status = "DRAFT";
                    existing.scheduledAt = scheduledAtMillis;
                    existing.updatedAt = now;
                    campaignDao.updateCampaign(existing).blockingAwait();
                }
            }

            ScheduledCampaignEntity scheduled = new ScheduledCampaignEntity(
                session.campaignId,
                scheduledAtMillis,
                TimeZone.getDefault().getID()
            );
            scheduled.status = "SCHEDULED";
            scheduled.isActive = true;
            scheduledCampaignDao.insertScheduledCampaign(scheduled).blockingAwait();
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule campaign", e);
            errorLiveData.postValue("Failed to schedule campaign: " + e.getMessage());
            return;
        }

        uploadPersistence.saveCurrentUpload(session);
        BulkSmsWorkManager.enqueueBulkSend(application, session.fileId, scheduledAtMillis - now, true);
        statusLiveData.postValue("Bulk SMS scheduled");
    }

    private UploadSession prepareSessionForSend(boolean resetProgress) {
        UploadSession session = currentSession;
        if (session == null) {
            session = new UploadSession();
            session.fileId = UUID.randomUUID().toString();
            session.fileName = "Bulk Campaign";
        }

        session.recipients = new ArrayList<>(currentData);
        session.totalRecords = currentData.size();
        session.validRecords = currentData.size();
        session.invalidRecords = 0;
        session.template = currentTemplate;
        session.simSlot = simSlotLiveData.getValue() != null ? simSlotLiveData.getValue() : 0;
        session.sendSpeed = sendSpeedLiveData.getValue() != null ? sendSpeedLiveData.getValue() : 300;
        session.campaignName = session.campaignName != null ? session.campaignName : "Bulk Campaign";
        session.campaignType = session.campaignType != null ? session.campaignType : "MARKETING";
        session.isActive = true;
        session.isPaused = false;
        session.isStopped = false;
        session.processingStatus = "sending";

        if (resetProgress) {
            session.lastProcessedIndex = 0;
            session.sentCount = 0;
            session.failedCount = 0;
            session.skippedCount = 0;
        }

        currentSession = session;
        pendingSession = null;
        uploadPersistence.saveCurrentUpload(session);
        activeSessionLiveData.postValue(session);
        return session;
    }

    private void enqueueWork(UploadSession session, boolean replaceExisting) {
        if (session == null || session.fileId == null) {
            return;
        }
        BulkSmsWorkManager.enqueueBulkSend(application, session.fileId, 0L, replaceExisting);
        observeWork(session.fileId);
    }

    private void observeWork(String sessionId) {
        if (workInfoLiveData != null && workObserver != null) {
            workInfoLiveData.removeObserver(workObserver);
        }

        workInfoLiveData = BulkSmsWorkManager.getWorkInfos(application, sessionId);
        workObserver = workInfos -> {
            if (workInfos == null || workInfos.isEmpty()) {
                return;
            }
            handleWorkInfo(workInfos.get(0));
        };

        workInfoLiveData.observeForever(workObserver);
    }

    private void handleWorkInfo(WorkInfo info) {
        if (info == null) {
            return;
        }

        WorkInfo.State state = info.getState();
        if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
            int total = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_TOTAL,
                    currentSession != null && currentSession.recipients != null ? currentSession.recipients.size() : 0);
            int processed = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_PROCESSED,
                    currentSession != null ? currentSession.lastProcessedIndex : 0);
            int sent = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_SENT,
                    currentSession != null ? currentSession.sentCount : 0);
            int failed = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_FAILED,
                    currentSession != null ? currentSession.failedCount : 0);
            int skipped = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_SKIPPED,
                    currentSession != null ? currentSession.skippedCount : 0);
            int percent = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_PERCENT,
                    total > 0 ? (processed * 100 / total) : 0);

            progressLiveData.postValue(percent);
            sentCountLiveData.postValue(sent);
            failedCountLiveData.postValue(failed);
            queuedCountLiveData.postValue(Math.max(0, total - processed));
            isSendingLiveData.postValue(true);
            if (state == WorkInfo.State.ENQUEUED) {
                statusLiveData.postValue("Queued for sending...");
            } else {
                statusLiveData.postValue("Sending: " + processed + "/" + total);
            }
            return;
        }

        if (state == WorkInfo.State.SUCCEEDED) {
            String status = info.getOutputData().getString(BulkSmsSendingWorker.RESULT_STATUS);
            int total = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_TOTAL, 0);
            int sent = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_SENT, 0);
            int failed = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_FAILED, 0);
            int skipped = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_SKIPPED, 0);

            sentCountLiveData.postValue(sent);
            failedCountLiveData.postValue(failed);
            queuedCountLiveData.postValue(Math.max(0, total - (sent + failed + skipped)));

            if (BulkSmsService.RESULT_PAUSED.equals(status)) {
                statusLiveData.postValue("Sending paused");
                isSendingLiveData.postValue(false);
                isPausedLiveData.postValue(true);
            } else if (BulkSmsService.RESULT_STOPPED.equals(status)) {
                statusLiveData.postValue("Sending stopped");
                isSendingLiveData.postValue(false);
                isPausedLiveData.postValue(false);
            } else {
                progressLiveData.postValue(100);
                statusLiveData.postValue("Bulk SMS sending completed");
                isSendingLiveData.postValue(false);
                isPausedLiveData.postValue(false);
            }
            return;
        }

        if (state == WorkInfo.State.CANCELLED) {
            statusLiveData.postValue("Sending cancelled");
            isSendingLiveData.postValue(false);
            isPausedLiveData.postValue(false);
            return;
        }

        if (state == WorkInfo.State.FAILED) {
            String error = info.getOutputData().getString(BulkSmsSendingWorker.RESULT_ERROR);
            String message = error != null ? "Sending failed: " + error : "Sending failed";
            statusLiveData.postValue(message);
            errorLiveData.postValue(message);
            isSendingLiveData.postValue(false);
            isPausedLiveData.postValue(false);
        }
    }
    
    public void pauseSending() {
        if (isSendingLiveData.getValue() == null || !isSendingLiveData.getValue()) {
            return;
        }
        if (currentSession != null) {
            currentSession.isPaused = true;
            currentSession.processingStatus = "paused";
            uploadPersistence.saveCurrentUploadSync(currentSession);
        }
        isPausedLiveData.postValue(true);
        statusLiveData.postValue("Sending paused");
    }

    public void resumeSending() {
        if (isPausedLiveData.getValue() == null || !isPausedLiveData.getValue()) {
            return;
        }
        UploadSession session = currentSession != null ? currentSession : uploadPersistence.loadCurrentUploadSync();
        if (session == null) {
            return;
        }
        session.isPaused = false;
        session.isStopped = false;
        session.processingStatus = "sending";
        currentSession = session;
        uploadPersistence.saveCurrentUploadSync(session);
        isPausedLiveData.postValue(false);
        isSendingLiveData.postValue(true);
        statusLiveData.postValue("Sending resumed");
        enqueueWork(session, true);
    }

    public void stopSending() {
        if (isSendingLiveData.getValue() == null || !isSendingLiveData.getValue()) {
            return;
        }
        if (currentSession != null) {
            currentSession.isStopped = true;
            currentSession.isPaused = false;
            currentSession.processingStatus = "stopped";
            uploadPersistence.saveCurrentUploadSync(currentSession);
            if (currentSession.fileId != null) {
                BulkSmsWorkManager.cancel(application, currentSession.fileId);
            }
        }
        isSendingLiveData.postValue(false);
        isPausedLiveData.postValue(false);
        statusLiveData.postValue("Sending stopped");
    }
    
    // Speed control
    public void setSendSpeed(int speed) {
        sendSpeedLiveData.postValue(speed);
        statusLiveData.postValue("Send speed set to: " + speed);
        if (currentSession != null) {
            currentSession.sendSpeed = speed;
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }

    // Backwards-compatible overload accepting String
    public void setSendSpeed(String speed) {
        try {
            int s = Integer.parseInt(speed);
            setSendSpeed(s);
        } catch (NumberFormatException e) {
            // ignore invalid input
            Log.w(TAG, "Invalid send speed value: " + speed);
        }
    }
    
    // SIM slot management
    public void setSimSlot(int slot) {
        simSlotLiveData.postValue(slot);
        statusLiveData.postValue("SIM slot set to: " + slot);
        if (currentSession != null) {
            currentSession.simSlot = slot;
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }
    
    // Queue management
    public void clearQueue() {
        executor.execute(() -> {
            try {
                queueManager.runQueueNow()
                    .subscribe(
                        () -> {
                            queuedCountLiveData.postValue(0);
                            statusLiveData.postValue("Queue processed");
                        },
                        error -> {
                            Log.e(TAG, "Error running queue", error);
                            errorLiveData.postValue("Failed to process queue: " + error.getMessage());
                        }
                    );
            } catch (Exception e) {
                Log.e(TAG, "Error processing queue", e);
                errorLiveData.postValue("Failed to process queue: " + e.getMessage());
            }
        });
    }
    
    public void retryFailedMessages() {
        statusLiveData.postValue("Retrying failed messages...");
        disposables.add(
            queueManager.runQueueNow()
                .subscribe(
                    () -> statusLiveData.postValue("Retry processing completed"),
                    error -> errorLiveData.postValue("Retry failed: " + error.getMessage())
                )
        );
    }
    
    // Compliance management
    public void addToOptOut(String phoneNumber, String reason) {
        disposables.add(
            complianceManager.addToOptOut(phoneNumber, reason)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        statusLiveData.postValue("Number added to opt-out list");
                        removeRecipientByPhone(phoneNumber);
                    },
                    error -> errorLiveData.postValue("Failed to add to opt-out: " + error.getMessage())
                )
        );
    }
    
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
            currentData = updatedRecipients;
            queuedCountLiveData.postValue(updatedRecipients.size());
            if (currentSession != null) {
                currentSession.recipients = updatedRecipients;
                currentSession.totalRecords = updatedRecipients.size();
                currentSession.validRecords = updatedRecipients.size();
                uploadPersistence.saveCurrentUpload(currentSession);
            }
        }
    }
    
    // Template management
    public void saveTemplate(String name, String template) {
        SmsTemplate smsTemplate = new SmsTemplate(name, template);
        templateManager.saveTemplate(smsTemplate);
        statusLiveData.postValue("Template saved: " + name);
    }
    
    public void loadTemplate(String templateId) {
        SmsTemplate template = templateManager.getTemplateById(templateId);
        if (template != null) {
            currentTemplate = template.getContent();
            updateTemplateVariables();
            updateMessagePreviews();
            statusLiveData.postValue("Template loaded: " + template.getName());
        } else {
            errorLiveData.postValue("Failed to load template: not found");
        }
    }
    
    // Legacy template methods for compatibility
    public List<String> getSavedTemplates() {
        List<String> names = new ArrayList<>();
        try {
            List<SmsTemplate> templates = templateManager.getAllTemplates();
            for (SmsTemplate template : templates) {
                if (template.getName() != null) {
                    names.add(template.getName());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load saved templates", e);
        }
        return names;
    }
    
    public String getTemplateContent(String templateName) {
        String content = templateManager.getTemplateContent(templateName);
        return content != null ? content : "";
    }
    
    // Data management
    public void addRecipient(Recipient recipient) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients == null) {
            currentRecipients = new ArrayList<>();
        }
        
        currentRecipients.add(recipient);
        currentData = currentRecipients;
        recipientsLiveData.postValue(currentRecipients);
        queuedCountLiveData.postValue(currentRecipients.size());
        updateMessagePreviews();
        if (currentSession != null) {
            currentSession.recipients = currentRecipients;
            currentSession.totalRecords = currentRecipients.size();
            currentSession.validRecords = currentRecipients.size();
            uploadPersistence.saveCurrentUpload(currentSession);
        }
    }
    
    public void removeRecipient(Recipient recipient) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients != null) {
            currentRecipients.remove(recipient);
            currentData = currentRecipients;
            recipientsLiveData.postValue(currentRecipients);
            queuedCountLiveData.postValue(currentRecipients.size());
            updateMessagePreviews();
            if (currentSession != null) {
                currentSession.recipients = currentRecipients;
                currentSession.totalRecords = currentRecipients.size();
                currentSession.validRecords = currentRecipients.size();
                uploadPersistence.saveCurrentUpload(currentSession);
            }
        }
    }
    
    public void clearAllData() {
        recipientsLiveData.postValue(new ArrayList<>());
        sentCountLiveData.postValue(0);
        failedCountLiveData.postValue(0);
        queuedCountLiveData.postValue(0);
        progressLiveData.postValue(0);
        statusLiveData.postValue("All data cleared");
        clearTemplateData();
        currentSession = null;
        pendingSession = null;
        uploadPersistence.clearCurrentUpload();
        activeSessionLiveData.postValue(null);
    }
    
    public void clearExhaustedRetries() {
        disposables.add(
            queueManager.clearExhaustedMessages()
                .subscribe(
                    () -> statusLiveData.postValue("Exhausted messages cleared"),
                    error -> errorLiveData.postValue("Failed to clear exhausted messages: " + error.getMessage())
                )
        );
    }
    
    // Session management methods for compatibility
    public void resumeSession() {
        uploadPersistence.loadCurrentUpload(session -> {
            UploadSession toResume = session != null ? session : pendingSession;
            if (toResume == null) {
                statusLiveData.postValue("No session to resume");
                return;
            }

            currentSession = toResume;
            pendingSession = null;
            currentData = toResume.recipients != null ? toResume.recipients : new ArrayList<>();
            currentTemplate = toResume.template != null ? toResume.template : "";

            recipientsLiveData.postValue(currentData);
            queuedCountLiveData.postValue(Math.max(0, currentData.size() - toResume.lastProcessedIndex));
            sentCountLiveData.postValue(toResume.sentCount);
            failedCountLiveData.postValue(toResume.failedCount);
            int percent = currentData.isEmpty() ? 0 : (toResume.lastProcessedIndex * 100 / currentData.size());
            progressLiveData.postValue(percent);
            sendSpeedLiveData.postValue(toResume.sendSpeed > 0 ? toResume.sendSpeed : 300);
            simSlotLiveData.postValue(toResume.simSlot);
            activeSessionLiveData.postValue(toResume);
            resumePromptLiveData.postValue(false);
            statusLiveData.postValue("Session resumed");
        });
    }
    
    public void discardSession() {
        pendingSession = null;
        resumePromptLiveData.postValue(false);
        uploadPersistence.clearCurrentUpload();
        statusLiveData.postValue("Session discarded");
        clearAllData();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        if (deliveryTrackingFuture != null && !deliveryTrackingFuture.isCancelled()) {
            deliveryTrackingFuture.cancel(true);
        }
        synchronized (previewLock) {
            if (previewFuture != null && !previewFuture.isCancelled()) {
                previewFuture.cancel(true);
            }
        }
        synchronized (saveLock) {
            if (saveTemplateFuture != null && !saveTemplateFuture.isCancelled()) {
                saveTemplateFuture.cancel(true);
            }
        }
        if (workInfoLiveData != null && workObserver != null) {
            workInfoLiveData.removeObserver(workObserver);
        }
        disposables.clear();
        executor.shutdown();
        previewExecutor.shutdown();
        bulkSmsService.shutdown();
    }
}
