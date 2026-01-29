package com.bulksms.smsmanager.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.bulksms.smsmanager.workers.SmsDeliveryWorker;
import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

/**
 * BroadcastReceiver for SMS delivery tracking
 * Delegates to WorkManager for reliable processing
 */
@AndroidEntryPoint
public class SmsDeliveryReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsDeliveryReceiver";
    
    @Inject
    WorkManager workManager;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String action = intent.getAction();
            long smsId = intent.getLongExtra("sms_id", -1);
            
            if (smsId == -1) {
                Log.e(TAG, "Invalid SMS ID in broadcast");
                return;
            }
            
            Data.Builder workData = new Data.Builder()
                .putLong("sms_id", smsId);
            
            if ("com.bulksms.smsmanager.SMS_SENT".equals(action)) {
                int resultCode = getResultCode();
                String status = (resultCode == android.app.Activity.RESULT_OK) ? "SENT" : "FAILED";
                
                if ("FAILED".equals(status)) {
                    workData.putString("error_code", "SEND_FAILED");
                }
                
                workData.putString("status", status);
                Log.d(TAG, "SMS sent broadcast: ID=" + smsId + ", Status=" + status);
                
            } else if ("com.bulksms.smsmanager.SMS_DELIVERED".equals(action)) {
                int resultCode = getResultCode();
                String status = (resultCode == android.app.Activity.RESULT_OK) ? "DELIVERED" : "DELIVERY_FAILED";
                
                if ("DELIVERY_FAILED".equals(status)) {
                    workData.putString("error_code", "DELIVERY_FAILED");
                }
                
                workData.putString("status", status);
                Log.d(TAG, "SMS delivery broadcast: ID=" + smsId + ", Status=" + status);
                
            } else {
                Log.w(TAG, "Unknown broadcast action: " + action);
                return;
            }
            
            // Schedule work with WorkManager
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SmsDeliveryWorker.class)
                .setInputData(workData.build())
                .addTag("sms_delivery")
                .build();
            
            workManager.enqueue(workRequest);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS delivery broadcast", e);
        }
    }
}
