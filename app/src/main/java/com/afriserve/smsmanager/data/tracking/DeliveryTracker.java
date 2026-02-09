package com.afriserve.smsmanager.data.tracking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Delivery tracking system for SMS messages
 * Tracks sent, delivered, and failed messages using Android's SMS APIs
 */
@Singleton
public class DeliveryTracker {
    
    private static final String TAG = "DeliveryTracker";
    
    // Delivery status tracking
    private final Map<String, DeliveryStatus> deliveryStatusMap = new ConcurrentHashMap<>();
    private final MutableLiveData<DeliveryStats> _deliveryStats = new MutableLiveData<>();
    public final LiveData<DeliveryStats> deliveryStats = _deliveryStats;
    
    // Broadcast receivers for delivery tracking
    private final SmsSentReceiver sentReceiver;
    private final SmsDeliveredReceiver deliveredReceiver;
    
    private final Context context;
    private final SmsDao smsDao;
    
    @Inject
    public DeliveryTracker(@ApplicationContext Context context, SmsDao smsDao) {
        this.context = context;
        this.smsDao = smsDao;
        this.sentReceiver = new SmsSentReceiver();
        this.deliveredReceiver = new SmsDeliveredReceiver();
        
        registerReceivers();
        Log.d(TAG, "Delivery tracker initialized");
    }
    
