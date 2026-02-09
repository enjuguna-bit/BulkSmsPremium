package com.afriserve.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
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
 * BroadcastReceiver for incoming SMS (SMS_RECEIVED)
 * Works as a fallback when app is not the default SMS app
 * Note: This has limitations and may not work on all Android versions
 */
public class SmsReceivedReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SmsReceivedReceiver";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        
        Log.d(TAG, "Received SMS_RECEIVED broadcast");

        final PendingResult pendingResult = goAsync();
        executor.execute(() -> {
            try {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages == null || messages.length == 0) {
                    Log.w(TAG, "No SMS messages found in intent");
                    return;
                }
                
                Log.d(TAG, "Processing " + messages.length + " SMS message parts (fallback)");
                
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
                        Log.d(TAG, "Blocked incoming message from: " + normalizedAddress + " (fallback)");
                        continue;
                    }
                    String body = completeMessage.body;
                    long timestamp = completeMessage.timestamp;
                    
                    Log.d(TAG, "Processing complete SMS from: " + address + 
                          " (multipart: " + completeMessage.wasMultipart + ") (fallback)");
                    
                    ProviderMatch providerMatch = findProviderMatch(context, normalizedAddress, body, timestamp);
                    if (providerMatch != null) {
                        try {
                            smsDao.getSmsByDeviceSmsId(providerMatch.deviceSmsId).blockingGet();
                            Log.d(TAG, "Skipping duplicate message by deviceSmsId: " + providerMatch.deviceSmsId + " (fallback)");
                            continue;
                        } catch (Exception ignored) {
                            // Not found, proceed
                        }
                    }

                    // Create SMS entity
                    SmsEntity smsEntity = new SmsEntity();
                    smsEntity.deviceSmsId = providerMatch != null ? providerMatch.deviceSmsId : null;
                    smsEntity.threadId = providerMatch != null ? providerMatch.threadId : null;
                    smsEntity.boxType = 1; // Telephony.Sms.MESSAGE_TYPE_INBOX
                    smsEntity.isRead = false; // New incoming messages are unread
                    smsEntity.phoneNumber = normalizedAddress;
                    smsEntity.message = body != null ? body : "";
                    smsEntity.status = "RECEIVED"; // Mark as received
                    smsEntity.createdAt = timestamp;
                    
                    // Insert into Room database
                    try {
                        Long result = smsDao.insertSms(smsEntity).blockingGet();
                        Log.d(TAG, "Inserted SMS from: " + address + " with ID: " + result + " (fallback)");

                        if (normalizedAddress != null && !normalizedAddress.trim().isEmpty()) {
                            try {
                                conversationRepository.updateConversationFromMessage(smsEntity).blockingAwait();
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to update conversation for incoming SMS (fallback)", e);
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
                        Log.e(TAG, "Error inserting SMS into database (fallback)", e);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing incoming SMS (fallback)", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private ProviderMatch findProviderMatch(Context context, String normalizedAddress, String body, long timestamp) {
        if (context == null || normalizedAddress == null || normalizedAddress.trim().isEmpty()) {
            return null;
        }

        Cursor cursor = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            long windowMs = 5 * 60 * 1000L;
            String lastDigits = PhoneNumberUtils.getLastNDigits(normalizedAddress, 7);

            String selection = Telephony.Sms.DATE + " >= ?";
            String[] selectionArgs = new String[] { String.valueOf(timestamp - windowMs) };

            if (lastDigits != null && !lastDigits.isEmpty()) {
                selection = Telephony.Sms.DATE + " >= ? AND " + Telephony.Sms.ADDRESS + " LIKE ?";
                selectionArgs = new String[] { String.valueOf(timestamp - windowMs), "%" + lastDigits };
            }

            String[] projection = new String[] {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.THREAD_ID
            };

            cursor = resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor == null) {
                return null;
            }

            int checked = 0;
            String expectedBody = body != null ? body : "";
            while (cursor.moveToNext() && checked < 50) {
                checked++;
                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String dbBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                long dbDate = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));

                if (!PhoneNumberUtils.areSameNumber(address, normalizedAddress)) {
                    continue;
                }
                if (dbBody == null) {
                    dbBody = "";
                }
                if (!dbBody.equals(expectedBody)) {
                    continue;
                }
                if (Math.abs(dbDate - timestamp) > (2 * 60 * 1000L)) {
                    continue;
                }

                long id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                return new ProviderMatch(id, threadId);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "No permission to read Telephony provider (fallback)", se);
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve provider match (fallback)", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static final class ProviderMatch {
        private final long deviceSmsId;
        private final Long threadId;

        private ProviderMatch(long deviceSmsId, Long threadId) {
            this.deviceSmsId = deviceSmsId;
            this.threadId = threadId;
        }
    }
}
