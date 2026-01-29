package com.bulksms.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver for incoming SMS (SMS_DELIVER)
 * Only works when app is set as default SMS app
 * Inserts incoming messages into Room database
 */
public class SmsDeliverReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsDeliverReceiver";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
        
        executor.execute(() -> {
            try {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages == null || messages.length == 0) {
                    return;
                }
                
                // Get database instance
                com.bulksms.smsmanager.AppDatabase database = 
                    com.bulksms.smsmanager.AppDatabase.getInstance(context);
                SmsDao smsDao = database.smsDao();
                
                for (SmsMessage message : messages) {
                    String address = message.getDisplayOriginatingAddress();
                    String body = message.getMessageBody();
                    long timestamp = message.getTimestampMillis();
                    int messageId = message.getIndexOnIcc();
                    
                    // Check if message already exists (by deviceSmsId if available)
                    // For incoming SMS, we'll use a combination of address + timestamp
                    // In a real implementation, you'd get the Telephony _ID after insert
                    
                    SmsEntity smsEntity = new SmsEntity();
                    smsEntity.boxType = 1; // Telephony.Sms.MESSAGE_TYPE_INBOX
                    smsEntity.isRead = false; // New incoming messages are unread
                    smsEntity.phoneNumber = address != null ? address : "";
                    smsEntity.message = body != null ? body : "";
                    smsEntity.status = "PENDING"; // Incoming messages start as pending
                    smsEntity.createdAt = timestamp;
                    
                    // Insert into Room database
                    @SuppressWarnings("CheckResult")
                    Long result = smsDao.insertSms(smsEntity).blockingGet();
                    
                    Log.d(TAG, "Received SMS from: " + address);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing incoming SMS", e);
            }
        });
    }
}
