package com.afriserve.smsmanager.data.repository;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.core.content.ContextCompat;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Repository for SMS data operations
 * 
 * ## Synchronization Architecture
 * 
 * This repository manages a dual-layer synchronization system:
 * 
 * ### 1. Telephony API Layer (Device SMS Database)
 * - Source: Android's Telephony.Sms ContentProvider (content://sms/)
 * - Purpose: System-level SMS storage, device synchronization
 * - Access: Read-only via ContentResolver queries
 * - Contains: All SMS messages with device-assigned IDs
 * 
 * ### 2. Room Database Layer (App Local Storage)
 * - Source: Local SQLite database via Room DAO
 * - Purpose: App-specific operations, caching, enhanced features
 * - Access: Full CRUD operations, search, indexing
 * - Contains: Synced messages plus app-created messages
 * 
 * ### Synchronization Flow:
 * 1. **Initial Sync**: Telephony → Room (copy all messages)
 * 2. **Real-time Sync**: ContentObserver detects changes → Room updates
 * 3. **App Messages**: Room → Telephony (outgoing SMS)
 * 4. **Bidirectional Updates**: Status changes propagate both directions
 * 
 * ### Key Fields:
 * - `deviceSmsId`: Links Room entity to Telephony provider record
 * - `boxType`: Mirrors Telephony.Sms.TYPE (1=Inbox, 2=Sent, etc.)
 * - `status`: App-specific state (PENDING, SENT, DELIVERED, FAILED)
 * - `isRead`: Mirrors Telephony.Sms.READ (0=unread, 1=read)
 * 
 * ### Data Consistency:
 * - Messages with `deviceSmsId` are synced from device
 * - Messages without `deviceSmsId` are app-created (pending sync)
 * - Status updates propagate to Telephony provider when possible
 * - Search indexing operates on Room layer for performance
 */
@Singleton
public class SmsRepository {

    private static final String TAG = "SmsRepository";
    private final SmsDao smsDao;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // Error states
    private final MutableLiveData<String> _errorState = new MutableLiveData<>();
    public final LiveData<String> errorState = _errorState;

    // Sync result
    private final MutableLiveData<SyncResult> _syncResult = new MutableLiveData<>();
    public final LiveData<SyncResult> syncResult = _syncResult;

    @Inject
    public SmsRepository(SmsDao smsDao, @ApplicationContext Context context) {
        this.smsDao = smsDao;
        this.context = context.getApplicationContext();
    }

    /**
     * Get all messages PagingSource for Paging 3
     */
    public androidx.paging.PagingSource<Integer, SmsEntity> getAllMessagesPaged() {
        return smsDao.getAllSmsPaged();
    }

    public androidx.paging.PagingSource<Integer, SmsEntity> getInboxMessagesPaged() {
        return smsDao.getInboxMessagesPaged();
    }

    public androidx.paging.PagingSource<Integer, SmsEntity> getSentMessagesPaged() {
        return smsDao.getSentMessagesPaged();
    }

    public androidx.paging.PagingSource<Integer, SmsEntity> getUnreadMessagesPaged() {
        return smsDao.getUnreadMessagesPaged();
    }

