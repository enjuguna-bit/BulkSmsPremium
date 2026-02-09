package com.afriserve.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import com.afriserve.smsmanager.data.utils.MultipartSmsUtils;
import com.afriserve.smsmanager.notifications.SmsNotificationService;
import com.afriserve.smsmanager.data.blocks.BlockListManager;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver for incoming SMS (SMS_DELIVER)
 * Only works when app is set as default SMS app
 * Inserts incoming messages into Room database and shows notifications
 */
public class SmsDeliverReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsDeliverReceiver";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            return;
        }
        
        Log.d(TAG, "Received SMS_DELIVER broadcast");
        final PendingResult pendingResult = goAsync();
        executor.execute(() -> {
            try {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages == null || messages.length == 0) {
                    Log.w(TAG, "No SMS messages found in intent");
                    return;
                }
                
                Log.d(TAG, "Processing " + messages.length + " SMS message parts");
                
                // Process messages and handle multipart concatenation
                List<MultipartSmsUtils.CompleteSmsMessage> completeMessages = 
                    MultipartSmsUtils.processIncomingMessages(messages);
                
                // Get database instance
                com.afriserve.smsmanager.AppDatabase database = 
                    com.afriserve.smsmanager.AppDatabase.getInstance(context);
                SmsDao smsDao = database.smsDao();
                
                com.afriserve.smsmanager.data.contacts.ContactResolver contactResolver =
                    new com.afriserve.smsmanager.data.contacts.ContactResolver(context);

                BlockListManager blockListManager = new BlockListManager(context);

                // Get notification service
                SmsNotificationService notificationService = 
                    new com.afriserve.smsmanager.notifications.SmsNotificationService(
                        context, 
                        contactResolver
                    );

                com.afriserve.smsmanager.data.repository.ConversationRepository conversationRepository =
                    new com.afriserve.smsmanager.data.repository.ConversationRepository(
                        database.conversationDao(),
                        smsDao,
                        contactResolver,
                        context
                    );
                
                // Process each complete message
                for (MultipartSmsUtils.CompleteSmsMessage completeMessage : completeMessages) {
                    String address = completeMessage.address;
                    String normalizedAddress = PhoneNumberUtils.normalizePhoneNumber(address);
                    if (normalizedAddress == null) {
                        normalizedAddress = address != null ? address : "";
                    }
                    if (blockListManager.isBlocked(normalizedAddress)) {
                        Log.d(TAG, "Blocked incoming message from: " + normalizedAddress);
                        continue;
                    }
                    String body = completeMessage.body;
                    long timestamp = completeMessage.timestamp;
                    
                    Log.d(TAG, "Processing complete SMS from: " + address + 
                          " (multipart: " + completeMessage.wasMultipart + ")");
                    
                    Long deviceSmsId = insertIntoTelephonyInbox(context, address, body, timestamp);
                    Long threadId = null;
                    if (deviceSmsId != null) {
                        threadId = resolveThreadId(context, deviceSmsId);
                        try {
                            smsDao.getSmsByDeviceSmsId(deviceSmsId).blockingGet();
                            Log.d(TAG, "Skipping duplicate message by deviceSmsId: " + deviceSmsId);
                            continue;
                        } catch (Exception ignored) {
                            // Not found, proceed
                        }
                    }

                    // Check if message already exists to avoid duplicates
                    try {
                        java.util.List<SmsEntity> existingMessages = 
                            smsDao.getMessagesByPhoneNumber(normalizedAddress).blockingGet();
                        
                        boolean isDuplicate = false;
                        for (SmsEntity existing : existingMessages) {
                            if (Math.abs(existing.createdAt - timestamp) < 1000 && // Within 1 second
                                existing.message.equals(body)) {
                                isDuplicate = true;
                                break;
                            }
                        }
                        
                        if (isDuplicate) {
                            Log.d(TAG, "Skipping duplicate message from: " + address);
                            continue;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error checking for duplicates", e);
                    }
                    
                    // Create SMS entity
                    SmsEntity smsEntity = new SmsEntity();
                    smsEntity.deviceSmsId = deviceSmsId;
                    smsEntity.threadId = threadId;
                    smsEntity.boxType = 1; // Telephony.Sms.MESSAGE_TYPE_INBOX
                    smsEntity.isRead = false; // New incoming messages are unread
                    smsEntity.phoneNumber = normalizedAddress;
                    smsEntity.message = body != null ? body : "";
                    smsEntity.status = "RECEIVED"; // Mark as received
                    smsEntity.createdAt = timestamp;
                    
                    // Insert into Room database
                    try {
                        Long result = smsDao.insertSms(smsEntity).blockingGet();
                        Log.d(TAG, "Inserted SMS from: " + address + " with ID: " + result);

                        if (normalizedAddress != null && !normalizedAddress.trim().isEmpty()) {
                            try {
                                conversationRepository.updateConversationFromMessage(smsEntity).blockingAwait();
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to update conversation for incoming SMS", e);
                            }
                        }
                        
                        // Show notification for new message
                        if (result != null && result > 0) {
                            notificationService.showIncomingSmsNotification(
                                address, 
                                body,
                                smsEntity.threadId,
                                timestamp
                            );
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error inserting SMS into database", e);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing incoming SMS", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private Long insertIntoTelephonyInbox(Context context, String address, String body, long timestamp) {
        try {
            if (context == null) {
                return null;
            }
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(Telephony.Sms.ADDRESS, address);
            values.put(Telephony.Sms.BODY, body != null ? body : "");
            values.put(Telephony.Sms.DATE, timestamp);
            values.put(Telephony.Sms.READ, 0);
            values.put(Telephony.Sms.SEEN, 0);
            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
            Uri uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values);
            if (uri == null) {
                return null;
            }
            long id = ContentUris.parseId(uri);
            return id > 0 ? id : null;
        } catch (SecurityException se) {
            Log.w(TAG, "No permission to insert into Telephony provider", se);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to insert into Telephony provider", e);
            return null;
        }
    }

    private Long resolveThreadId(Context context, long deviceSmsId) {
        Cursor cursor = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, deviceSmsId);
            cursor = resolver.query(uri, new String[]{Telephony.Sms.THREAD_ID}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve threadId for deviceSmsId=" + deviceSmsId, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
