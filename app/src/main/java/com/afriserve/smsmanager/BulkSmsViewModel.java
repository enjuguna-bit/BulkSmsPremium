package com.afriserve.smsmanager;

import android.app.Application;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.afriserve.smsmanager.models.Recipient;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.dao.ScheduledCampaignDao;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.entity.ScheduledCampaignEntity;
import com.afriserve.smsmanager.data.compliance.RateLimitManager;
import com.afriserve.smsmanager.data.compliance.ComplianceManager;
import com.afriserve.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.data.queue.SmsQueueManager;
import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.parser.TemplateVariableExtractor;
import com.afriserve.smsmanager.data.templates.TemplateManager;
import com.afriserve.smsmanager.data.templates.SmsTemplate;
import com.afriserve.smsmanager.domain.CampaignSessionManager;
import com.afriserve.smsmanager.domain.MessagePreviewManager;
import com.afriserve.smsmanager.domain.SendingOrchestrator;
import com.afriserve.smsmanager.utils.FileUtils;
import com.afriserve.smsmanager.worker.BulkSmsWorkManager;
import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * ViewModel for Bulk SMS operations.
 * Delegates to domain helpers: CampaignSessionManager, MessagePreviewManager, SendingOrchestrator.
 */
@HiltViewModel
public class BulkSmsViewModel extends ViewModel {
    private static final String TAG = "BulkSmsViewModel";
    private static final long TEMPLATE_SAVE_DEBOUNCE_MS = AppConfig.Polling.TEMPLATE_SAVE_DEBOUNCE_MS;

    // Dependencies
    private final Application application;
    private final BulkSmsService bulkSmsService;
    private final CampaignDao campaignDao;
    private final ScheduledCampaignDao scheduledCampaignDao;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final EnhancedDeliveryTracker deliveryTracker;
    private final SmsQueueManager queueManager;
    private final TemplateManager templateManager;

    // Domain helpers
    private final CampaignSessionManager sessionManager;
    private final MessagePreviewManager previewManager;
    private final SendingOrchestrator sendingOrchestrator;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private Disposable deliveryPollingDisposable;
    private String campaignNameOverride;

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
    private final MutableLiveData<Integer> sendSpeedLiveData = new MutableLiveData<>(AppConfig.Defaults.SEND_SPEED);
    private final MutableLiveData<Integer> simSlotLiveData =
            new MutableLiveData<>(AppConfig.Defaults.SIM_SLOT);
    private final MutableLiveData<UploadSession> activeSessionLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resumePromptLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<RateLimitManager.RateLimitStats> rateLimitStatsLiveData = new MutableLiveData<>();
    private final MutableLiveData<SmsQueueManager.QueueStatistics> queueStatsLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> deliveredRecipientLiveData = new MutableLiveData<>();

    // Current data
    private List<Recipient> currentData = new ArrayList<>();
    private List<Recipient> importedData = new ArrayList<>();
    private String currentTemplate = "";

    @Inject
    public BulkSmsViewModel(
        Application application,
        BulkSmsService bulkSmsService,
        CampaignDao campaignDao,
        ScheduledCampaignDao scheduledCampaignDao,
        RateLimitManager rateLimitManager,
        ComplianceManager complianceManager,
        EnhancedDeliveryTracker deliveryTracker,
        SmsQueueManager queueManager,
        TemplateManager templateManager,
        CampaignSessionManager sessionManager,
        MessagePreviewManager previewManager,
        SendingOrchestrator sendingOrchestrator
    ) {
        this.application = application;
        this.bulkSmsService = bulkSmsService;
        this.campaignDao = campaignDao;
        this.scheduledCampaignDao = scheduledCampaignDao;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        this.queueManager = queueManager;
        this.templateManager = templateManager;
        this.sessionManager = sessionManager;
        this.previewManager = previewManager;
        this.sendingOrchestrator = sendingOrchestrator;

        deliveryTracker.setDeliveryEventListener((smsId, recipientName) ->
                deliveredRecipientLiveData.postValue(
                        recipientName == null || recipientName.trim().isEmpty()
                                ? "customer" : recipientName.trim()));

        // Initialize with empty data
        recipientsLiveData.postValue(new ArrayList<>());
        templateVariablesLiveData.postValue(new ArrayList<>());
        messagePreviewsLiveData.postValue(new ArrayList<>());
        complianceResultsLiveData.postValue(new ArrayList<>());

        // Setup delivery tracking via RxJava interval
        setupDeliveryTracking();
        sessionManager.checkForResumeSession(available -> resumePromptLiveData.postValue(available));
    }