    /**
     * Register broadcast receivers for delivery tracking
     */
    private void registerReceivers() {
        try {
            // Register sent receiver
            IntentFilter sentFilter = new IntentFilter("com.afriserve.smsmanager.SMS_SENT");
            ContextCompat.registerReceiver(context, sentReceiver, sentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            
            // Register delivered receiver  
            IntentFilter deliveredFilter = new IntentFilter("com.afriserve.smsmanager.SMS_DELIVERED");
            ContextCompat.registerReceiver(context, deliveredReceiver, deliveredFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            
            Log.d(TAG, "Delivery tracking receivers registered");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to register delivery receivers", e);
        }
    }
    
    /**
     * Unregister broadcast receivers
     */
    public void unregisterReceivers() {
        try {
            context.unregisterReceiver(sentReceiver);
            context.unregisterReceiver(deliveredReceiver);
            Log.d(TAG, "Delivery tracking receivers unregistered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister delivery receivers", e);
        }
    }
    
    /**
     * Create pending intents for delivery tracking
     */
    public DeliveryIntents createDeliveryIntents(String messageId) {
        try {
            // Create pending intents for sent and delivered callbacks
            Intent sentIntent = new Intent("com.afriserve.smsmanager.SMS_SENT");
            sentIntent.setPackage(context.getPackageName());
            sentIntent.putExtra("messageId", messageId);
            
            Intent deliveredIntent = new Intent("com.afriserve.smsmanager.SMS_DELIVERED");
            deliveredIntent.setPackage(context.getPackageName());
            deliveredIntent.putExtra("messageId", messageId);
            
            android.app.PendingIntent sentPendingIntent = android.app.PendingIntent.getBroadcast(
                context, 
                messageId.hashCode(), 
                sentIntent, 
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT
            );
            
            android.app.PendingIntent deliveredPendingIntent = android.app.PendingIntent.getBroadcast(
                context, 
                messageId.hashCode() + 1, 
                deliveredIntent, 
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT
            );
            
            return new DeliveryIntents(sentPendingIntent, deliveredPendingIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create delivery intents", e);
            return null;
        }
    }
    
    /**
     * Track message status
     */
    public void trackMessageStatus(String messageId, String phoneNumber, DeliveryStatus status) {
        deliveryStatusMap.put(messageId, status);
        
        // Update database
        updateMessageStatusInDatabase(messageId, status);
        
        // Update statistics
        updateDeliveryStats();
        
        Log.d(TAG, "Tracked status for message " + messageId + ": " + status);
    }
    
    /**
     * Get delivery status for message
     */
    public Single<DeliveryStatus> getDeliveryStatus(String messageId) {
        return Single.fromCallable(() -> {
            DeliveryStatus status = deliveryStatusMap.get(messageId);
            if (status == null) {
                // Try to get from database
                try {
                    SmsEntity sms = smsDao.getSmsByDeviceSmsId(Long.parseLong(messageId)).blockingGet();
                    if (sms != null) {
                        status = mapEntityToDeliveryStatus(sms);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get status from database", e);
                }
            }
            return status != null ? status : DeliveryStatus.UNKNOWN;
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Get delivery statistics
     */
    public Single<DeliveryStats> getDeliveryStatistics() {
        return Single.fromCallable(() -> {
            try {
                int totalSent = smsDao.getTotalCount().getValue() != null ? smsDao.getTotalCount().getValue() : 0;
                int totalDelivered = smsDao.getDeliveredCount().getValue() != null ? smsDao.getDeliveredCount().getValue() : 0;
                int totalFailed = smsDao.getFailedCount().getValue() != null ? smsDao.getFailedCount().getValue() : 0;
                int totalPending = smsDao.getPendingCount().getValue() != null ? smsDao.getPendingCount().getValue() : 0;
                
                double deliveryRate = totalSent > 0 ? (double) totalDelivered / totalSent * 100 : 0.0;
                
                return new DeliveryStats(totalSent, totalDelivered, totalFailed, totalPending, deliveryRate);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get delivery statistics", e);
                return new DeliveryStats(0, 0, 0, 0, 0.0);
            }
        }).subscribeOn(Schedulers.io());
    }
    
    /**
     * Update message status in database
     */
    private void updateMessageStatusInDatabase(String messageId, DeliveryStatus status) {
        Completable.fromAction(() -> {
            try {
                long smsId = Long.parseLong(messageId);
                SmsEntity sms = smsDao.getSmsById(smsId).blockingGet();
                
                if (sms != null) {
                    switch (status) {
                        case SENT:
                            sms.status = "SENT";
                            sms.sentAt = System.currentTimeMillis();
                            break;
                        case DELIVERED:
                            sms.status = "DELIVERED";
                            sms.deliveredAt = System.currentTimeMillis();
                            break;
                        case FAILED:
                            sms.status = "FAILED";
                            sms.sentAt = System.currentTimeMillis();
                            break;
                    }
                    
                    smsDao.updateSms(sms).blockingAwait();
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update message status in database", e);
            }
        }).subscribeOn(Schedulers.io()).subscribe(
            () -> Log.d(TAG, "Updated message status in database"),
            error -> Log.e(TAG, "Failed to update message status", error)
        );
    }
    
    /**
     * Update delivery statistics
     */
    private void updateDeliveryStats() {
        getDeliveryStatistics()
            .subscribe(
                stats -> _deliveryStats.postValue(stats),
                error -> Log.e(TAG, "Failed to update delivery stats", error)
            );
    }
    
    /**
     * Map SMS entity to delivery status
     */
    private DeliveryStatus mapEntityToDeliveryStatus(SmsEntity sms) {
        if ("DELIVERED".equals(sms.status)) {
            return DeliveryStatus.DELIVERED;
        } else if ("SENT".equals(sms.status)) {
            return DeliveryStatus.SENT;
        } else if ("FAILED".equals(sms.status)) {
            return DeliveryStatus.FAILED;
        } else if ("PENDING".equals(sms.status)) {
            return DeliveryStatus.PENDING;
        }
        return DeliveryStatus.UNKNOWN;
    }
    
    /**
     * SMS sent broadcast receiver
     */
    private class SmsSentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String messageId = intent.getStringExtra("messageId");
                if (messageId != null) {
                    trackMessageStatus(messageId, null, DeliveryStatus.SENT);
                    Log.d(TAG, "SMS sent: " + messageId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in sent receiver", e);
            }
        }
    }
    
    /**
     * SMS delivered broadcast receiver
     */
    private class SmsDeliveredReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String messageId = intent.getStringExtra("messageId");
                if (messageId != null) {
                    trackMessageStatus(messageId, null, DeliveryStatus.DELIVERED);
                    Log.d(TAG, "SMS delivered: " + messageId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in delivered receiver", e);
            }
        }
    }
    
    /**
     * Delivery intents container
     */
    public static class DeliveryIntents {
        public final android.app.PendingIntent sentIntent;
        public final android.app.PendingIntent deliveredIntent;
        
        public DeliveryIntents(android.app.PendingIntent sentIntent, android.app.PendingIntent deliveredIntent) {
            this.sentIntent = sentIntent;
            this.deliveredIntent = deliveredIntent;
        }
    }
    
    /**
     * Delivery status enum
     */
    public enum DeliveryStatus {
        PENDING,
        SENT,
        DELIVERED,
        FAILED,
        UNKNOWN
    }
    
    /**
     * Delivery statistics
     */
    public static class DeliveryStats {
        public final int totalSent;
        public final int totalDelivered;
        public final int totalFailed;
        public final int totalPending;
        public final double deliveryRate;
        
        public DeliveryStats(int totalSent, int totalDelivered, int totalFailed, int totalPending, double deliveryRate) {
            this.totalSent = totalSent;
            this.totalDelivered = totalDelivered;
            this.totalFailed = totalFailed;
            this.totalPending = totalPending;
            this.deliveryRate = deliveryRate;
        }
        
        @Override
        public String toString() {
            return "DeliveryStats{" +
                    "totalSent=" + totalSent +
                    ", totalDelivered=" + totalDelivered +
                    ", totalFailed=" + totalFailed +
                    ", totalPending=" + totalPending +
                    ", deliveryRate=" + String.format("%.2f%%", deliveryRate) +
                    '}';
        }
    }
}
