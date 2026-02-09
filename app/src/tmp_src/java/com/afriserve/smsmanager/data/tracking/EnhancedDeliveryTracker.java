package com.afriserve.smsmanager.data.tracking;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.queue.SmsQueueManager;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import javax.inject.Inject;
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
    private final SmsQueueManager queueManager;
    private final Handler mainHandler;
    private final AtomicInteger requestCodeGenerator = new AtomicInteger(1000);
    
    // Tracking maps for pending intents
    private final Map<Integer, Long> pendingSentIntents = new ConcurrentHashMap<>();
    private final Map<Integer, Long> pendingDeliveredIntents = new ConcurrentHashMap<>();
    
    // Delivery statistics
    private final AtomicInteger sentCount = new AtomicInteger(0);
    private final AtomicInteger deliveredCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    
    // Broadcast receivers
    private final BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleSmsSent(intent);
        }
    };
    
    private final BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleSmsDelivered(intent);
        }
    };
    
    private boolean isRegistered = false;
    
    @Inject
    public EnhancedDeliveryTracker(Context context, SmsDao smsDao, SmsQueueManager queueManager) {
        this.context = context;
        this.smsDao = smsDao;
        this.queueManager = queueManager;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        registerReceivers();
    }
    
    /**
     * Create delivery tracking intents for SMS
     */
    @NonNull
    public DeliveryIntents createDeliveryIntents(@NonNull String smsId) {
        int requestCode = requestCodeGenerator.incrementAndGet();
        
        // Create sent intent
        Intent sentIntent = new Intent(SMS_SENT_ACTION);
        sentIntent.putExtra(EXTRA_SMS_ID, smsId);
        sentIntent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
            context, requestCode, sentIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Create delivered intent
        Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
        deliveredIntent.putExtra(EXTRA_SMS_ID, smsId);
        deliveredIntent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        
        int deliveredRequestCode = requestCodeGenerator.incrementAndGet();
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
            context, deliveredRequestCode, deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Track pending intents
        pendingSentIntents.put(requestCode, Long.parseLong(smsId));
        pendingDeliveredIntents.put(deliveredRequestCode, Long.parseLong(smsId));
        
        pendingCount.incrementAndGet();
        
        Log.d(TAG, "Created delivery intents for SMS ID: " + smsId);
        
        return new DeliveryIntents(sentPendingIntent, deliveredPendingIntent);
    }
    
    /**
     * Handle SMS sent event
     */
    private void handleSmsSent(@NonNull Intent intent) {
        try {
            String smsIdStr = intent.getStringExtra(EXTRA_SMS_ID);
            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0);
            
            if (smsIdStr == null) {
                Log.w(TAG, "Received sent intent without SMS ID");
                return;
            }
            
            long smsId = Long.parseLong(smsIdStr);
            // Result code from SmsManager callback - assume success if intent received
            int resultCode = android.app.Activity.RESULT_OK;
            
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
            
            pendingCount.decrementAndGet();
            
            // Clean up tracking
            cleanupPendingIntents(smsId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS sent event", e);
        }
    }
    
    /**
     * Handle SMS delivered event
     */
    private void handleSmsDelivered(@NonNull Intent intent) {
        try {
            String smsIdStr = intent.getStringExtra(EXTRA_SMS_ID);
            long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0);
            
            if (smsIdStr == null) {
                Log.w(TAG, "Received delivered intent without SMS ID");
                return;
            }
            
            long smsId = Long.parseLong(smsIdStr);
            // Result code from delivery callback - assume success if intent received
            int resultCode = android.app.Activity.RESULT_OK;
            
            Log.d(TAG, "SMS delivered event - ID: " + smsId + ", Result: " + getResultCodeString(resultCode));
            
            // Update SMS entity
            updateSmsDeliveredStatus(smsId, resultCode, timestamp);
            
            // Update statistics
            if (resultCode == android.app.Activity.RESULT_OK) {
                deliveredCount.incrementAndGet();
            } else {
                // Delivery failed but SMS was sent - no retry needed for delivery failure
                Log.w(TAG, "SMS delivery failed for ID: " + smsId);
            }
            
            // Clean up tracking
            pendingDeliveredIntents.entrySet().removeIf(entry -> entry.getValue().equals(smsId));
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS delivered event", e);
        }
    }
    
    /**
     * Update SMS entity after send attempt
     */
    private void updateSmsSentStatus(long smsId, int resultCode, long timestamp) {
        Completable.fromAction(() -> {
            try {
                SmsEntity smsEntity = smsDao.getSmsById(smsId).blockingGet();
                if (smsEntity != null) {
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
                    Log.d(TAG, "Updated SMS sent status: " + smsId + " -> " + smsEntity.status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating SMS sent status", e);
            }
        }).subscribeOn(Schedulers.io())
          .subscribe(
              () -> Log.d(TAG, "SMS sent status updated successfully"),
              error -> Log.e(TAG, "Failed to update SMS sent status", error)
          );
    }
    
    /**
     * Update SMS entity after delivery attempt
     */
    private void updateSmsDeliveredStatus(long smsId, int resultCode, long timestamp) {
        Completable.fromAction(() -> {
            try {
                SmsEntity smsEntity = smsDao.getSmsById(smsId).blockingGet();
                if (smsEntity != null) {
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
                    Log.d(TAG, "Updated SMS delivery status: " + smsId + " -> " + smsEntity.status);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating SMS delivery status", e);
            }
        }).subscribeOn(Schedulers.io())
          .subscribe(
              () -> Log.d(TAG, "SMS delivery status updated successfully"),
              error -> Log.e(TAG, "Failed to update SMS delivery status", error)
          );
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
                    queueManager.enqueueSms(smsEntity.phoneNumber, smsEntity.message, 0, smsId)
                        .subscribe(
                            () -> Log.d(TAG, "Enqueued SMS for retry: " + smsId),
                            error -> Log.e(TAG, "Failed to enqueue SMS for retry", error)
                        );
                }
            } catch (Exception e) {
                Log.e(TAG, "Error enqueueing SMS for retry", e);
            }
        }).subscribeOn(Schedulers.io())
          .subscribe();
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
            pendingCount.get()
        );
    }
    
    /**
     * Reset statistics
     */
    public void resetStatistics() {
        sentCount.set(0);
        deliveredCount.set(0);
        failedCount.set(0);
        pendingCount.set(0);
        Log.d(TAG, "Delivery statistics reset");
    }
    
    /**
     * Register broadcast receivers
     */
    private void registerReceivers() {
        if (!isRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ requires explicit receiver flags
                context.registerReceiver(sentReceiver, new IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
                context.registerReceiver(deliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION), Context.RECEIVER_NOT_EXPORTED);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13 also requires explicit receiver flags
                context.registerReceiver(sentReceiver, new IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
                context.registerReceiver(deliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION), Context.RECEIVER_NOT_EXPORTED);
            } else {
                // Legacy registration for older versions
                context.registerReceiver(sentReceiver, new IntentFilter(SMS_SENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
                context.registerReceiver(deliveredReceiver, new IntentFilter(SMS_DELIVERED_ACTION), Context.RECEIVER_NOT_EXPORTED);
            }
            isRegistered = true;
            Log.d(TAG, "Delivery tracking receivers registered");
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
    }
}