    // ==================== Delivery Tracking (RxJava) ====================

    private void setupDeliveryTracking() {
        deliveryPollingDisposable = Observable.interval(0, AppConfig.Polling.DELIVERY_STATS_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .observeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .subscribe(tick -> {
                try {
                    deliveryStatsLiveData.postValue(deliveryTracker.getDeliveryStatistics());
                } catch (Exception e) {
                    Log.e(TAG, "Error polling delivery statistics", e);
                }
                try {
                    queueStatsLiveData.postValue(queueManager.getQueueStatistics());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get queue stats", e);
                }
                try {
                    rateLimitStatsLiveData.postValue(rateLimitManager.getStats());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get rate limit stats", e);
                }
            }, error -> Log.e(TAG, "Delivery tracking error", error));
    }

    // ==================== Public LiveData Getters ====================

    public LiveData<List<Recipient>> getRecipients() { return recipientsLiveData; }
    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<String> getError() { return errorLiveData; }
    public LiveData<String> getStatus() { return statusLiveData; }
    public LiveData<Integer> getProgress() { return progressLiveData; }
    public LiveData<Integer> getSentCount() { return sentCountLiveData; }
    public LiveData<Integer> getFailedCount() { return failedCountLiveData; }
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
    public LiveData<String> getDeliveredRecipient() { return deliveredRecipientLiveData; }

    public void clearDeliveredRecipient() {
        deliveredRecipientLiveData.setValue(null);
    }
    public LiveData<Boolean> getResumePrompt() { return resumePromptLiveData; }
    public LiveData<Boolean> getShowResumePrompt() { return resumePromptLiveData; }
    public LiveData<RateLimitManager.RateLimitStats> getRateLimitStats() { return rateLimitStatsLiveData; }
    public LiveData<SmsQueueManager.QueueStatistics> getQueueStats() { return queueStatsLiveData; }

    // Backward-compatibility aliases
    public LiveData<List<Recipient>> getRecipientsRaw() { return recipientsLiveData; }
    public LiveData<Boolean> getIsLoadingRaw() { return isLoadingLiveData; }
    public LiveData<String> getStatusRaw() { return statusLiveData; }
    public LiveData<Integer> getSentCountRaw() { return sentCountLiveData; }
    public LiveData<Integer> getFailedCountRaw() { return failedCountLiveData; }

    // ==================== File Upload ====================

    public void uploadFile(Uri fileUri) {
        isLoadingLiveData.postValue(true);
        errorLiveData.postValue(null);
        final String fileName = FileUtils.getFileNameFromUri(application, fileUri);

        disposables.add(
            io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                try {
                    return bulkSmsService.parseImportFile(fileUri, fileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                res -> {
                    importedData = new ArrayList<>(res.recipients);
                    currentData = new ArrayList<>(importedData);
                    recipientsLiveData.postValue(res.recipients);
                    queuedCountLiveData.postValue(res.recipients.size());
                    isLoadingLiveData.postValue(false);
                    statusLiveData.postValue("File uploaded successfully: " + res.recipients.size() + " recipients");
                    detectedColumnsLiveData.postValue(res.mapping);
                    previewManager.updateTemplateVariables(currentData, templateVariablesLiveData::postValue);

                    int speed = sendSpeedLiveData.getValue() != null ? sendSpeedLiveData.getValue() : AppConfig.Defaults.SEND_SPEED;
                    int slot = simSlotLiveData.getValue() != null ? simSlotLiveData.getValue() : 0;
                    UploadSession session = sessionManager.initSessionFromImport(fileName, res, currentTemplate, speed, slot);
                    resumePromptLiveData.postValue(false);
                    activeSessionLiveData.postValue(session);
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

    public void importRecipientsFromCsv(Uri fileUri) {
        uploadFile(fileUri);
    }

    // ==================== Template Operations ====================

    public void updateMessageTemplate(String template) {
        this.currentTemplate = template;
        previewManager.updateMessagePreviews(template, currentData, messagePreviewsLiveData::postValue);
        previewManager.scheduleTemplateSave(template, TEMPLATE_SAVE_DEBOUNCE_MS,
                () -> sessionManager.saveTemplateToSession(template));
    }

    public void updateMessagePreview(String template) {
        updateMessageTemplate(template);
    }

    public void clearTemplateData() {
        previewManager.clearAll();
        templateVariablesLiveData.postValue(new ArrayList<>());
        messagePreviewsLiveData.postValue(new ArrayList<>());
        detectedColumnsLiveData.postValue(null);
        currentData = new ArrayList<>();
        importedData = new ArrayList<>();
        currentTemplate = "";
    }

    // ==================== Compliance ====================

    public void checkCompliance() {
        if (currentData.isEmpty()) {
            complianceResultsLiveData.postValue(new ArrayList<>());
            return;
        }

        isLoadingLiveData.postValue(true);

        disposables.add(
            Observable.fromIterable(currentData)
                .flatMapSingle(recipient -> complianceManager.checkCompliance(recipient.getPhone(), "BULK_MARKETING")
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

    /** Apply a user-selected arrears bucket to the last imported spreadsheet. */
    public boolean applyArrearsFilter(@NonNull String selection) {
        if (importedData == null || importedData.isEmpty()) return false;
        if ("All customers".equals(selection)) {
            currentData = new ArrayList<>(importedData);
        } else {
            java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern
                    .compile("^(\\d+)(?:\\s*[-–]\\s*(\\d+))?$")
                    .matcher(selection.trim());
            if (!rangeMatcher.matches()) return false;

            int min;
            int max;
            try {
                min = Integer.parseInt(rangeMatcher.group(1));
                max = rangeMatcher.group(2) == null ? min : Integer.parseInt(rangeMatcher.group(2));
            } catch (NumberFormatException e) {
                return false;
            }
            if (min < 0 || max < min) return false;

            List<Recipient> filtered = new ArrayList<>();
            for (Recipient recipient : importedData) {
                Integer days = readArrearsDays(recipient);
                if (days != null && days >= min && days <= max) filtered.add(recipient);
            }
            currentData = filtered;
        }
        recipientsLiveData.postValue(currentData);
        queuedCountLiveData.postValue(currentData.size());
        statusLiveData.postValue("Arrears filter: " + selection + " (" + currentData.size() + " customers)");
        if (sessionManager.getCurrentSession() != null) {
            sessionManager.getCurrentSession().arrearsFilter = selection;
            sessionManager.persistSession();
        }
        previewManager.updateTemplateVariables(currentData, templateVariablesLiveData::postValue);
        previewManager.updateMessagePreviews(currentTemplate, currentData, messagePreviewsLiveData::postValue);
        return true;
    }

    private Integer readArrearsDays(@NonNull Recipient recipient) {
        if (recipient.getFields() == null) return null;
        for (java.util.Map.Entry<String, String> entry : recipient.getFields().entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!(key.equals("days") || key.contains("days_in_arrears") || key.contains("arrears_days")
                    || key.contains("days_overdue") || key.contains("overdue_days")
                    || (key.contains("days") && key.contains("arrears")))) continue;
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?")
                        .matcher(entry.getValue() == null ? "" : entry.getValue());
                if (matcher.find()) return (int) Double.parseDouble(matcher.group());
            } catch (Exception ignored) { }
        }
        return null;
    }

    // ==================== Sending Operations ====================

    private final SendingOrchestrator.WorkProgressCallback workCallback = new SendingOrchestrator.WorkProgressCallback() {
        @Override
        public void onProgress(int percent, int sent, int failed, int queued, String status,
                               boolean isSending, boolean isPaused) {
            progressLiveData.postValue(percent);
            sentCountLiveData.postValue(sent);
            failedCountLiveData.postValue(failed);
            queuedCountLiveData.postValue(queued);
            statusLiveData.postValue(status);
            isSendingLiveData.postValue(isSending);
            isPausedLiveData.postValue(isPaused);

            UploadSession activeSession = activeSessionLiveData.getValue();
            if (activeSession != null) {
                activeSession.sentCount = sent;
                activeSession.failedCount = failed;
                activeSession.lastProcessedIndex = Math.min(
                        activeSession.recipients != null ? activeSession.recipients.size() : 0,
                        sent + failed + queued);
                activeSession.isActive = isSending;
                activeSession.isPaused = isPaused;
                if ("Bulk SMS sending completed".equals(status)) {
                    activeSession.processingStatus = "completed";
                } else if ("Sending paused".equals(status)) {
                    activeSession.processingStatus = "paused";
                } else if ("Sending stopped".equals(status)) {
                    activeSession.processingStatus = "stopped";
                } else if (isSending) {
                    activeSession.processingStatus = "sending";
                }
                activeSessionLiveData.postValue(activeSession);
            }
        }

        @Override
        public void onError(String message) {
            statusLiveData.postValue(message);
            errorLiveData.postValue(message);
            isSendingLiveData.postValue(false);
            isPausedLiveData.postValue(false);
        }
    };

    public void startSending() {
        if (currentData == null || currentData.isEmpty()) {
            errorLiveData.postValue("No recipients to send to");
            return;
        }
        if (currentTemplate == null || currentTemplate.trim().isEmpty()) {
            errorLiveData.postValue("No message template provided");
            return;
        }

        int slot = simSlotLiveData.getValue() != null ? simSlotLiveData.getValue() : 0;
        int speed = sendSpeedLiveData.getValue() != null ? sendSpeedLiveData.getValue() : AppConfig.Defaults.SEND_SPEED;
        UploadSession session = sessionManager.prepareSessionForSend(currentData, currentTemplate, slot, speed, true);
        if (campaignNameOverride != null && !campaignNameOverride.trim().isEmpty()) {
            session.campaignName = campaignNameOverride.trim();
        }

        // Create the campaign before WorkManager starts. This gives the live campaign
        // page and export a stable scope even while the first SMS is being sent.
        Observable.fromCallable(() -> ensureCampaignForSession(session, System.currentTimeMillis()))
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(prepared -> {
                    isSendingLiveData.postValue(true);
                    isPausedLiveData.postValue(false);
                    statusLiveData.postValue("Starting bulk SMS sending...");
                    progressLiveData.postValue(0);
                    sentCountLiveData.postValue(prepared.sentCount);
                    failedCountLiveData.postValue(prepared.failedCount);
                    queuedCountLiveData.postValue(currentData.size());
                    activeSessionLiveData.postValue(prepared);
                    sendingOrchestrator.enqueueWork(prepared, true, workCallback);
                }, error -> {
                    Log.e(TAG, "Unable to prepare campaign", error);
                    errorLiveData.postValue("Unable to prepare campaign: " + error.getMessage());
                });
    }

    public void startBulkSend(String template, int simSlot) {
        this.currentTemplate = template;
        setSimSlot(simSlot);
        startSending();
    }

    public void setCampaignName(@NonNull String name) {
        campaignNameOverride = name.trim();
    }

    private UploadSession ensureCampaignForSession(@NonNull UploadSession session, long now) {
        if (session.campaignId > 0) return session;

        CampaignEntity campaign = new CampaignEntity();
        campaign.name = session.campaignName != null && !session.campaignName.trim().isEmpty()
                ? session.campaignName.trim() : "Bulk Campaign";
        campaign.campaignUuid = UUID.randomUUID().toString();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Africa/Nairobi"));
        campaign.campaignDate = format.format(new Date(now));
        campaign.campaignType = session.campaignType != null ? session.campaignType : "BULK_MARKETING";
        campaign.sourceFileName = session.fileName;
        campaign.arrearsFilter = session.arrearsFilter;
        campaign.timezone = "Africa/Nairobi";
        campaign.messageTemplate = session.template;
        campaign.status = CampaignEntity.STATUS_ACTIVE;
        campaign.recipientCount = session.totalRecords > 0 ? session.totalRecords : session.recipients.size();
        campaign.createdAt = now;
        campaign.updatedAt = now;
        campaign.startedAt = now;
        session.campaignId = campaignDao.insertCampaign(campaign).blockingGet();
        session.processingStatus = "sending";
        sessionManager.persistSession();
        return session;
    }

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

        int slot = simSlotLiveData.getValue() != null ? simSlotLiveData.getValue() : 0;
        int speed = sendSpeedLiveData.getValue() != null ? sendSpeedLiveData.getValue() : AppConfig.Defaults.SEND_SPEED;
        UploadSession session = sessionManager.prepareSessionForSend(currentData, currentTemplate, slot, speed, true);
        session.scheduledAt = scheduledAtMillis;
        session.processingStatus = "scheduled";
        sessionManager.persistSession();

        try {
            if (session.campaignId <= 0) {
                CampaignEntity campaign = new CampaignEntity();
                campaign.name = session.campaignName != null ? session.campaignName : "Bulk Campaign";
                campaign.campaignUuid = UUID.randomUUID().toString();
                SimpleDateFormat campaignDateFormat = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                campaignDateFormat.setTimeZone(TimeZone.getTimeZone("Africa/Nairobi"));
                campaign.campaignDate = campaignDateFormat.format(new Date(now));
                campaign.campaignType = session.campaignType;
                campaign.sourceFileName = session.fileName;
                campaign.arrearsFilter = session.arrearsFilter;
                campaign.timezone = "Africa/Nairobi";
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

            List<ScheduledCampaignEntity> existingSchedules =
                scheduledCampaignDao.getScheduledCampaignsForCampaign(session.campaignId).blockingGet();

            ScheduledCampaignEntity scheduledToSave = null;
            if (existingSchedules != null && !existingSchedules.isEmpty()) {
                for (ScheduledCampaignEntity item : existingSchedules) {
                    if (item != null && item.isActive) {
                        scheduledToSave = item;
                        break;
                    }
                }
                if (scheduledToSave == null) {
                    scheduledToSave = existingSchedules.get(0);
                }
            }

            if (scheduledToSave == null) {
                scheduledToSave = new ScheduledCampaignEntity(
                    session.campaignId, scheduledAtMillis, TimeZone.getDefault().getID());
                scheduledToSave.status = "SCHEDULED";
                scheduledToSave.isActive = true;
                scheduledCampaignDao.insertScheduledCampaign(scheduledToSave).blockingAwait();
            } else {
                scheduledToSave.campaignId = session.campaignId;
                scheduledToSave.scheduledTime = scheduledAtMillis;
                scheduledToSave.nextExecutionTime = scheduledAtMillis;
                scheduledToSave.timezone = TimeZone.getDefault().getID();
                scheduledToSave.status = "SCHEDULED";
                scheduledToSave.isActive = true;
                scheduledToSave.updatedAt = now;
                scheduledCampaignDao.updateScheduledCampaign(scheduledToSave).blockingAwait();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule campaign", e);
            errorLiveData.postValue("Failed to schedule campaign: " + e.getMessage());
            return;
        }

        sessionManager.persistSession();
        sendingOrchestrator.enqueueScheduledWork(session.fileId, scheduledAtMillis - now);
        statusLiveData.postValue("Bulk SMS scheduled");
    }

    public void pauseSending() {
        if (isSendingLiveData.getValue() == null || !isSendingLiveData.getValue()) return;
        sessionManager.markPaused();
        isPausedLiveData.postValue(true);
        isSendingLiveData.postValue(false);
        statusLiveData.postValue("Sending paused");
    }

    public void resumeSending() {
        if (isPausedLiveData.getValue() == null || !isPausedLiveData.getValue()) return;
        UploadSession session = sessionManager.resumeSession();
        if (session == null) return;

        sessionManager.markResumedForSend();
        isPausedLiveData.postValue(false);
        isSendingLiveData.postValue(true);
        statusLiveData.postValue("Sending resumed");
        sendingOrchestrator.enqueueWork(session, true, workCallback);
    }

    public void stopSending() {
        if (isSendingLiveData.getValue() == null || !isSendingLiveData.getValue()) return;
        UploadSession session = sessionManager.getCurrentSession();
        sessionManager.markStopped();
        if (session != null) {
            sendingOrchestrator.cancelWork(session.fileId);
        }
        isSendingLiveData.postValue(false);
        isPausedLiveData.postValue(false);
        statusLiveData.postValue("Sending stopped");
    }

    // ==================== Speed & SIM Controls ====================

    public void setSendSpeed(int speed) {
        sendSpeedLiveData.postValue(speed);
        statusLiveData.postValue("Send speed set to: " + speed);
        UploadSession session = sessionManager.getCurrentSession();
        if (session != null) {
            session.sendSpeed = speed;
            sessionManager.persistSession();
        }
    }

    public void setSendSpeed(String speed) {
        try {
            setSendSpeed(Integer.parseInt(speed));
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid send speed value: " + speed);
        }
    }

    public void setSimSlot(int slot) {
        simSlotLiveData.postValue(slot);
        statusLiveData.postValue("SIM slot set to: " + slot);
        UploadSession session = sessionManager.getCurrentSession();
        if (session != null) {
            session.simSlot = slot;
            sessionManager.persistSession();
        }
    }

    // ==================== Queue Management ====================

    public void clearQueue() {
        disposables.add(
            queueManager.runQueueNow()
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        queuedCountLiveData.postValue(0);
                        statusLiveData.postValue("Queue processed");
                    },
                    error -> {
                        Log.e(TAG, "Error running queue", error);
                        errorLiveData.postValue("Failed to process queue: " + error.getMessage());
                    }
                )
        );
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

    // ==================== Compliance ====================

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
            sessionManager.updateRecipients(updatedRecipients);
        }
    }

    // ==================== Template Management ====================

    public void saveTemplate(String name, String template) {
        SmsTemplate smsTemplate = new SmsTemplate(name, template);
        templateManager.saveTemplate(smsTemplate);
        statusLiveData.postValue("Template saved: " + name);
    }

    public void loadTemplate(String templateId) {
        SmsTemplate template = templateManager.getTemplateById(templateId);
        if (template != null) {
            currentTemplate = template.getContent();
            previewManager.updateTemplateVariables(currentData, templateVariablesLiveData::postValue);
            previewManager.updateMessagePreviews(currentTemplate, currentData, messagePreviewsLiveData::postValue);
            statusLiveData.postValue("Template loaded: " + template.getName());
        } else {
            errorLiveData.postValue("Failed to load template: not found");
        }
    }

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

    // ==================== Data Management ====================

    public void addRecipient(Recipient recipient) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients == null) {
            currentRecipients = new ArrayList<>();
        }
        currentRecipients.add(recipient);
        currentData = currentRecipients;
        recipientsLiveData.postValue(currentRecipients);
        queuedCountLiveData.postValue(currentRecipients.size());
        previewManager.updateMessagePreviews(currentTemplate, currentData, messagePreviewsLiveData::postValue);
        sessionManager.updateRecipients(currentRecipients);
    }

    public void removeRecipient(Recipient recipient) {
        List<Recipient> currentRecipients = recipientsLiveData.getValue();
        if (currentRecipients != null) {
            currentRecipients.remove(recipient);
            currentData = currentRecipients;
            recipientsLiveData.postValue(currentRecipients);
            queuedCountLiveData.postValue(currentRecipients.size());
            previewManager.updateMessagePreviews(currentTemplate, currentData, messagePreviewsLiveData::postValue);
            sessionManager.updateRecipients(currentRecipients);
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
        sessionManager.discardSession();
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

    // ==================== Session Resume/Discard ====================

    public void resumeSession() {
        UploadSession toResume = sessionManager.resumeSession();
        if (toResume == null) {
            statusLiveData.postValue("No session to resume");
            return;
        }

        currentData = toResume.recipients != null ? toResume.recipients : new ArrayList<>();
        currentTemplate = toResume.template != null ? toResume.template : "";

        recipientsLiveData.postValue(currentData);
        queuedCountLiveData.postValue(Math.max(0, currentData.size() - toResume.lastProcessedIndex));
        sentCountLiveData.postValue(toResume.sentCount);
        failedCountLiveData.postValue(toResume.failedCount);
        int percent = currentData.isEmpty() ? 0 : (toResume.lastProcessedIndex * 100 / currentData.size());
        progressLiveData.postValue(percent);
        sendSpeedLiveData.postValue(toResume.sendSpeed > 0 ? toResume.sendSpeed : AppConfig.Defaults.SEND_SPEED);
        simSlotLiveData.postValue(toResume.simSlot);
        activeSessionLiveData.postValue(toResume);
        resumePromptLiveData.postValue(false);
        statusLiveData.postValue("Session resumed");
    }

    public void discardSession() {
        sessionManager.discardSession();
        resumePromptLiveData.postValue(false);
        statusLiveData.postValue("Session discarded");
        clearAllData();
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onCleared() {
        super.onCleared();
        deliveryTracker.setDeliveryEventListener(null);
        if (deliveryPollingDisposable != null && !deliveryPollingDisposable.isDisposed()) {
            deliveryPollingDisposable.dispose();
        }
        previewManager.shutdown();
        sendingOrchestrator.removeWorkObserver();
        disposables.clear();
        bulkSmsService.shutdown();
    }
}
