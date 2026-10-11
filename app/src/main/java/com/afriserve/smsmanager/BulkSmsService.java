package com.afriserve.smsmanager;

import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.CampaignEntity;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.models.Recipient;
import com.afriserve.smsmanager.data.compliance.RateLimitManager;
import com.afriserve.smsmanager.data.compliance.ComplianceManager;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.sync.BidirectionalSmsSync;
import com.afriserve.smsmanager.data.tracking.EnhancedDeliveryTracker;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.domain.FileImportService;
import com.afriserve.smsmanager.data.parser.ExcelParser;
import com.afriserve.smsmanager.data.parser.CsvParser;
import com.afriserve.smsmanager.data.parser.TransactionParser;
import com.afriserve.smsmanager.data.parser.PhoneNormalizer;
import com.afriserve.smsmanager.data.parser.DateParser;
import com.afriserve.smsmanager.data.parser.RecordParser;
import com.afriserve.smsmanager.data.templates.TemplateValueResolver;
import com.afriserve.smsmanager.billing.SubscriptionHelper;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import android.app.PendingIntent;
import android.provider.Telephony;
import javax.inject.Inject;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Premium Bulk SMS Service - Complete implementation
 * Integrated with Room database for campaign and SMS tracking
 */
@Singleton
public class BulkSmsService {
    private static final String TAG = "BulkSmsService";
    public static final String RESULT_COMPLETED = "COMPLETED";
    public static final String RESULT_PAUSED = "PAUSED";
    public static final String RESULT_STOPPED = "STOPPED";
    public static final String RESULT_FAILED = "FAILED";
    // AppConfig.Service.SESSION_PERSIST_INTERVAL_MS  -> AppConfig.Service.AppConfig.Service.SESSION_PERSIST_INTERVAL_MS
    // AppConfig.Service.SESSION_FLAG_REFRESH_INTERVAL_MS -> AppConfig.Service.AppConfig.Service.SESSION_FLAG_REFRESH_INTERVAL_MS
    // AppConfig.Service.CONTROL_CHECK_INTERVAL_MS     -> AppConfig.Service.AppConfig.Service.CONTROL_CHECK_INTERVAL_MS
    // AppConfig.Service.CSV_FREE_LIMIT                -> AppConfig.Service.AppConfig.Service.CSV_FREE_LIMIT
    private final Context context;
    private final SmsDao smsDao;
    private final CampaignDao campaignDao;
    private final RateLimitManager rateLimitManager;
    private final ComplianceManager complianceManager;
    private final EnhancedDeliveryTracker deliveryTracker;
    private final UploadPersistenceService uploadPersistence;
    private final ConversationRepository conversationRepository;
    private final BidirectionalSmsSync bidirectionalSmsSync;
    private final FileImportService fileImportService;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    @Inject
    public BulkSmsService(
            @ApplicationContext @NonNull Context context,
            @NonNull SmsDao smsDao,
            @NonNull CampaignDao campaignDao,
            @NonNull RateLimitManager rateLimitManager,
            @NonNull ComplianceManager complianceManager,
            @NonNull EnhancedDeliveryTracker deliveryTracker,
            @NonNull UploadPersistenceService uploadPersistence,
            @NonNull ConversationRepository conversationRepository,
            @NonNull BidirectionalSmsSync bidirectionalSmsSync,
            @NonNull FileImportService fileImportService) {
        this.context = context;
        this.smsDao = smsDao;
        this.campaignDao = campaignDao;
        this.rateLimitManager = rateLimitManager;
        this.complianceManager = complianceManager;
        this.deliveryTracker = deliveryTracker;
        this.uploadPersistence = uploadPersistence;
        this.conversationRepository = conversationRepository;
        this.bidirectionalSmsSync = bidirectionalSmsSync;
        this.fileImportService = fileImportService;
        this.executorService = Executors.newFixedThreadPool(4);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface ProgressCallback {
        void onProgress(int sent, int total);
    }

    /**
     * Safely call ProgressCallback on main thread
     */
    private void updateProgressOnMainThread(@NonNull ProgressCallback callback, int sent, int total) {
        mainHandler.post(() -> callback.onProgress(sent, total));
    }

    private void updateConversationSafe(@Nullable SmsEntity smsEntity) {
        if (smsEntity == null) return;
        try {
            conversationRepository.updateConversationFromMessage(smsEntity).blockingAwait();
        } catch (Exception e) {
            Log.w(TAG, "Failed to update conversation", e);
        }
    }

    private void syncSentToContentProviderSafe(@Nullable Long smsId) {
        if (smsId == null || smsId <= 0) return;
        try {
            bidirectionalSmsSync.syncSentMessageToContentProvider(smsId)
                .subscribe(
                    () -> {},
                    error -> Log.w(TAG, "Failed to sync sent message to provider", error)
                );
        } catch (Exception e) {
            Log.w(TAG, "Failed to sync sent message to provider", e);
        }
    }

    /**
     * Get available SIM slots with their information
     * @return List of available SIM slot information
     */
    public List<SimSlotInfo> getAvailableSimSlots() {
        List<SimSlotInfo> slots = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (sm != null) {
                    try {
                        java.util.List<android.telephony.SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
                        if (infos != null) {
                            for (android.telephony.SubscriptionInfo info : infos) {
                                int simSlot = info.getSimSlotIndex();
                                String carrierName = info.getCarrierName() != null ? info.getCarrierName().toString() : "Unknown";
                                String displayName = info.getDisplayName() != null ? info.getDisplayName().toString() : "SIM " + (simSlot + 1);
                                
                                slots.add(new SimSlotInfo(simSlot, carrierName, displayName));
                                Log.d(TAG, "Found SIM slot " + simSlot + ": " + carrierName + " (" + displayName + ")");
                            }
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "No permission to access subscription info", e);
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting subscription info", e);
                    }
                }
            } else {
                Log.w(TAG, "READ_PHONE_STATE permission not granted");
            }
        }
        
        if (slots.isEmpty()) {
            slots.add(new SimSlotInfo(0, "Default", "SIM 1"));
        }
        
