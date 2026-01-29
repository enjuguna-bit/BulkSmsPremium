package com.bulksms.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

/**
 * BroadcastReceiver for SMS sent status
 * Updates Room database when SMS is sent
 */
public class SmsSentReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsSentReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        long smsId = intent.getLongExtra("sms_id", -1);
        int resultCode = getResultCode();
        
        if (smsId <= 0) {
            Log.w(TAG, "Invalid SMS ID in intent");
            return;
        }
        
        // Use Application context to get Hilt components
        // Note: BroadcastReceivers can't use @AndroidEntryPoint directly
        // We'll need to use manual injection or a different approach
        updateSmsStatus(context, smsId, resultCode == android.app.Activity.RESULT_OK);
    }
    
    private void updateSmsStatus(Context context, long smsId, boolean success) {
        // Get database instance
        com.bulksms.smsmanager.AppDatabase database = 
            com.bulksms.smsmanager.AppDatabase.getInstance(context);
        SmsDao smsDao = database.smsDao();
        
        new Thread(() -> {
            try {
                SmsEntity sms = smsDao.getSmsById(smsId).blockingGet();
                if (sms != null) {
                    if (success) {
                        sms.status = "SENT";
                        sms.sentAt = System.currentTimeMillis();
                        sms.boxType = 2; // Telephony.Sms.MESSAGE_TYPE_SENT
                        smsDao.updateSms(sms).blockingAwait();
                        Log.d(TAG, "SMS sent: " + smsId);
                    } else {
                        sms.status = "FAILED";
                        sms.errorCode = "SEND_FAILED";
                        sms.errorMessage = "Failed to send SMS";
                        smsDao.updateSms(sms).blockingAwait();
                        Log.e(TAG, "SMS send failed: " + smsId);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating SMS status", e);
            }
        }).start();
    }
}