    public androidx.paging.PagingSource<Integer, SmsEntity> searchMessagesPaged(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllMessagesPaged();
        }
        return smsDao.searchMessagesPaged("%" + query + "%");
    }

    /**
     * Get messages for a specific phone number
     * Returns a list of messages for the given phone number
     */
    public java.util.List<SmsEntity> getMessagesByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        if (phoneNumber.startsWith("thread:")) {
            try {
                String idPart = phoneNumber.replace("thread:", "").trim();
                long threadId = Long.parseLong(idPart);
                return getMessagesByThreadId(threadId);
            } catch (Exception ignored) {
                return new java.util.ArrayList<>();
            }
        }

        String normalized = normalizePhoneNumber(phoneNumber);
        if (normalized == null || normalized.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            java.util.List<SmsEntity> exact = smsDao.getMessagesByPhoneNumber(normalized).blockingGet();
            if (exact != null && !exact.isEmpty()) {
                return exact;
            }
        } catch (Exception e) {
            // Fall through to fuzzy match
        }

        String lastDigits = PhoneNumberUtils.getLastNDigits(normalized, 7);
        if (lastDigits != null && !lastDigits.isEmpty()) {
            try {
                return smsDao.getMessagesByPhoneNumberLike(lastDigits).blockingGet();
            } catch (Exception e) {
                // Ignore and return empty below
            }
        }

        return new java.util.ArrayList<>();
    }

    /**
     * Get messages for a specific thread ID
     */
    public java.util.List<SmsEntity> getMessagesByThreadId(Long threadId) {
        if (threadId == null || threadId <= 0) {
            return new java.util.ArrayList<>();
        }
        try {
            java.util.List<SmsEntity> messages = smsDao.getMessagesByThreadId(threadId).blockingGet();
            return messages != null ? messages : new java.util.ArrayList<>();
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Get a single SMS by its database id
     */
    public Single<SmsEntity> getSmsById(long id) {
        return smsDao.getSmsById(id);
    }

    /**
     * Send SMS message
     * Creates a new SMS entity and stores it in the database with PENDING status
     * Returns Completable for proper async handling
     */
    public Completable sendSms(SmsEntity smsEntity) {
        return sendSms(smsEntity, -1);
    }

    /**
     * Send SMS message with optional SIM slot selection
     */
    public Completable sendSms(SmsEntity smsEntity, int simSlot) {
        return Completable.fromAction(() -> {
            try {
                // Validate message
                if (smsEntity == null || smsEntity.phoneNumber == null || smsEntity.message == null
                        || smsEntity.message.trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid SMS message");
                }

                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("SEND_SMS permission not granted");
                }

                // Normalize phone number
                smsEntity.phoneNumber = normalizePhoneNumber(smsEntity.phoneNumber);
                if (smsEntity.phoneNumber == null || smsEntity.phoneNumber.isEmpty()) {
                    throw new IllegalArgumentException("Invalid phone number");
                }

                // Set default values if not set
                if (smsEntity.status == null || smsEntity.status.isEmpty()) {
                    smsEntity.status = "PENDING";
                }

                if (smsEntity.createdAt == 0) {
                    smsEntity.createdAt = System.currentTimeMillis();
                }

                if (smsEntity.isRead == null) {
                    smsEntity.isRead = true;
                }

                if (smsEntity.boxType == null) {
                    smsEntity.boxType = Telephony.Sms.MESSAGE_TYPE_SENT;
                }

                // Insert message into database
                Long messageId = smsDao.insertSms(smsEntity).blockingGet();
                if (messageId == null || messageId <= 0) {
                    throw new IllegalStateException("Failed to insert SMS into database");
                }
                smsEntity.id = messageId;
                Log.d(TAG, "SMS inserted with ID: " + messageId);

                // Trigger actual SMS send
                sendSmsInternal(messageId, smsEntity.phoneNumber, smsEntity.message, simSlot);

            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS", e);
                _errorState.postValue("Failed to send message: " + e.getMessage());
                try {
                    if (smsEntity != null && smsEntity.id > 0) {
                        smsEntity.status = "FAILED";
                        smsEntity.errorCode = "SEND_FAILED";
                        smsEntity.errorMessage = e.getMessage();
                        smsDao.updateSms(smsEntity).blockingAwait();
                    }
                } catch (Exception updateError) {
                    Log.e(TAG, "Failed to update SMS failure status", updateError);
                }
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    public LiveData<Integer> getUnreadCount() {
        return smsDao.getUnreadCount();
    }

    public LiveData<Integer> getTotalCount() {
        return smsDao.getTotalCount();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = PhoneNumberUtils.normalizePhoneNumber(phoneNumber);
        if (normalized != null && !normalized.isEmpty()) {
            return normalized;
        }
        return phoneNumber != null ? phoneNumber.trim() : "";
    }

    private void sendSmsInternal(long smsId, String phoneNumber, String message, int simSlot) {
        SmsManager smsManager = resolveSmsManager(simSlot);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent sentIntent = new Intent("com.afriserve.smsmanager.SMS_SENT");
        sentIntent.setPackage(context.getPackageName());
        sentIntent.putExtra("sms_id", smsId);

        Intent deliveredIntent = new Intent("com.afriserve.smsmanager.SMS_DELIVERED");
        deliveredIntent.setPackage(context.getPackageName());
        deliveredIntent.putExtra("sms_id", smsId);

        int requestCode = (int) (smsId % Integer.MAX_VALUE);
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
                context, requestCode, sentIntent, flags);
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
                context, requestCode + 10000, deliveredIntent, flags);

        ArrayList<String> parts = smsManager.divideMessage(message);
        if (parts.size() > 1) {
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentPendingIntent);
                deliveredIntents.add(deliveredPendingIntent);
            }
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents);
        } else {
            smsManager.sendTextMessage(phoneNumber, null, message, sentPendingIntent, deliveredPendingIntent);
        }
    }

    private SmsManager resolveSmsManager(int simSlot) {
        SmsManager smsManager = SmsManager.getDefault();

        if (simSlot < 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    int defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId();
                    if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SmsManager systemManager = context.getSystemService(SmsManager.class);
                            if (systemManager != null) {
                                return systemManager.createForSubscriptionId(defaultSubId);
                            }
                        }
                        return SmsManager.getSmsManagerForSubscriptionId(defaultSubId);
                    }
                } catch (Exception ignored) {
                }
            }
            return smsManager;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SubscriptionManager subscriptionManager =
                    (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager != null) {
                SubscriptionInfo subscriptionInfo = null;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                                == PackageManager.PERMISSION_GRANTED) {
                            subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(simSlot);
                        } else {
                            Log.w(TAG, "READ_PHONE_STATE permission not granted, using default SIM");
                        }
                    } else {
                        subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(simSlot);
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception getting subscription info", e);
                }

                if (subscriptionInfo != null) {
                    int subId = subscriptionInfo.getSubscriptionId();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SmsManager systemManager = context.getSystemService(SmsManager.class);
                        if (systemManager != null) {
                            return systemManager.createForSubscriptionId(subId);
                        }
                    } else {
                        return SmsManager.getSmsManagerForSubscriptionId(subId);
                    }
                }
            }
        }

        return smsManager;
    }

    /**
     * Sync messages from device SMS ContentProvider to Room database
     * Returns Completable for proper async handling
     */
    public Completable syncNewMessages() {
        return Completable.fromAction(() -> {
            try {
                Log.d(TAG, "Starting SMS sync...");
                ContentResolver contentResolver = context.getContentResolver();
                Uri uri = Uri.parse("content://sms/");

                String[] projection = {
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ,
                        Telephony.Sms.THREAD_ID
                };

                String sortOrder = Telephony.Sms.DATE + " DESC";

                Log.d(TAG, "Querying SMS ContentProvider...");
                Cursor cursor = contentResolver.query(uri, projection, null, null, sortOrder);

                if (cursor != null) {
                    try {
                        int totalCount = cursor.getCount();
                        Log.d(TAG, "SMS cursor returned with " + totalCount + " messages");

                        if (totalCount == 0) {
                            Log.w(TAG, "No SMS messages found in device ContentProvider");
                            _syncResult.postValue(new SyncResult.Success(0, 0));
                            return;
                        }

                        int syncedCount = 0;
                        int skippedCount = 0;

                        while (cursor.moveToNext()) {
                            try {
                                long deviceSmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                                long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                                int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                                int read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ));
                                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                                String normalizedAddress = normalizePhoneNumber(address);

                                // Check if message already exists by deviceSmsId
                                SmsEntity existing = null;
                                try {
                                    existing = smsDao.getSmsByDeviceSmsId(deviceSmsId).blockingGet();
                                } catch (Exception e) {
                                    // No existing message found - this is expected for new messages
                                }

                                if (existing == null) {
                                    // New message - create entity
                                    String status = mapSmsTypeToStatus(type, read);

                                    SmsEntity smsEntity = new SmsEntity();
                                    smsEntity.deviceSmsId = deviceSmsId;
                                    smsEntity.boxType = type;
                                    smsEntity.threadId = threadId;
                                    smsEntity.isRead = read == 1;
                                    smsEntity.phoneNumber = normalizedAddress;
                                    smsEntity.message = body != null ? body : "";
                                    smsEntity.status = status;
                                    smsEntity.createdAt = date;

                                    @SuppressWarnings("CheckResult")
                                    Long result = smsDao.insertSms(smsEntity).blockingGet();
                                    syncedCount++;
                                } else {
                                    // Update existing message if needed
                                    boolean needsUpdate = false;

                                    if (!java.util.Objects.equals(existing.boxType, type)) {
                                        existing.boxType = type;
                                        needsUpdate = true;
                                    }
                                    if (!java.util.Objects.equals(existing.threadId, threadId)) {
                                        existing.threadId = threadId;
                                        needsUpdate = true;
                                    }
                                    if (existing.isRead == null || existing.isRead != (read == 1)) {
                                        existing.isRead = read == 1;
                                        needsUpdate = true;
                                    }
                                    if (!java.util.Objects.equals(existing.phoneNumber, normalizedAddress)) {
                                        existing.phoneNumber = normalizedAddress;
                                        needsUpdate = true;
                                    }
                                    if (!java.util.Objects.equals(existing.message, body)) {
                                        existing.message = body != null ? body : "";
                                        needsUpdate = true;
                                    }

                                    if (needsUpdate) {
                                        smsDao.updateSms(existing).blockingAwait();
                                        syncedCount++;
                                    } else {
                                        skippedCount++;
                                    }
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to process SMS: " + e.getMessage());
                                // Continue with next message
                            }
                        }

                        Log.d(TAG, "Sync completed: " + syncedCount + " synced, " + skippedCount + " skipped");

                        // Sync MMS as part of full sync
                        try {
                            syncMmsMessages(null);
                        } catch (Exception e) {
                            Log.w(TAG, "MMS sync failed during full sync", e);
                        }

                        _syncResult.postValue(new SyncResult.Success(syncedCount, skippedCount));
                    } finally {
                        cursor.close();
                    }
                } else {
                    Log.w(TAG, "Failed to query SMS ContentProvider - cursor is null");
                    _errorState.postValue("Failed to access SMS content provider");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync messages", e);
                _errorState.postValue("Sync failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Legacy sync method for backward compatibility
     */
    public void syncNewMessagesLegacy() {
        syncNewMessages().subscribe(
                () -> Log.d(TAG, "Legacy sync completed"),
                error -> Log.e(TAG, "Legacy sync failed", error));
    }

    /**
     * Map Android SMS type to our status field
     */
    private String mapSmsTypeToStatus(int type, int read) {
        // Android SMS types: 1=Inbox, 2=Sent, 3=Draft, 4=Outbox, 5=Failed, 6=Queued
        if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
            return "RECEIVED";
        } else if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
            return "SENT";
        } else if (type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
            return "FAILED";
        } else {
            return "PENDING";
        }
    }

    /**
     * Mark message as read by updating isRead field
     * Also updates Telephony provider if deviceSmsId exists
     * Returns Completable for proper async handling
     */
    public Completable markAsRead(long messageId) {
        return Completable.fromAction(() -> {
            try {
                SmsEntity message = smsDao.getSmsById(messageId).blockingGet();
                if (message != null) {
                    boolean wasChanged = false;

                    // Update isRead field
                    if (message.isRead == null || !message.isRead) {
                        message.isRead = true;
                        wasChanged = true;
                    }

                    // Update status if it was PENDING
                    if ("PENDING".equals(message.status)) {
                        message.status = "DELIVERED";
                        wasChanged = true;
                    }

                    if (wasChanged) {
                        smsDao.updateSms(message).blockingAwait();

                        // Update Telephony provider if this is a synced message
                        if (message.deviceSmsId != null) {
                            try {
                                android.content.ContentValues values = new android.content.ContentValues();
                                values.put(Telephony.Sms.READ, 1);
                                context.getContentResolver().update(
                                        Telephony.Sms.CONTENT_URI,
                                        values,
                                        Telephony.Sms._ID + " = ?",
                                        new String[] { String.valueOf(message.deviceSmsId) });
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to update Telephony provider for read status", e);
                            }
                        }

                        Log.d(TAG, "Marked message " + messageId + " as read");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark as read", e);
                _errorState.postValue("Failed to mark message as read: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Legacy mark as read for backward compatibility
     */
    public void markAsReadLegacy(long messageId) {
        markAsRead(messageId).subscribe(
                () -> Log.d(TAG, "Legacy mark as read completed"),
                error -> Log.e(TAG, "Legacy mark as read failed", error));
    }

    /**
     * Mark message as unread by updating isRead field
     * Also updates Telephony provider if deviceSmsId exists
     * Returns Completable for proper async handling
     */
    public Completable markAsUnread(long messageId) {
        return Completable.fromAction(() -> {
            try {
                SmsEntity message = smsDao.getSmsById(messageId).blockingGet();
                if (message != null) {
                    boolean wasChanged = false;

                    // Update isRead field
                    if (message.isRead == null || message.isRead) {
                        message.isRead = false;
                        wasChanged = true;
                    }

                    // Update status if it was DELIVERED
                    if ("DELIVERED".equals(message.status)) {
                        message.status = "PENDING";
                        wasChanged = true;
                    }

                    if (wasChanged) {
                        smsDao.updateSms(message).blockingAwait();

                        // Update Telephony provider if this is a synced message
                        if (message.deviceSmsId != null) {
                            try {
                                android.content.ContentValues values = new android.content.ContentValues();
                                values.put(Telephony.Sms.READ, 0);
                                context.getContentResolver().update(
                                        Telephony.Sms.CONTENT_URI,
                                        values,
                                        Telephony.Sms._ID + " = ?",
                                        new String[] { String.valueOf(message.deviceSmsId) });
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to update Telephony provider for unread status", e);
                            }
                        }

                        Log.d(TAG, "Marked message " + messageId + " as unread");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark as unread", e);
                _errorState.postValue("Failed to mark message as unread: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Legacy mark as unread for backward compatibility
     */
    public void markAsUnreadLegacy(long messageId) {
        markAsUnread(messageId).subscribe(
                () -> Log.d(TAG, "Legacy mark as unread completed"),
                error -> Log.e(TAG, "Legacy mark as unread failed", error));
    }

    /**
     * Delete message from database
     * Returns Completable for proper async handling
     */
    public Completable deleteMessage(SmsEntity message) {
        return Completable.fromAction(() -> {
            try {
                smsDao.deleteSms(message).blockingAwait();
                Log.d(TAG, "Deleted message " + message.id);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete message", e);
                _errorState.postValue("Failed to delete message: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Legacy delete for backward compatibility
     */
    public void deleteMessageLegacy(SmsEntity message) {
        deleteMessage(message).subscribe(
                () -> Log.d(TAG, "Legacy delete completed"),
                error -> Log.e(TAG, "Legacy delete failed", error));
    }

    /**
     * Incremental sync - sync only recent messages since last sync
     * More efficient for startup and periodic updates
     */
    public Completable syncRecentMessages() {
        return Completable.fromAction(() -> {
            try {
                Log.d(TAG, "Starting incremental SMS sync...");

                // Get the timestamp of the most recent synced message
                Long lastSyncTimestamp = smsDao.getLatestMessageTimestamp().blockingGet();
                if (lastSyncTimestamp == null) {
                    Log.d(TAG, "No previous sync found, falling back to full sync");
                    syncNewMessages().blockingAwait();
                    return;
                }

                Log.d(TAG, "Syncing messages since timestamp: " + lastSyncTimestamp);

                ContentResolver contentResolver = context.getContentResolver();
                Uri uri = Uri.parse("content://sms/");

                String[] projection = {
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE,
                        Telephony.Sms.READ,
                        Telephony.Sms.THREAD_ID
                };

                // Only get messages newer than our last sync
                String selection = Telephony.Sms.DATE + " > ?";
                String[] selectionArgs = { String.valueOf(lastSyncTimestamp) };
                String sortOrder = Telephony.Sms.DATE + " DESC";

                Log.d(TAG, "Querying recent SMS from ContentProvider...");
                Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);

                if (cursor != null) {
                    try {
                        int recentCount = cursor.getCount();
                        Log.d(TAG, "Found " + recentCount + " recent messages to sync");

                        if (recentCount == 0) {
                            Log.d(TAG, "No recent messages found");
                            _syncResult.postValue(new SyncResult.Success(0, 0));
                            return;
                        }

                        int syncedCount = 0;

                        while (cursor.moveToNext()) {
                            try {
                                long deviceSmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                                long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                                int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                                int read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ));
                                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                                String normalizedAddress = normalizePhoneNumber(address);

                                // Check if message already exists by deviceSmsId
                                SmsEntity existing = null;
                                try {
                                    existing = smsDao.getSmsByDeviceSmsId(deviceSmsId).blockingGet();
                                } catch (Exception e) {
                                    // No existing message found - this is expected for new messages
                                }

                                if (existing == null) {
                                    // New message - create entity
                                    String status = mapSmsTypeToStatus(type, read);

                                    SmsEntity smsEntity = new SmsEntity();
                                    smsEntity.deviceSmsId = deviceSmsId;
                                    smsEntity.boxType = type;
                                    smsEntity.threadId = threadId;
                                    smsEntity.isRead = read == 1;
                                    smsEntity.phoneNumber = normalizedAddress;
                                    smsEntity.message = body != null ? body : "";
                                    smsEntity.status = status;
                                    smsEntity.createdAt = date;

                                    smsDao.insertSms(smsEntity).blockingGet();
                                    syncedCount++;
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Error processing recent message", e);
                            }
                        }

                        // Sync MMS newer than last sync
                        try {
                            syncMmsMessages(lastSyncTimestamp);
                        } catch (Exception e) {
                            Log.w(TAG, "MMS sync failed during incremental sync", e);
                        }

                        Log.d(TAG, "Incremental sync completed: " + syncedCount + " messages synced");
                        _syncResult.postValue(new SyncResult.Success(syncedCount, 0));

                    } finally {
                        cursor.close();
                    }
                } else {
                    Log.e(TAG, "Failed to query SMS ContentProvider for recent messages");
                    _syncResult.postValue(new SyncResult.Error("Failed to access SMS content provider"));
                }

            } catch (Exception e) {
                Log.e(TAG, "Incremental sync failed", e);
                _syncResult.postValue(new SyncResult.Error("Incremental sync failed: " + e.getMessage()));
                throw new RuntimeException(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Sync MMS messages from device ContentProvider into Room database.
     * Uses negative deviceSmsId values to avoid collisions with SMS ids.
     */
    private void syncMmsMessages(Long sinceTimestampMs) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = Uri.parse("content://mms");

            String[] projection = {
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.SUBJECT
            };

            String selection = null;
            String[] selectionArgs = null;
            if (sinceTimestampMs != null && sinceTimestampMs > 0) {
                long sinceSeconds = sinceTimestampMs / 1000;
                selection = Telephony.Mms.DATE + " > ?";
                selectionArgs = new String[] { String.valueOf(sinceSeconds) };
            }

            Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, Telephony.Mms.DATE + " DESC");
            if (cursor == null) {
                return;
            }

            try {
                while (cursor.moveToNext()) {
                    long mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID));
                    long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID));
                    long dateSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE));
                    long dateMs = dateSeconds * 1000;
                    int msgBox = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX));
                    int read = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ));
                    String subject = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT));

                    long deviceKey = -mmsId;
                    try {
                        smsDao.getSmsByDeviceSmsId(deviceKey).blockingGet();
                        continue;
                    } catch (Exception ignored) {
                        // Not found, proceed
                    }

                    String address = getMmsAddress(contentResolver, mmsId, msgBox);
                    String normalizedAddress = normalizePhoneNumber(address);
                    if (normalizedAddress == null) {
                        normalizedAddress = address != null ? address : "";
                    }

                    MmsContent content = getMmsContent(contentResolver, mmsId);
                    String body = content.text != null && !content.text.isEmpty()
                            ? content.text
                            : (subject != null && !subject.isEmpty() ? subject : (content.attachmentCount > 0 ? "[Attachment]" : "[MMS]"));

                    SmsEntity smsEntity = new SmsEntity();
                    smsEntity.deviceSmsId = deviceKey;
                    smsEntity.threadId = threadId;
                    smsEntity.boxType = msgBox;
                    smsEntity.isRead = read == 1;
                    smsEntity.phoneNumber = normalizedAddress;
                    smsEntity.message = body;
                    smsEntity.status = msgBox == Telephony.Mms.MESSAGE_BOX_INBOX ? "RECEIVED" : "SENT";
                    smsEntity.createdAt = dateMs;
                    smsEntity.isMms = true;
                    smsEntity.attachmentCount = content.attachmentCount;
                    smsEntity.mediaUri = content.mediaUri;

                    smsDao.insertSms(smsEntity).blockingGet();
                }
            } finally {
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "MMS sync failed", e);
        }
    }

    private String getMmsAddress(ContentResolver resolver, long mmsId, int msgBox) {
        String address = null;
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        String selection = "type=" + (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX ? "137" : "151");
        Cursor cursor = resolver.query(addrUri, new String[]{"address", "type"}, selection, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                }
            } finally {
                cursor.close();
            }
        }
        if (address != null && "insert-address-token".equals(address)) {
            return null;
        }
        return address;
    }

    private MmsContent getMmsContent(ContentResolver resolver, long mmsId) {
        MmsContent content = new MmsContent();
        Uri partUri = Uri.parse("content://mms/part");
        Cursor cursor = resolver.query(partUri, new String[]{"_id", "ct", "text"}, "mid=?", new String[]{String.valueOf(mmsId)}, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String partId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                    String contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"));
                    String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));

                    if ("text/plain".equals(contentType)) {
                        if (text != null) {
                            content.text = text;
                        } else {
                            content.text = readMmsTextPart(resolver, partId);
                        }
                    } else if (contentType != null) {
                        if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                            content.attachmentCount++;
                            if (content.mediaUri == null && contentType.startsWith("image/")) {
                                content.mediaUri = "content://mms/part/" + partId;
                            }
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return content;
    }

    private String readMmsTextPart(ContentResolver resolver, String partId) {
        try {
            Uri partUri = Uri.parse("content://mms/part/" + partId);
            InputStream is = resolver.openInputStream(partUri);
            if (is == null) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static class MmsContent {
        String text;
        String mediaUri;
        int attachmentCount = 0;
    }
}
