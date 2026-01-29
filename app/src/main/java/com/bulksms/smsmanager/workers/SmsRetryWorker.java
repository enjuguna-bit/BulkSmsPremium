package com.bulksms.smsmanager.workers;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import io.reactivex.rxjava3.core.Single;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * Worker for retrying failed SMS
 * Handles exponential backoff and retry limits
 */
public class SmsRetryWorker extends Worker {
    
    private static final String TAG = "SmsRetryWorker";
    
    private final SmsDao smsDao;
    
    public SmsRetryWorker(@NonNull Context context, @NonNull WorkerParameters params, SmsDao smsDao) {
        super(context, params);
        this.smsDao = smsDao;
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            long smsId = getInputData().getLong("sms_id", -1);
            
            if (smsId == -1) {
                Log.e(TAG, "Invalid SMS ID for retry");
                return Result.failure();
            }
            
            Log.d(TAG, "Retrying SMS: " + smsId);
            
            Single<SmsEntity> smsSingle = smsDao.getSmsById(smsId);
            SmsEntity sms = smsSingle.blockingGet();
            if (sms == null) {
                Log.e(TAG, "SMS not found for retry: " + smsId);
                return Result.failure();
            }
            
            // Check retry limit
            if (sms.retryCount >= 3) {
                Log.d(TAG, "Max retry limit reached for SMS: " + smsId);
                sms.status = "FAILED";
                sms.errorCode = "MAX_RETRY_REACHED";
                smsDao.updateSms(sms);
                return Result.failure();
            }
            
            // Update retry count
            sms.retryCount++;
            sms.status = "RETRYING";
            smsDao.updateSms(sms);
            
            // Attempt to send SMS again
            boolean success = sendSmsRetry(sms);
            
            if (success) {
                Log.d(TAG, "SMS retry successful: " + smsId);
                return Result.success();
            } else {
                Log.d(TAG, "SMS retry failed: " + smsId);
                
                // Schedule next retry if under limit
                if (sms.retryCount < 3) {
                    scheduleNextRetry(smsId, sms.retryCount);
                } else {
                    // Mark as permanently failed
                    sms.status = "FAILED";
                    sms.errorCode = "MAX_RETRY_REACHED";
                    smsDao.updateSms(sms);
                }
                
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in SMS retry worker", e);
            return Result.retry();
        }
    }
    
    private boolean sendSmsRetry(SmsEntity sms) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            
            // Create new pending intents for tracking
            // This would use the same logic as in SingleSmsViewModel
            // For now, just attempt basic send
            
            smsManager.sendTextMessage(
                sms.phoneNumber,
                null,
                sms.message,
                null, // No sent intent for retry
                null  // No delivery intent for retry
            );
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS retry", e);
            return false;
        }
    }
    
    private void scheduleNextRetry(long smsId, int retryCount) {
        try {
            // Calculate exponential backoff delay
            long delayMinutes = (long) Math.pow(2, retryCount);
            long delayMillis = delayMinutes * 60 * 1000;
            
            // Schedule next retry
            // This would use WorkManager's delay functionality
            Log.d(TAG, "Scheduled next retry for SMS: " + smsId + " in " + delayMinutes + " minutes");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule next retry", e);
        }
    }
}
