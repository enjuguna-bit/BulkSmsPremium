package com.afriserve.smsmanager.data.tracking;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.dao.CampaignDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.queue.SmsQueueManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Enhanced Delivery Tracker with comprehensive SMS delivery tracking
 * Handles sent, delivered, and failed events with automatic retry logic
 */
@Singleton
public class EnhancedDeliveryTracker {
    private static final String TAG = "EnhancedDeliveryTracker";

    // Intent actions for delivery tracking
    private static final String SMS_SENT_ACTION = "com.afriserve.smsmanager.SMS_SENT";
    private static final String SMS_DELIVERED_ACTION = "com.afriserve.smsmanager.SMS_DELIVERED";

    // Intent extras
    private static final String EXTRA_SMS_ID = "sms_id";
    private static final String EXTRA_RETRY_COUNT = "retry_count";
    private static final String EXTRA_TIMESTAMP = "timestamp";

    private final Context context;
    private final SmsDao smsDao;
    private final Provider<SmsQueueManager> queueManagerProvider;
    private final CampaignDao campaignDao;
    private final Handler mainHandler;
    private final AtomicInteger requestCodeGenerator = new AtomicInteger(1000);

    // Tracking maps for pending intents
    private final Map<Integer, Long> pendingSentIntents = new ConcurrentHashMap<>();
    private final Map<Integer, Long> pendingDeliveredIntents = new ConcurrentHashMap<>();
    private final Map<Long, Integer> pendingSimSlots = new ConcurrentHashMap<>();
    private final Map<Long, String> pendingRecipientNames = new ConcurrentHashMap<>();

