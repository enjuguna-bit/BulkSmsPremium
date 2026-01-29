package com.bulksms.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.SmsEntity;

/**
 * BroadcastReceiver for SMS delivered status
 * Updates Room database when SMS is delivered
 */
public class SmsDeliveredReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsDeliveredReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        long smsId = intent.getLongExtra("sms_id", -1);
        int resultCode = getResultCode();
        
        if (smsId <= 0) {
            Log.w(TAG, "Invalid SMS ID in intent");
            return;
        }
        
        updateDeliveryStatus(context, smsId, resultCode == android.app.Activity.RESULT_OK);
    }
    
    private void updateDeliveryStatus(Context context, long smsId, boolean delivered) {
        // Get database instance
        com.bulksms.smsmanager.AppDatabase database = 
            com.bulksms.smsmanager.AppDatabase.getInstance(context);
        SmsDao smsDao = database.smsDao();
        
        new Thread(() -> {
            try {
                SmsEntity sms = smsDao.getSmsById(smsId).blockingGet();
                if (sms != null && delivered) {
                    sms.status = "DELIVERED";
                    sms.deliveredAt = System.currentTimeMillis();
                    smsDao.updateSms(sms).blockingAwait();
                    Log.d(TAG, "SMS delivered: " + smsId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating delivery status", e);
            }
        }).start();
    }
}