        return slots;
    }

    /**
     * Get the correct SmsManager for the specified SIM slot
     * @param simSlot The SIM slot index (0 for first SIM, 1 for second SIM, etc.)
     * @return The appropriate SmsManager instance
     */
    private SmsManager getSmsManagerForSlot(int simSlot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager != null) {
                try {
                    java.util.List<android.telephony.SubscriptionInfo> activeSubs = subscriptionManager.getActiveSubscriptionInfoList();
                    if (activeSubs != null) {
                        for (android.telephony.SubscriptionInfo info : activeSubs) {
                            if (info.getSimSlotIndex() == simSlot) {
                                int subscriptionId = info.getSubscriptionId();
                                return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
                            }
                        }
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "No permission to access subscription info, falling back to default", e);
                } catch (Exception e) {
                    Log.w(TAG, "Error getting subscription info, falling back to default", e);
                }
            }
        }
        // Fallback to default for older Android versions or if SIM slot not found
        return SmsManager.getDefault();
    }

    /**
     * Send bulk SMS campaign asynchronously
     * Uses rate limiting and compliance checking
     */
    public Completable sendBulkSmsAsync(
            @NonNull List<Recipient> recipients,
            @NonNull String template,
            int simSlot,
            @Nullable String campaignName,
            @NonNull ProgressCallback progressCallback
    ) {
        return Completable.fromAction(() -> {
            try {
                if (!isSubscriptionActive()) {
                    throw new IllegalStateException("Subscription required for bulk SMS");
                }

                int total = recipients.size();
                int sent = 0;
                int failed = 0;
                int skipped = 0;
                
                // Create campaign entity
                CampaignEntity campaign = new CampaignEntity();
                campaign.name = campaignName != null ? campaignName : "Bulk Campaign";
                campaign.campaignUuid = UUID.randomUUID().toString();
                campaign.campaignDate = campaignDate(System.currentTimeMillis());
                campaign.campaignType = "BULK_MARKETING";
                campaign.timezone = "Africa/Nairobi";
                campaign.messageTemplate = template;
                campaign.description = "Bulk SMS campaign with " + total + " recipients";
                campaign.status = "ACTIVE";
                campaign.recipientCount = total;
                campaign.createdAt = System.currentTimeMillis();
                campaign.updatedAt = System.currentTimeMillis();
                campaign.startedAt = System.currentTimeMillis();
                
                // Insert campaign and get ID
                Long campaignId = campaignDao.insertCampaign(campaign).blockingGet();
                Log.d(TAG, "Created campaign with ID: " + campaignId);

                // Update initial progress
                updateProgressOnMainThread(progressCallback, 0, total);

                // Get the correct SmsManager for the specified SIM slot
                SmsManager smsManager = getSmsManagerForSlot(simSlot);
                Log.d(TAG, "Using SmsManager for SIM slot: " + simSlot);

                for (int i = 0; i < recipients.size(); i++) {
                    Recipient recipient = recipients.get(i);
                    
                    try {
                        // Check compliance before sending
                        ComplianceManager.ComplianceResult complianceResult = 
                            complianceManager.checkCompliance(recipient.getPhone(), "BULK_MARKETING").blockingGet();
                        
                        if (!complianceResult.isCompliant()) {
                            Log.w(TAG, "Skipping non-compliant number: " + recipient.getPhone() + 
                                  " - " + complianceResult.getReason());
                            skipped++;
                            updateProgressOnMainThread(progressCallback, sent + failed + skipped, total);
                            continue;
                        }
                        
                        // Apply rate limiting
                        long delay = rateLimitManager.getDelayBeforeNextSend(recipient.getPhone());
                        if (delay > 0) {
                            Log.d(TAG, "Rate limiting: waiting " + delay + "ms for " + recipient.getPhone());
                            Thread.sleep(delay);
                        }
                        
                        String message = formatMessage(template, recipient);
                        
                        // Track SMS entity ID and entity for potential failure handling
                        Long smsId = null;
                        SmsEntity smsEntity = null;
                        
                        try {
                            // Create SMS entity before sending
                            smsEntity = new SmsEntity();
                            smsEntity.phoneNumber = recipient.getPhone();
                            smsEntity.message = message;
                            smsEntity.status = "PENDING";
                            smsEntity.boxType = Telephony.Sms.MESSAGE_TYPE_SENT;
                            smsEntity.isRead = true;
                            smsEntity.campaignId = campaignId;
                            populateRecipientSnapshot(smsEntity, recipient, i);
                            smsEntity.simSlot = simSlot;
                            smsEntity.createdAt = System.currentTimeMillis();
                            
                            // Save SMS entity before sending
                            smsId = smsDao.insertSms(smsEntity).blockingGet();
                            updateConversationSafe(smsEntity);
                            Log.d(TAG, "Created SMS entity with ID: " + smsId);
                            
                            // Create delivery tracking intents
                            EnhancedDeliveryTracker.DeliveryIntents deliveryIntents = 
                                deliveryTracker.createDeliveryIntents(
                                        String.valueOf(smsId), simSlot, getRecipientDisplayName(recipient));
                            
                            // Send SMS with delivery tracking
                            ArrayList<String> parts = smsManager.divideMessage(message);
                            if (parts.size() == 1) {
                                // Single part message
                                smsManager.sendTextMessage(
                                    recipient.getPhone(), 
                                    null, 
                                    message, 
                                    deliveryIntents != null ? deliveryIntents.sentIntent : null,
                                    deliveryIntents != null ? deliveryIntents.deliveredIntent : null
                                );
                            } else {
                                // Multipart message - create proper intent arrays
                                ArrayList<PendingIntent> sentIntents = null;
                                ArrayList<PendingIntent> deliveredIntents = null;
                                
                                if (deliveryIntents != null) {
                                    sentIntents = new ArrayList<>(parts.size());
                                    deliveredIntents = new ArrayList<>(parts.size());
                                    
                                    // Use the same intents for all parts (common pattern)
                                    for (int j = 0; j < parts.size(); j++) {
                                        sentIntents.add(deliveryIntents.sentIntent);
                                        deliveredIntents.add(deliveryIntents.deliveredIntent);
                                    }
                                }
                                
                                smsManager.sendMultipartTextMessage(
                                    recipient.getPhone(), 
                                    null, 
                                    parts, 
                                    sentIntents,
                                    deliveredIntents
                                );
                            }
                            
                            // Record send for rate limiting
                            rateLimitManager.recordSend(recipient.getPhone());
                            
                            // Leave the row PENDING until SmsManager confirms the
                            // send through the PendingIntent callback. Marking it
                            // SENT here hides carrier-side failures.
                            sent++;
                            Log.d(TAG, "SMS queued for send: " + recipient.getPhone());
                            
                        } catch (Exception e) {
                            failed++;
                            
                            // Update existing entity if we have one, otherwise create new
                            if (smsId != null && smsEntity != null) {
                                // Update the existing PENDING entity to FAILED
                                smsEntity.status = "FAILED";
                                smsEntity.errorMessage = e.getMessage();
                                smsEntity.errorCode = "SEND_ERROR";
                                smsEntity.sentAt = System.currentTimeMillis();
                                smsDao.updateSms(smsEntity).blockingAwait();
                            } else {
                                // Create new FAILED entity (fallback case)
                                SmsEntity failedEntity = new SmsEntity();
                                failedEntity.phoneNumber = recipient.getPhone();
                                failedEntity.message = message;
                                failedEntity.status = "FAILED";
                                failedEntity.boxType = Telephony.Sms.MESSAGE_TYPE_SENT;
                                failedEntity.isRead = true;
                                failedEntity.errorMessage = e.getMessage();
                                failedEntity.errorCode = "SEND_ERROR";
                                failedEntity.campaignId = campaignId;
                                populateRecipientSnapshot(failedEntity, recipient, i);
                                failedEntity.createdAt = System.currentTimeMillis();
                                failedEntity.sentAt = System.currentTimeMillis();
                                
                                try {
                                    smsDao.insertSms(failedEntity).blockingGet();
                                    updateConversationSafe(failedEntity);
                                } catch (Exception insertError) {
                                    Log.e(TAG, "Failed to save failed SMS", insertError);
                                }
                            }
                            
                            // Update campaign counts
                            try {
                                campaignDao.incrementFailedCount(campaignId).blockingAwait();
                            } catch (Exception updateError) {
                                Log.e(TAG, "Failed to update campaign counts", updateError);
                            }
                            
                            Log.e(TAG, "❌ Failed to send SMS to: " + recipient.getPhone(), e);
                        }
                        
                    } catch (Exception e) {
                        // Handle outer-level exceptions (compliance, rate limiting, etc.)
                        failed++;
                        Log.e(TAG, "❌ Failed to process recipient: " + recipient.getPhone(), e);
                    }

                    // Update progress
                    updateProgressOnMainThread(progressCallback, sent + failed + skipped, total);
                }
                
                // Update campaign status to COMPLETED
                try {
                    CampaignEntity updatedCampaign = campaignDao.getCampaignById(campaignId).blockingGet();
                    if (updatedCampaign != null) {
                        int pendingCallbacks = smsDao.getNonTerminalCountByCampaign(campaignId).blockingGet();
                        updatedCampaign.status = pendingCallbacks == 0 ? "COMPLETED" : "ACTIVE";
                        updatedCampaign.completedAt = pendingCallbacks == 0 ? System.currentTimeMillis() : null;
                        updatedCampaign.updatedAt = System.currentTimeMillis();
                        updatedCampaign.skippedCount = skipped;
                        campaignDao.updateCampaign(updatedCampaign).blockingAwait();
                        Log.d(TAG, "Campaign status updated to COMPLETED");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update campaign status", e);
                }

                Log.d(TAG, "📊 Campaign completed - Sent: " + sent + ", Failed: " + failed + ", Skipped: " + skipped);

            } catch (Exception e) {
                Log.e(TAG, "❌ Bulk SMS campaign failed", e);
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Send bulk SMS using a persisted session (WorkManager friendly).
     */
    public BulkSendResult sendBulkSmsSession(
            @NonNull UploadSession session,
            @Nullable ProgressCallback progressCallback
    ) {
        if (session == null || session.recipients == null || session.recipients.isEmpty()) {
            return new BulkSendResult(RESULT_FAILED, 0, 0, 0, 0, 0L, 0, "No recipients");
        }

        if (session.fileId == null || session.fileId.trim().isEmpty()) {
            session.fileId = String.valueOf(System.currentTimeMillis());
        }

        String template = session.template != null ? session.template : "";
        if (template.trim().isEmpty()) {
            return new BulkSendResult(RESULT_FAILED, 0, 0, 0, session.recipients.size(),
                    session.campaignId, session.lastProcessedIndex, "No template provided");
        }

        List<Recipient> recipients = session.recipients;
        int total = recipients.size();
        if (!isSubscriptionActive()) {
            if (!(isCsvSession(session) && total <= AppConfig.Service.CSV_FREE_LIMIT)) {
                session.processingStatus = "blocked";
                session.isActive = false;
                persistSession(session);
                String reason = isCsvSession(session)
                        ? "Free version allows up to " + AppConfig.Service.CSV_FREE_LIMIT + " recipients per CSV"
                        : "Subscription required for bulk SMS";
                return new BulkSendResult(RESULT_FAILED, 0, 0, 0, total,
                        session.campaignId, session.lastProcessedIndex, reason);
            }
        }
        int startIndex = Math.min(Math.max(session.lastProcessedIndex, 0), total);
        int sent = Math.max(0, session.sentCount);
        int failed = Math.max(0, session.failedCount);
        int skipped = Math.max(0, session.skippedCount);

        // Statistics are process-local and belong to this campaign, not to a
        // previous campaign that used the same singleton tracker.
        deliveryTracker.resetStatistics();

        session.totalRecords = total;
        session.validRecords = total;

        long campaignId = session.campaignId;
        long now = System.currentTimeMillis();

        if (campaignId <= 0) {
            CampaignEntity campaign = new CampaignEntity();
            campaign.name = session.campaignName != null ? session.campaignName : "Bulk Campaign";
            campaign.campaignUuid = UUID.randomUUID().toString();
            campaign.campaignDate = campaignDate(now);
            campaign.campaignType = session.campaignType != null ? session.campaignType : "BULK_MARKETING";
            campaign.sourceFileName = session.fileName;
            campaign.arrearsFilter = session.arrearsFilter;
            campaign.timezone = "Africa/Nairobi";
            campaign.messageTemplate = template;
            campaign.description = "Bulk SMS campaign with " + total + " recipients";
            campaign.status = "ACTIVE";
            campaign.recipientCount = total;
            campaign.createdAt = now;
            campaign.updatedAt = now;
            campaign.startedAt = now;

            campaignId = campaignDao.insertCampaign(campaign).blockingGet();
            session.campaignId = campaignId;
            persistSession(session);
        } else {
            try {
                campaignDao.resumeCampaign(campaignId).blockingAwait();
                CampaignEntity existing = campaignDao.getCampaignById(campaignId).blockingGet();
                if (existing != null && (existing.campaignUuid == null || existing.campaignUuid.trim().isEmpty())) {
                    existing.campaignUuid = UUID.randomUUID().toString();
                    existing.campaignDate = existing.campaignDate != null ? existing.campaignDate : campaignDate(existing.createdAt);
                    existing.timezone = existing.timezone != null ? existing.timezone : "Africa/Nairobi";
                    existing.updatedAt = now;
                    campaignDao.updateCampaign(existing).blockingAwait();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to resume campaign", e);
            }
        }

        SmsManager smsManager = getSmsManagerForSlot(session.simSlot);
        String campaignType = session.campaignType != null ? session.campaignType : "MARKETING";
        String resultStatus = RESULT_COMPLETED;
        long lastPersistAt = System.currentTimeMillis();
        long lastFlagRefreshAt = 0L;

        for (int i = startIndex; i < total; i++) {
            long nowLoop = System.currentTimeMillis();
            if (lastFlagRefreshAt == 0L || nowLoop - lastFlagRefreshAt >= AppConfig.Service.SESSION_FLAG_REFRESH_INTERVAL_MS) {
                session = refreshSessionFlags(session);
                lastFlagRefreshAt = nowLoop;
            }

            if (session.isStopped) {
                resultStatus = RESULT_STOPPED;
                break;
            }
            if (session.isPaused) {
                resultStatus = RESULT_PAUSED;
                break;
            }

            Recipient recipient = recipients.get(i);
            String message = null;
            Long smsId = null;
            SmsEntity smsEntity = null;

            try {
                ComplianceManager.ComplianceResult complianceResult =
                        complianceManager.checkCompliance(recipient.getPhone(), campaignType).blockingGet();

                if (!complianceResult.isCompliant()) {
                    skipped++;
                    updateProgressState(session, i + 1, sent, failed, skipped, progressCallback, total);
                    continue;
                }

                long rateDelay = rateLimitManager.getDelayBeforeNextSend(recipient.getPhone());
                long speedDelayMs = getSendSpeedDelayMs(session.sendSpeed);
                long delay = Math.max(rateDelay, speedDelayMs);
                if (delay > 0) {
                    session = waitWithControl(session, delay);
                    if (session.isStopped) {
                        resultStatus = RESULT_STOPPED;
                        break;
                    }
                    if (session.isPaused) {
                        resultStatus = RESULT_PAUSED;
                        break;
                    }
                }

                message = formatMessage(template, recipient);

                smsEntity = new SmsEntity();
                smsEntity.phoneNumber = recipient.getPhone();
                smsEntity.message = message;
                smsEntity.status = "PENDING";
                smsEntity.boxType = Telephony.Sms.MESSAGE_TYPE_SENT;
                smsEntity.isRead = true;
                smsEntity.campaignId = campaignId;
                populateRecipientSnapshot(smsEntity, recipient, i);
                smsEntity.simSlot = session.simSlot;
                smsEntity.createdAt = System.currentTimeMillis();

                smsId = smsDao.insertSms(smsEntity).blockingGet();
                updateConversationSafe(smsEntity);

                EnhancedDeliveryTracker.DeliveryIntents deliveryIntents =
                        deliveryTracker.createDeliveryIntents(
                                String.valueOf(smsId), session.simSlot, getRecipientDisplayName(recipient));

                ArrayList<String> parts = smsManager.divideMessage(message);
                if (parts.size() == 1) {
                    smsManager.sendTextMessage(
                            recipient.getPhone(),
                            null,
                            message,
                            deliveryIntents != null ? deliveryIntents.sentIntent : null,
                            deliveryIntents != null ? deliveryIntents.deliveredIntent : null
                    );
                } else {
                    ArrayList<PendingIntent> sentIntents = null;
                    ArrayList<PendingIntent> deliveredIntents = null;

                    if (deliveryIntents != null) {
                        sentIntents = new ArrayList<>(parts.size());
                        deliveredIntents = new ArrayList<>(parts.size());

                        for (int j = 0; j < parts.size(); j++) {
                            sentIntents.add(deliveryIntents.sentIntent);
                            deliveredIntents.add(deliveryIntents.deliveredIntent);
                        }
                    }

                    smsManager.sendMultipartTextMessage(
                            recipient.getPhone(),
                            null,
                            parts,
                            sentIntents,
                            deliveredIntents
                    );
                }

                rateLimitManager.recordSend(recipient.getPhone());

                // The sent callback owns the SENT transition and campaign count.
                sent++;

            } catch (Exception e) {
                failed++;

                if (smsId != null && smsEntity != null) {
                    smsEntity.status = "FAILED";
                    smsEntity.errorMessage = e.getMessage();
                    smsEntity.errorCode = "SEND_ERROR";
                    smsEntity.sentAt = System.currentTimeMillis();
                    try {
                        smsDao.updateSms(smsEntity).blockingAwait();
                    } catch (Exception updateError) {
                        Log.e(TAG, "Failed to update failed SMS", updateError);
                    }
                } else if (message != null) {
                    SmsEntity failedEntity = new SmsEntity();
                    failedEntity.phoneNumber = recipient.getPhone();
                    failedEntity.message = message;
                    failedEntity.status = "FAILED";
                    failedEntity.boxType = Telephony.Sms.MESSAGE_TYPE_SENT;
                    failedEntity.isRead = true;
                    failedEntity.errorMessage = e.getMessage();
                    failedEntity.errorCode = "SEND_ERROR";
                    failedEntity.campaignId = campaignId;
                    populateRecipientSnapshot(failedEntity, recipient, i);
                    failedEntity.createdAt = System.currentTimeMillis();
                    failedEntity.sentAt = System.currentTimeMillis();
                    try {
                        smsDao.insertSms(failedEntity).blockingGet();
                        updateConversationSafe(failedEntity);
                    } catch (Exception insertError) {
                        Log.e(TAG, "Failed to save failed SMS", insertError);
                    }
                }

                try {
                    campaignDao.incrementFailedCount(campaignId).blockingAwait();
                } catch (Exception updateError) {
                    Log.e(TAG, "Failed to update campaign counts", updateError);
                }

                if (message != null) {
                    deliveryTracker.enqueueForRetryManual(recipient.getPhone(), message, session.simSlot, smsId, e.getMessage());
                }

                Log.e(TAG, "❌ Failed to send SMS to: " + recipient.getPhone(), e);
            }

            updateProgressState(session, i + 1, sent, failed, skipped, progressCallback, total);

            nowLoop = System.currentTimeMillis();
            if (nowLoop - lastPersistAt >= AppConfig.Service.SESSION_PERSIST_INTERVAL_MS || i == total - 1) {
                persistSession(session);
                lastPersistAt = nowLoop;
            }
        }

        session.sentCount = sent;
        session.failedCount = failed;
        session.skippedCount = skipped;

        if (RESULT_COMPLETED.equals(resultStatus)) {
            session.isActive = false;
            session.isPaused = false;
            session.isStopped = false;
            session.processingStatus = "completed";

            try {
                CampaignEntity updatedCampaign = campaignDao.getCampaignById(campaignId).blockingGet();
                if (updatedCampaign != null) {
                    int pendingCallbacks = smsDao.getNonTerminalCountByCampaign(campaignId).blockingGet();
                    updatedCampaign.status = pendingCallbacks == 0 ? "COMPLETED" : "ACTIVE";
                    updatedCampaign.completedAt = pendingCallbacks == 0 ? System.currentTimeMillis() : null;
                    updatedCampaign.updatedAt = System.currentTimeMillis();
                    updatedCampaign.skippedCount = skipped;
                    campaignDao.updateCampaign(updatedCampaign).blockingAwait();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update campaign status", e);
            }

            uploadPersistence.markUploadCompleted(session.fileId, sent, failed);
        } else if (RESULT_PAUSED.equals(resultStatus)) {
            session.processingStatus = "paused";
            session.isPaused = true;
            try {
                campaignDao.pauseCampaign(campaignId).blockingAwait();
            } catch (Exception e) {
                Log.w(TAG, "Failed to pause campaign", e);
            }
        } else if (RESULT_STOPPED.equals(resultStatus)) {
            session.processingStatus = "stopped";
            session.isActive = false;
            session.isStopped = true;
            try {
                campaignDao.updateCampaignStatus(campaignId, "CANCELLED").blockingAwait();
            } catch (Exception e) {
                Log.w(TAG, "Failed to cancel campaign", e);
            }
        }

        persistSession(session);

        return new BulkSendResult(resultStatus, sent, failed, skipped, total, campaignId,
                session.lastProcessedIndex, null);
    }

    private UploadSession refreshSessionFlags(@NonNull UploadSession session) {
        try {
            if (session.fileId != null) {
                UploadSession latest = uploadPersistence.loadSessionSync(session.fileId);
                if (latest != null) {
                    session.isPaused = latest.isPaused;
                    session.isStopped = latest.isStopped;
                    if (latest.sendSpeed > 0) {
                        session.sendSpeed = latest.sendSpeed;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh session flags", e);
        }
        return session;
    }

    private UploadSession waitWithControl(@NonNull UploadSession session, long delayMs) {
        long remaining = delayMs;
        while (remaining > 0) {
            long chunk = Math.min(AppConfig.Service.CONTROL_CHECK_INTERVAL_MS, remaining);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                session.isStopped = true;
                break;
            }
            remaining -= chunk;

            session = refreshSessionFlags(session);
            if (session.isStopped || session.isPaused) {
                break;
            }
        }
        return session;
    }

    private void updateProgressState(@NonNull UploadSession session, int processed, int sent,
                                     int failed, int skipped,
                                     @Nullable ProgressCallback callback, int total) {
        session.lastProcessedIndex = processed;
        session.sentCount = sent;
        session.failedCount = failed;
        session.skippedCount = skipped;
        session.processingStatus = "sending";

        if (callback != null) {
            callback.onProgress(processed, total);
        }
    }

    private void persistSession(@NonNull UploadSession session) {
        try {
            if (uploadPersistence.isActiveSession(session.fileId)) {
                uploadPersistence.saveCurrentUploadSync(session);
            } else {
                uploadPersistence.saveSessionSync(session);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist session", e);
        }
    }

    private boolean isCsvSession(@NonNull UploadSession session) {
        if (session.source != null && "csv".equalsIgnoreCase(session.source.trim())) {
            return true;
        }
        if (session.fileName != null && "CSV Upload".equalsIgnoreCase(session.fileName.trim())) {
            return true;
        }
        if (session.fileName != null && session.fileName.toLowerCase(Locale.getDefault()).endsWith(".csv")) {
            return true;
        }
        if (session.campaignName != null && "CSV Campaign".equalsIgnoreCase(session.campaignName.trim())) {
            return true;
        }
        return false;
    }

    private boolean isSubscriptionActive() {
        try {
            if (SubscriptionHelper.INSTANCE.hasActiveSubscription(context)) {
                return true;
            }
            SubscriptionHelper.SubscriptionStatus cached =
                    SubscriptionHelper.INSTANCE.getCachedStatus(context);
            long now = System.currentTimeMillis();
            if (cached != null && cached.getPremium()
                    && (cached.getPaidUntilMillis() == null || cached.getPaidUntilMillis() > now)) {
                return true;
            }

            SubscriptionHelper.SubscriptionStatus refreshed =
                    SubscriptionHelper.INSTANCE.refreshSubscriptionStatusBlocking(context, true);
            return refreshed != null && refreshed.getPremium()
                    && (refreshed.getPaidUntilMillis() == null || refreshed.getPaidUntilMillis() > now);
        } catch (Exception e) {
            Log.w(TAG, "Failed to verify subscription status", e);
            return false;
        }
    }

    private long getSendSpeedDelayMs(int sendSpeedPerMinute) {
        if (sendSpeedPerMinute <= 0) {
            return 0;
        }
        return 60000L / Math.max(1, sendSpeedPerMinute);
    }

    /**
     * Format message with recipient variables
     */
    @NonNull
    public String formatMessage(@NonNull String template, @NonNull Recipient recipient) {
        Map<String, String> values = new HashMap<>();
        values.put("name", recipient.getName() != null ? recipient.getName() : "");
        values.put("phone", recipient.getPhone() != null ? recipient.getPhone() : "");
        values.put("amount", recipient.getAmount() != null ? recipient.getAmount().toString() : "");
        if (recipient.getFields() != null) values.putAll(recipient.getFields());

        String formatted = TemplateValueResolver.process(template, values);
        
        // Auto-split name support
        String nameToSplit = null;
        if (recipient.getName() != null && !recipient.getName().trim().isEmpty()) {
            nameToSplit = recipient.getName();
        } else if (recipient.getFields() != null) {
            for (Map.Entry<String, String> entry : recipient.getFields().entrySet()) {
                if (com.afriserve.smsmanager.data.csv.NameSplitter.isNameColumn(entry.getKey())) {
                    String val = entry.getValue();
                    if (val != null && !val.trim().isEmpty()) {
                        nameToSplit = val;
                        break;
                    }
                }
            }
        }
        
        if (nameToSplit != null) {
            com.afriserve.smsmanager.data.csv.NameSplitter.SplitName split = com.afriserve.smsmanager.data.csv.NameSplitter.split(nameToSplit);
            formatted = formatted.replace("{firstname}", split.getPart("firstname"));
            formatted = formatted.replace("{secondname}", split.getPart("secondname"));
            formatted = formatted.replace("{thirdname}", split.getPart("thirdname"));
        }

        return formatted;
    }

    /** Resolve the customer name shown when the delivery receipt arrives. */
    @NonNull
    private String getRecipientDisplayName(@NonNull Recipient recipient) {
        Map<String, String> fields = recipient.getFields();
        String fullName = getRecipientField(fields, "fullnames");
        if (isBlank(fullName)) {
            fullName = getRecipientField(fields, "fullname");
        }
        if (!isBlank(fullName)) {
            return fullName.trim();
        }

        StringBuilder nameParts = new StringBuilder();
        for (String field : new String[] {"firstname", "secondname", "thirdname"}) {
            String value = getRecipientField(fields, field);
            if (!isBlank(value)) {
                if (nameParts.length() > 0) nameParts.append(' ');
                nameParts.append(value.trim());
            }
        }
        if (nameParts.length() > 0) {
            return nameParts.toString();
        }

        if (!isBlank(recipient.getName())) {
            return recipient.getName().trim();
        }
        return "customer";
    }

    private String campaignDate(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Africa/Nairobi"));
        return format.format(new Date(timestamp));
    }

    /** Store source-row values on the campaign recipient; never join reports by phone alone. */
    private void populateRecipientSnapshot(@NonNull SmsEntity sms, @NonNull Recipient recipient, int rowIndex) {
        sms.customerName = getRecipientDisplayName(recipient);
        sms.amount = recipient.getAmount();
        sms.officerName = firstRecipientField(recipient,
                "officer", "officername", "loanofficer", "loan_officer", "loanofficername",
                "fieldofficer", "field_officer", "fieldofficername",
                "collectionofficer", "collection_officer", "collectionofficername",
                "relationship_officer", "relationshipofficer", "relationshipofficername",
                "officerincharge", "officer_in_charge", "assignedofficer", "assigned_officer",
                "assignedto", "assigned_to", "salesrep", "sales_rep", "salesrepname",
                "salesrepresentative", "collector", "collectorname", "agent", "agentname",
                "ro", "ro_name", "roname");
        sms.arrearsDays = parseArrearsDays(recipient);
        sms.sourceRowIndex = rowIndex;
    }

    @Nullable
    private String firstRecipientField(@NonNull Recipient recipient, String... keys) {
        Map<String, String> fields = recipient.getFields();
        if (fields == null) return null;
        for (String wanted : keys) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                String key = normalizeRecipientFieldKey(entry.getKey());
                String wantedKey = normalizeRecipientFieldKey(wanted);
                if (key.equals(wantedKey) && !isBlank(entry.getValue())) {
                    return entry.getValue().trim();
                }
            }
        }
        return null;
    }

    private String normalizeRecipientFieldKey(@Nullable String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Nullable
    private Integer parseArrearsDays(@NonNull Recipient recipient) {
        Map<String, String> fields = recipient.getFields();
        if (fields == null) return null;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (!(key.equals("days") || key.contains("days_in_arrears") || key.contains("arrears_days")
                    || key.contains("days_overdue") || key.contains("overdue_days")
                    || (key.contains("days") && (key.contains("arrears") || key.contains("overdue"))))) continue;
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?")
                        .matcher(entry.getValue() == null ? "" : entry.getValue());
                if (matcher.find()) return (int) Double.parseDouble(matcher.group());
            } catch (Exception ignored) { }
        }
        return null;
    }

    @Nullable
    private String getRecipientField(Map<String, String> fields, String expectedName) {
        if (fields == null) return null;
        String normalizedExpected = expectedName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey() == null) continue;
            String normalizedKey = entry.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (normalizedExpected.equals(normalizedKey)) return entry.getValue();
        }
        return null;
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Parse a single CSV line handling quoted fields.
     * Delegates to FileImportService.
     */
    @NonNull
    private String[] parseCsvLine(@NonNull String line) {
        return fileImportService.parseCsvLine(line);
    }

    /**
     * Parse CSV file to extract recipients. Delegates to FileImportService.
     */
    @NonNull
    public List<Recipient> parseCsv(@NonNull Uri uri) throws IOException {
        return fileImportService.parseCsv(uri);
    }

    /**
     * Enhanced file parser supporting Excel and CSV. Delegates to FileImportService.
     */
    @NonNull
    public ExcelParser.ParseResult parseImportFile(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        return fileImportService.parseImportFile(uri, fileName);
    }

    /**
     * Parse SMS transaction. Delegates to FileImportService.
     */
    @Nullable
    public TransactionParser.Transaction parseSmsTransaction(@NonNull String message) {
        return fileImportService.parseSmsTransaction(message);
    }

    /**
     * Enhanced recipient parsing with record validation. Delegates to FileImportService.
     */
    @NonNull
    public List<Recipient> parseAndValidateRecipients(@NonNull List<Map<String, String>> rawData, boolean isLoanData) {
        return fileImportService.parseAndValidateRecipients(rawData, isLoanData);
    }

    /**
     * Enhanced message formatting with dynamic placeholder support
     */
    @NonNull
    public String formatMessageWithDynamicPlaceholders(@NonNull String template, @NonNull Recipient recipient) {
        return formatMessage(template, recipient);
    }

    /**
     * Extract placeholders from template
     */
    @NonNull
    public List<String> extractPlaceholders(@NonNull String template) {
        List<String> placeholders = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(template);
        
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        
        return placeholders;
    }

    /**
     * Validate template placeholders against recipient data
     */
    @NonNull
    public TemplateValidationResult validateTemplatePlaceholders(@NonNull String template, @NonNull List<Recipient> recipients) {
        List<String> placeholders = TemplateValueResolver.extractReferencedNames(template);
        List<String> missingPlaceholders = new ArrayList<>();
        List<String> availablePlaceholders = new ArrayList<>();
        
        if (recipients.isEmpty()) {
            return new TemplateValidationResult(placeholders, placeholders, "No recipients to validate against");
        }
        
        // Get all available fields from first recipient (assuming consistent structure)
        Recipient sampleRecipient = recipients.get(0);
        Map<String, String> allFields = new HashMap<>();
        
        // Add built-in fields
        if (sampleRecipient.getName() != null) allFields.put("name", sampleRecipient.getName());
        if (sampleRecipient.getPhone() != null) allFields.put("phone", sampleRecipient.getPhone());
        if (sampleRecipient.getAmount() != null) allFields.put("amount", String.valueOf(sampleRecipient.getAmount()));
        
        // Add custom fields
        if (sampleRecipient.getFields() != null) {
            allFields.putAll(sampleRecipient.getFields());
        }
        allFields = TemplateValueResolver.canonicalize(allFields);
        
        // Check each placeholder
        for (String placeholder : placeholders) {
            String normalized = TemplateValueResolver.normalizeKey(placeholder);
            if (allFields.containsKey(normalized) || "money".equalsIgnoreCase(placeholder)
                    || "number".equalsIgnoreCase(placeholder) || "date".equalsIgnoreCase(placeholder)) {
                availablePlaceholders.add(placeholder);
            } else {
                missingPlaceholders.add(placeholder);
            }
        }
        
        String message = missingPlaceholders.isEmpty() ? 
            "All placeholders are available" : 
            "Missing placeholders: " + String.join(", ", missingPlaceholders);
        
        return new TemplateValidationResult(availablePlaceholders, missingPlaceholders, message);
    }

    /**
     * Get phone number statistics. Delegates to FileImportService.
     */
    @NonNull
    public PhoneStatistics getPhoneStatistics(@NonNull List<Recipient> recipients) {
        FileImportService.PhoneStatistics fis = fileImportService.getPhoneStatistics(recipients);
        PhoneStatistics stats = new PhoneStatistics();
        stats.totalRecipients = fis.totalRecipients;
        stats.kenyaMobileCount = fis.kenyaMobileCount;
        stats.internationalCount = fis.internationalCount;
        stats.otherCount = fis.otherCount;
        stats.invalidCount = fis.invalidCount;
        return stats;
    }

    /**
     * Normalize phone numbers. Delegates to FileImportService.
     */
    @NonNull
    public List<Recipient> normalizePhoneNumbers(@NonNull List<Recipient> recipients) {
        return fileImportService.normalizePhoneNumbers(recipients);
    }

    /**
     * Parse Excel file with smart mapping. Delegates to parseImportFile.
     */
    @NonNull
    public ExcelParser.ParseResult parseExcelSmart(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        return parseImportFile(uri, fileName);
    }

    /**
     * Detect file type. Delegates to FileImportService.
     */
    @NonNull
    public ExcelParser.ImportFileType detectFileType(@NonNull String fileName) {
        return fileImportService.detectFileType(fileName);
    }

    /**
     * Check if file type is supported. Delegates to FileImportService.
     */
    public boolean isFileTypeSupported(@NonNull String fileName) {
        return fileImportService.isFileTypeSupported(fileName);
    }

    /**
     * Get file type display name. Delegates to FileImportService.
     */
    @NonNull
    public String getFileTypeDisplayName(@NonNull String fileName) {
        return fileImportService.getFileTypeDisplayName(fileName);
    }

    /**
     * Get file preview data. Delegates to FileImportService.
     */
    @NonNull
    public FilePreviewData getFilePreview(@NonNull Uri uri, @NonNull String fileName) throws IOException {
        FileImportService.FilePreviewData fis = fileImportService.getFilePreview(uri, fileName);
        return new FilePreviewData(fis.getHeaders(), fis.getRows(), fis.getTotalRows());
    }

    /**
     * Validate message content
     */
    @NonNull
    public ValidationResult validateMessage(@NonNull String message) {
        if (message.trim().isEmpty()) {
            return new ValidationResult(false, "Message cannot be empty");
        }

        if (message.length() > 1600) {
            return new ValidationResult(false, "Message too long (max 1600 characters)");
        }

        return new ValidationResult(true, "Message is valid");
    }

    /**
     * Legacy send bulk SMS method for backward compatibility
     */
    public void sendBulkSms(
            @NonNull List<Recipient> recipients,
            @NonNull String template,
            int simSlot,
            @Nullable String campaignName,
            @NonNull ProgressCallback progressCallback
    ) {
        sendBulkSmsAsync(recipients, template, simSlot, campaignName, progressCallback)
            .subscribe(
                () -> Log.d(TAG, "Legacy bulk SMS completed"),
                error -> Log.e(TAG, "Legacy bulk SMS failed", error)
            );
    }

    /**
     * Analyze message for optimization suggestions
     */
    @NonNull
    public MessageAnalysis analyzeMessage(@NonNull String message) {
        int originalLength = message.length();
        boolean willConcatenate = originalLength > 160;
        int estimatedParts = willConcatenate ? (int) Math.ceil(originalLength / 153.0) : 1;
        
        DeliveryRisk risk = DeliveryRisk.LOW;
        String suggestion = "";
        
        if (originalLength > 160) {
            risk = DeliveryRisk.MEDIUM;
            suggestion = "Consider shortening message to avoid concatenation";
        }
        
        if (originalLength > 480) {
            risk = DeliveryRisk.HIGH;
            suggestion = "Message is very long, consider splitting into multiple messages";
        }

        return new MessageAnalysis(originalLength, willConcatenate, estimatedParts, risk, suggestion);
    }

    /**
     * Estimate send time for bulk SMS
     */
    public long estimateSendTime(int recipientCount, int sendSpeedPerMinute) {
        // Base time + time per recipient
        long delay = getSendSpeedDelayMs(sendSpeedPerMinute);
        return 1000 + (recipientCount * delay);
    }

    /**
     * Cleanup resources
     * Note: As a singleton, this is managed by Hilt lifecycle
     * Only call shutdown if explicitly needed
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    // ==================== INNER CLASSES ====================

    public static class BulkSendResult {
        public final String status;
        public final int sentCount;
        public final int failedCount;
        public final int skippedCount;
        public final int totalCount;
        public final long campaignId;
        public final int processedCount;
        public final String error;

        public BulkSendResult(String status, int sentCount, int failedCount, int skippedCount,
                              int totalCount, long campaignId, int processedCount, @Nullable String error) {
            this.status = status;
            this.sentCount = sentCount;
            this.failedCount = failedCount;
            this.skippedCount = skippedCount;
            this.totalCount = totalCount;
            this.campaignId = campaignId;
            this.processedCount = processedCount;
            this.error = error;
        }

        public boolean isCompleted() { return RESULT_COMPLETED.equals(status); }
        public boolean isPaused() { return RESULT_PAUSED.equals(status); }
        public boolean isStopped() { return RESULT_STOPPED.equals(status); }
    }

    public static class SimSlotInfo {
        private final int slotIndex;
        private final String carrierName;
        private final String displayName;

        public SimSlotInfo(int slotIndex, String carrierName, String displayName) {
            this.slotIndex = slotIndex;
            this.carrierName = carrierName;
            this.displayName = displayName;
        }

        public int getSlotIndex() { return slotIndex; }
        public String getCarrierName() { return carrierName; }
        public String getDisplayName() { return displayName; }
    }

    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() { return isValid; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class MessageAnalysis {
        private final int originalLength;
        private final boolean willConcatenate;
        private final int estimatedParts;
        private final DeliveryRisk deliveryRisk;
        private final String suggestedOptimization;

        public MessageAnalysis(int originalLength, boolean willConcatenate, int estimatedParts,
                             DeliveryRisk deliveryRisk, String suggestedOptimization) {
            this.originalLength = originalLength;
            this.willConcatenate = willConcatenate;
            this.estimatedParts = estimatedParts;
            this.deliveryRisk = deliveryRisk;
            this.suggestedOptimization = suggestedOptimization;
        }

        public int getOriginalLength() { return originalLength; }
        public boolean getWillConcatenate() { return willConcatenate; }
        public int getEstimatedParts() { return estimatedParts; }
        public DeliveryRisk getDeliveryRisk() { return deliveryRisk; }
        public String getSuggestedOptimization() { return suggestedOptimization; }
    }

    public enum DeliveryRisk {
        LOW, MEDIUM, HIGH
    }

    public static class BillingResult {
        private final boolean success;
        private final String error;
        private final boolean blocked;
        private final String reason;

        public BillingResult(boolean success, String error, boolean blocked, String reason) {
            this.success = success;
            this.error = error;
            this.blocked = blocked;
            this.reason = reason;
        }

        public boolean getSuccess() { return success; }
        public String getError() { return error; }
        public boolean getBlocked() { return blocked; }
        public String getReason() { return reason; }
    }

    public static class SubscriptionInfo {
        private final boolean isActive;
        private final String planName;
        private final Long expiryDate;
        private final int smsLimit;
        private final int smsUsed;
        private final boolean isTrial;
        private final Long trialEndsAt;

        public SubscriptionInfo(boolean isActive, String planName, Long expiryDate,
                              int smsLimit, int smsUsed, boolean isTrial, Long trialEndsAt) {
            this.isActive = isActive;
            this.planName = planName;
            this.expiryDate = expiryDate;
            this.smsLimit = smsLimit;
            this.smsUsed = smsUsed;
            this.isTrial = isTrial;
            this.trialEndsAt = trialEndsAt;
        }

        public boolean isActive() { return isActive; }
        public String getPlanName() { return planName; }
        public Long getExpiryDate() { return expiryDate; }
        public int getSmsLimit() { return smsLimit; }
        public int getSmsUsed() { return smsUsed; }
        public boolean isTrial() { return isTrial; }
        public Long getTrialEndsAt() { return trialEndsAt; }
    }

    public static class FilePreviewData {
        private final List<String> headers;
        private final List<List<String>> rows;
        private final int totalRows;

        public FilePreviewData(List<String> headers, List<List<String>> rows, int totalRows) {
            this.headers = headers;
            this.rows = rows;
            this.totalRows = totalRows;
        }

        public List<String> getHeaders() { return headers; }
        public List<List<String>> getRows() { return rows; }
        public int getTotalRows() { return totalRows; }
    }

    /**
     * Template validation result data class
     */
    public static class TemplateValidationResult {
        private final List<String> availablePlaceholders;
        private final List<String> missingPlaceholders;
        private final String message;

        public TemplateValidationResult(List<String> availablePlaceholders, List<String> missingPlaceholders, String message) {
            this.availablePlaceholders = availablePlaceholders;
            this.missingPlaceholders = missingPlaceholders;
            this.message = message;
        }

        public List<String> getAvailablePlaceholders() { return availablePlaceholders; }
        public List<String> getMissingPlaceholders() { return missingPlaceholders; }
        public String getMessage() { return message; }
        public boolean isValid() { return missingPlaceholders.isEmpty(); }
    }

    /**
     * Phone statistics data class
     */
    public static class PhoneStatistics {
        public int totalRecipients = 0;
        public int kenyaMobileCount = 0;
        public int internationalCount = 0;
        public int otherCount = 0;
        public int invalidCount = 0;

        public double getKenyaMobilePercentage() {
            return totalRecipients > 0 ? (double) kenyaMobileCount / totalRecipients * 100 : 0;
        }

        public double getInternationalPercentage() {
            return totalRecipients > 0 ? (double) internationalCount / totalRecipients * 100 : 0;
        }

        public double getValidPercentage() {
            return totalRecipients > 0 ? (double) (kenyaMobileCount + internationalCount) / totalRecipients * 100 : 0;
        }

        @Override
        public String toString() {
            return "PhoneStatistics{" +
                   "total=" + totalRecipients +
                   ", kenya=" + kenyaMobileCount + " (" + String.format("%.1f", getKenyaMobilePercentage()) + "%)" +
                   ", international=" + internationalCount + " (" + String.format("%.1f", getInternationalPercentage()) + "%)" +
                   ", valid=" + (kenyaMobileCount + internationalCount) + " (" + String.format("%.1f", getValidPercentage()) + "%)" +
                   ", invalid=" + invalidCount +
                   '}';
        }
    }

    /**
     * Error information data class
     */
    public static class ErrorInfo {
        private final String message;
        private final String code;
        private final long timestamp;
        private final Map<String, Object> context;

        public ErrorInfo(String message, String code, long timestamp, Map<String, Object> context) {
            this.message = message;
            this.code = code;
            this.timestamp = timestamp;
            this.context = context;
        }

        public String getMessage() { return message; }
        public String getCode() { return code; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getContext() { return context; }
    }
}