    // Android may emit one callback per multipart segment. These sets make the
    // UI statistics recipient/message based rather than segment/callback based.
    private final Set<Long> pendingSmsIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Long> handledSentSmsIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Long> handledDeliveredSmsIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Delivery statistics
    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger deliveredCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    // Broadcast receivers
    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleSmsSent(intent, getResultCode());
        }
    };

    private final BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleSmsDelivered(intent, getResultCode());
        }
    };

    private boolean isRegistered = false;
    private volatile DeliveryEventListener deliveryEventListener;

    public interface DeliveryEventListener {
        void onDelivered(long smsId, @Nullable String recipientName);
    }

    public void setDeliveryEventListener(@Nullable DeliveryEventListener listener) {
        this.deliveryEventListener = listener;
    }

    @Inject
    public EnhancedDeliveryTracker(@dagger.hilt.android.qualifiers.ApplicationContext Context context, SmsDao smsDao,
            CampaignDao campaignDao,
            Provider<SmsQueueManager> queueManagerProvider) {
        this.context = context;
        this.smsDao = smsDao;
        this.campaignDao = campaignDao;
        this.queueManagerProvider = queueManagerProvider;
        this.mainHandler = new Handler(Looper.getMainLooper());

        registerReceivers();
    }

    /**
     * Create delivery tracking intents for SMS
     */
    @NonNull
    public DeliveryIntents createDeliveryIntents(@NonNull String smsId) {
        return createDeliveryIntents(smsId, 0);
    }

    /** Create callbacks while retaining the SIM slot for an in-process retry. */
    @NonNull
    public DeliveryIntents createDeliveryIntents(@NonNull String smsId, int simSlot) {
        return createDeliveryIntents(smsId, simSlot, null);
    }

    /** Create callbacks and retain the display name for delivery notifications. */
    @NonNull
    public DeliveryIntents createDeliveryIntents(@NonNull String smsId, int simSlot,
            @Nullable String recipientName) {
        int requestCode = requestCodeGenerator.incrementAndGet();
        long numericSmsId = Long.parseLong(smsId);
        pendingSimSlots.put(numericSmsId, simSlot);
        if (recipientName != null && !recipientName.trim().isEmpty()) {
            pendingRecipientNames.put(numericSmsId, recipientName.trim());
        } else {
            pendingRecipientNames.remove(numericSmsId);
        }

        // A retry starts a new logical attempt for this SMS record.
        pendingSmsIds.add(numericSmsId);
        handledSentSmsIds.remove(numericSmsId);
        handledDeliveredSmsIds.remove(numericSmsId);

        // Create sent intent
        Intent sentIntent = new Intent(SMS_SENT_ACTION);
        sentIntent.setPackage(context.getPackageName());
        // Keep the callback contract numeric. The manifest receiver reads this
        // extra with getLongExtra(), and Android does not coerce String extras.
        sentIntent.putExtra(EXTRA_SMS_ID, numericSmsId);
        sentIntent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());

        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
                context, requestCode, sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create delivered intent
        Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
        deliveredIntent.setPackage(context.getPackageName());
        deliveredIntent.putExtra(EXTRA_SMS_ID, numericSmsId);
        deliveredIntent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());

        int deliveredRequestCode = requestCodeGenerator.incrementAndGet();
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
                context, deliveredRequestCode, deliveredIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Track pending intents
        pendingSentIntents.put(requestCode, numericSmsId);
        pendingDeliveredIntents.put(deliveredRequestCode, numericSmsId);

        pendingCount.set(pendingSmsIds.size());

        Log.d(TAG, "Created delivery intents for SMS ID: " + smsId);

        return new DeliveryIntents(sentPendingIntent, deliveredPendingIntent);
    }

    /**
     * Handle SMS sent event
     */
    private void handleSmsSent(@NonNull Intent intent, int resultCode) {
        try {
            long smsId = getSmsId(intent);
            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0);

            if (smsId <= 0) {
                Log.w(TAG, "Received sent intent without SMS ID");
                return;
            }

            // Multipart sends reuse the same PendingIntent for every segment.
            // Only the first callback represents the recipient-level send.
            if (!handledSentSmsIds.add(smsId)) {
                Log.d(TAG, "Ignoring duplicate/multipart sent callback for SMS ID: " + smsId);
                return;
            }

            Log.d(TAG, "SMS sent event - ID: " + smsId + ", Result: " + getResultCodeString(resultCode));

            // Update SMS entity based on result
            updateSmsSentStatus(smsId, resultCode, timestamp);

            // Update statistics
            if (resultCode == android.app.Activity.RESULT_OK) {
                sentCount.incrementAndGet();
            } else {
                failedCount.incrementAndGet();
                // Enqueue for retry if sending failed
                enqueueForRetry(smsId, "Send failed: " + getResultCodeString(resultCode));
            }

            pendingSmsIds.remove(smsId);
            pendingCount.set(pendingSmsIds.size());

            // Clean up tracking
            cleanupPendingIntents(smsId);

            maybeCompleteCampaign(smsId);

        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS sent event", e);
        }
    }

    /**
     * Handle SMS delivered event
     */
    private void handleSmsDelivered(@NonNull Intent intent, int resultCode) {
        try {
            long smsId = getSmsId(intent);
            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0);

            if (smsId <= 0) {
                Log.w(TAG, "Received delivered intent without SMS ID");
                return;
            }

            // Delivery callbacks can be repeated for multipart segments or by
            // the carrier. Count delivery once per logical SMS record.
            if (!handledDeliveredSmsIds.add(smsId)) {
                Log.d(TAG, "Ignoring duplicate/multipart delivered callback for SMS ID: " + smsId);
                return;
            }

            Log.d(TAG, "SMS delivered event - ID: " + smsId + ", Result: " + getResultCodeString(resultCode));

            // Update SMS entity
            updateSmsDeliveredStatus(smsId, resultCode, timestamp);

            // Update statistics
            if (resultCode == android.app.Activity.RESULT_OK) {
                deliveredCount.incrementAndGet();
                DeliveryEventListener listener = deliveryEventListener;
                String recipientName = pendingRecipientNames.remove(smsId);
                if (listener != null) {
                    mainHandler.post(() -> listener.onDelivered(smsId, recipientName));
                }
            } else {
                // Delivery failed but SMS was sent - no retry needed for delivery failure
                Log.w(TAG, "SMS delivery failed for ID: " + smsId);
            }

            // Clean up tracking
            pendingDeliveredIntents.entrySet().removeIf(entry -> entry.getValue().equals(smsId));
            pendingSimSlots.remove(smsId);

            maybeCompleteCampaign(smsId);

        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS delivered event", e);
        }
    }

    /** Complete a campaign only after all SMS rows have received a terminal send result. */
    private void maybeCompleteCampaign(long smsId) {
        Completable.fromAction(() -> {
            SmsEntity sms = smsDao.getSmsById(smsId).blockingGet();
            if (sms == null || sms.campaignId == null || sms.campaignId <= 0) return;
            int pending = smsDao.getNonTerminalCountByCampaign(sms.campaignId).blockingGet();
            if (pending == 0) {
                campaignDao.completeCampaign(sms.campaignId, System.currentTimeMillis()).blockingAwait();
                Log.d(TAG, "Campaign completed after delivery callbacks: " + sms.campaignId);
            }
        }).subscribeOn(Schedulers.io()).subscribe(
                () -> { }, error -> Log.w(TAG, "Failed to finalize campaign callback state", error));
    }

    /**
     * Read both the current long form and the string form emitted by older
     * builds, so an in-flight campaign can finish after an app upgrade.
     */
    private long getSmsId(@NonNull Intent intent) {
        Object value = intent.getExtras() != null ? intent.getExtras().get(EXTRA_SMS_ID) : null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                // Fall through to the invalid-ID result.
            }
        }
        return -1L;
    }

    /**
     * Update SMS entity after send attempt
     */
    private void updateSmsSentStatus(long smsId, int resultCode, long timestamp) {
        Completable.fromAction(() -> {
            try {
                SmsEntity smsEntity = smsDao.getSmsById(smsId).blockingGet();
                if (smsEntity != null) {
                    String previousStatus = smsEntity.status;
                    Long campaignId = smsEntity.campaignId;
                    smsEntity.sentAt = timestamp;

                    if (resultCode == android.app.Activity.RESULT_OK) {
                        smsEntity.status = "SENT";
                        smsEntity.errorMessage = null;
                        smsEntity.errorCode = null;
                    } else {
                        smsEntity.status = "FAILED";
                        smsEntity.errorMessage = "Send failed: " + getResultCodeString(resultCode);
                        smsEntity.errorCode = "SEND_ERROR_" + resultCode;
                    }

                    smsDao.updateSms(smsEntity).blockingAwait();
                    if (campaignId != null && campaignId > 0 && resultCode == android.app.Activity.RESULT_OK
                            && (previousStatus == null || (!"SENT".equals(previousStatus)
                            && !"DELIVERED".equals(previousStatus)))) {
                        try {
                            campaignDao.incrementSentCount(campaignId).blockingAwait();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to update campaign sent count", e);
                        }
                    } else if (campaignId != null && campaignId > 0 && resultCode != android.app.Activity.RESULT_OK
                            && (previousStatus == null || !"FAILED".equals(previousStatus))) {
                        try {
                            campaignDao.incrementFailedCount(campaignId).blockingAwait();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to update campaign failed count", e);
                        }
                    }
                    Log.d(TAG, "Updated SMS sent status: " + smsId + " -> " + smsEntity.status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating SMS sent status", e);
            }
        }).subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Log.d(TAG, "SMS sent status updated successfully"),
                        error -> Log.e(TAG, "Failed to update SMS sent status", error));
    }

    /**
     * Update SMS entity after delivery attempt
     */
    private void updateSmsDeliveredStatus(long smsId, int resultCode, long timestamp) {
        Completable.fromAction(() -> {
            try {
                SmsEntity smsEntity = smsDao.getSmsById(smsId).blockingGet();
                if (smsEntity != null) {
                    String previousStatus = smsEntity.status;
                    Long campaignId = smsEntity.campaignId;
                    smsEntity.deliveredAt = timestamp;

                    if (resultCode == android.app.Activity.RESULT_OK) {
                        smsEntity.status = "DELIVERED";
                        smsEntity.errorMessage = null;
                        smsEntity.errorCode = null;
                    } else {
                        smsEntity.status = "DELIVERY_FAILED";
                        smsEntity.errorMessage = "Delivery failed: " + getResultCodeString(resultCode);
                        smsEntity.errorCode = "DELIVERY_ERROR_" + resultCode;
                    }

                    smsDao.updateSms(smsEntity).blockingAwait();
                    if (campaignId != null && campaignId > 0 && resultCode == android.app.Activity.RESULT_OK
                            && (previousStatus == null || !"DELIVERED".equals(previousStatus))) {
                        try {
                            campaignDao.incrementDeliveredCount(campaignId).blockingAwait();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to update campaign delivered count", e);
                        }
                    }
                    Log.d(TAG, "Updated SMS delivery status: " + smsId + " -> " + smsEntity.status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating SMS delivery status", e);
            }
        }).subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Log.d(TAG, "SMS delivery status updated successfully"),
                        error -> Log.e(TAG, "Failed to update SMS delivery status", error));
    }

    /**
     * Enqueue SMS for retry
     */
    private void enqueueForRetry(long smsId, @NonNull String reason) {
        Completable.fromAction(() -> {
            try {
                SmsEntity smsEntity = smsDao.getSmsById(smsId).blockingGet();
                if (smsEntity != null) {
                    // Enqueue for retry using queue manager
                    int retrySimSlot = pendingSimSlots.getOrDefault(smsId, smsEntity.simSlot);
                    queueManagerProvider.get().enqueueSms(smsEntity.phoneNumber, smsEntity.message, retrySimSlot, smsId)
                            .subscribe(
                                    () -> Log.d(TAG, "Enqueued SMS for retry: " + smsId),
                                    error -> Log.e(TAG, "Failed to enqueue SMS for retry", error));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enqueueing SMS for retry", e);
            }
        }).subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * Enqueue a failed SMS directly for retry (used by bulk send failure paths).
     */
    public void enqueueForRetryManual(@NonNull String phoneNumber, @NonNull String message, int simSlot,
                                      @Nullable Long smsId, @Nullable String reason) {
        try {
            queueManagerProvider.get().enqueueSms(phoneNumber, message, simSlot, smsId)
                    .subscribe(
                            () -> Log.d(TAG, "Enqueued SMS for retry (manual): " + phoneNumber),
                            error -> Log.e(TAG, "Failed to enqueue SMS for retry (manual)", error));
        } catch (Exception e) {
            Log.e(TAG, "Error enqueueing SMS for retry (manual)", e);
        }
    }

    /**
     * Clean up pending intents tracking
     */
    private void cleanupPendingIntents(long smsId) {
        pendingSentIntents.entrySet().removeIf(entry -> entry.getValue().equals(smsId));
    }

    /**
     * Get result code string for logging
     */
    @NonNull
    private String getResultCodeString(int resultCode) {
        switch (resultCode) {
            case android.app.Activity.RESULT_OK:
                return "SUCCESS";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "GENERIC_FAILURE";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "RADIO_OFF";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "NULL_PDU";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "NO_SERVICE";
            case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                return "LIMIT_EXCEEDED";
            case SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE:
                return "FDN_CHECK_FAILURE";
            default:
                return "UNKNOWN(" + resultCode + ")";
        }
    }

    /**
     * Get current delivery statistics
     */
    @NonNull
    public DeliveryStatistics getDeliveryStatistics() {
        return new DeliveryStatistics(
                sentCount.get(),
                deliveredCount.get(),
                failedCount.get(),
                pendingCount.get());
    }

    /**
     * Reset statistics
     */
    public void resetStatistics() {
        sentCount.set(0);
        deliveredCount.set(0);
        failedCount.set(0);
        pendingCount.set(0);
        pendingSmsIds.clear();
        handledSentSmsIds.clear();
        handledDeliveredSmsIds.clear();
        pendingSentIntents.clear();
        pendingDeliveredIntents.clear();
        pendingSimSlots.clear();
        pendingRecipientNames.clear();
        Log.d(TAG, "Delivery statistics reset");
    }

    /**
     * Register broadcast receivers
     */
    private void registerReceivers() {
        if (isRegistered) {
            return;
        }

        try {
            IntentFilter sentFilter = new IntentFilter(SMS_SENT_ACTION);
            IntentFilter deliveredFilter = new IntentFilter(SMS_DELIVERED_ACTION);

            ContextCompat.registerReceiver(context, sentReceiver, sentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            ContextCompat.registerReceiver(context, deliveredReceiver, deliveredFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

            isRegistered = true;
            Log.d(TAG, "Delivery tracking receivers registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register delivery receivers", e);
        }
    }

    /**
     * Unregister broadcast receivers
     */
    public void unregisterReceivers() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(sentReceiver);
                context.unregisterReceiver(deliveredReceiver);
                isRegistered = false;
                Log.d(TAG, "Delivery tracking receivers unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receivers already unregistered", e);
            }
        }
    }

    /**
     * Data class for delivery intents
     */
    public static class DeliveryIntents {
        public final PendingIntent sentIntent;
        public final PendingIntent deliveredIntent;

        public DeliveryIntents(@Nullable PendingIntent sentIntent, @Nullable PendingIntent deliveredIntent) {
            this.sentIntent = sentIntent;
            this.deliveredIntent = deliveredIntent;
        }
    }

    /**
     * Data class for delivery statistics
     */
    public static class DeliveryStatistics {
        public final int sentCount;
        public final int deliveredCount;
        public final int failedCount;
        public final int pendingCount;

        public DeliveryStatistics(int sentCount, int deliveredCount, int failedCount, int pendingCount) {
            this.sentCount = sentCount;
            this.deliveredCount = deliveredCount;
            this.failedCount = failedCount;
            this.pendingCount = pendingCount;
        }

        public double getDeliveryRate() {
            return sentCount > 0 ? (double) deliveredCount / sentCount * 100 : 0;
        }

        public double getSuccessRate() {
            return sentCount > 0 ? (double) (sentCount - failedCount) / sentCount * 100 : 0;
        }

        // Backwards-compatible getters
        public int getDeliveredCount() { return deliveredCount; }
        public int getPendingCount() { return pendingCount; }
        public int getFailedCount() { return failedCount; }
        public int getSentCount() { return sentCount; }
    }
}
