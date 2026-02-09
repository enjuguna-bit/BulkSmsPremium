package com.afriserve.smsmanager.workers;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.sync.BidirectionalSmsSync;
import io.reactivex.rxjava3.core.Single;
import androidx.hilt.work.HiltWorker;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * Worker for SMS delivery tracking
 * Handles SMS sent/delivery status updates reliably
 */
@HiltWorker
public class SmsDeliveryWorker extends Worker {
    
    private static final String TAG = "SmsDeliveryWorker";
    
    private final SmsDao smsDao;
    private final BidirectionalSmsSync bidirectionalSmsSync;
    
    @AssistedInject
    public SmsDeliveryWorker(@Assisted @NonNull Context context,
            @Assisted @NonNull WorkerParameters params,
            SmsDao smsDao,
            BidirectionalSmsSync bidirectionalSmsSync) {
        super(context, params);
        this.smsDao = smsDao;
        this.bidirectionalSmsSync = bidirectionalSmsSync;
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            long smsId = getInputData().getLong("sms_id", -1);
            String status = getInputData().getString("status");
            String errorCode = getInputData().getString("error_code");
            
            if (smsId == -1) {
                Log.e(TAG, "Invalid SMS ID for delivery tracking");
                return Result.failure();
            }
            
            Single<SmsEntity> smsSingle = smsDao.getSmsById(smsId);
            SmsEntity sms = smsSingle.blockingGet();
            
            Log.d(TAG, "Processing SMS delivery: ID=" + smsId + ", Status=" + status);
            
            // Update SMS status in database
            updateSmsStatus(smsId, status, errorCode);

            if ("SENT".equals(status) || "DELIVERED".equals(status)) {
                try {
                    bidirectionalSmsSync.syncSentMessageToContentProvider(smsId).blockingAwait();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to sync sent message to ContentProvider", e);
                }
            }
            
            // Handle retry logic for failed SMS
            if ("FAILED".equals(status) && shouldRetry(smsId)) {
                scheduleRetry(smsId);
            }
            
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in SMS delivery worker", e);
            return Result.retry();
        }
    }
    
    private void updateSmsStatus(long smsId, String status, String errorCode) {
        try {
            Single<SmsEntity> smsSingle = smsDao.getSmsById(smsId);
            SmsEntity sms = smsSingle.blockingGet();
            if (sms != null) {
                sms.status = status;
                
                if (errorCode != null) {
                    sms.errorCode = errorCode;
                }
                
                // Update timestamp
                if ("SENT".equals(status)) {
                    sms.sentAt = System.currentTimeMillis();
                } else if ("DELIVERED".equals(status)) {
                    sms.deliveredAt = System.currentTimeMillis();
                } else if ("FAILED".equals(status)) {
                    sms.sentAt = System.currentTimeMillis();
                }
                
                smsDao.updateSms(sms).blockingAwait();
                Log.d(TAG, "Updated SMS status: " + smsId + " -> " + status);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update SMS status", e);
        }
    }
    
    private boolean shouldRetry(long smsId) {
        try {
            Single<SmsEntity> smsSingle = smsDao.getSmsById(smsId);
            SmsEntity sms = smsSingle.blockingGet();
            if (sms != null && sms.retryCount < 3) {
                // Check if enough time has passed for retry
                long currentTime = System.currentTimeMillis();
                long retryDelay = (long) Math.pow(2, sms.retryCount) * 60000; // Exponential backoff
                
                return (currentTime - sms.createdAt) > retryDelay;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking retry conditions", e);
        }
        return false;
    }
    
    private void scheduleRetry(long smsId) {
        try {
            Single<SmsEntity> smsSingle = smsDao.getSmsById(smsId);
            SmsEntity sms = smsSingle.blockingGet();
            if (sms != null) {
                sms.retryCount++;
                sms.status = "PENDING_RETRY";
                smsDao.updateSms(sms).blockingAwait();
                
                // Schedule retry with WorkManager
                // This would be implemented with a separate retry worker
                Log.d(TAG, "Scheduled retry for SMS: " + smsId + ", attempt: " + sms.retryCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule retry", e);
        }
    }
}
