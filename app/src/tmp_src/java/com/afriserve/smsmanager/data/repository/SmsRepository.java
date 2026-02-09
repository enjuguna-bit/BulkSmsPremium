package com.afriserve.smsmanager.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;

import androidx.paging.PagingData;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
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

        String normalized = phoneNumber.replaceAll("[^0-9+]", "");
        return smsDao.getMessagesByPhoneNumber(normalized).blockingGet();
    }

    /**
     * Send SMS message
     * Creates a new SMS entity and stores it in the database with PENDING status
     * Returns Completable for proper async handling
     */
    public Completable sendSms(SmsEntity smsEntity) {
        return Completable.fromAction(() -> {
            try {
                // Validate message
                if (smsEntity == null || smsEntity.phoneNumber == null || smsEntity.message == null) {
                    throw new IllegalArgumentException("Invalid SMS message");
                }

                // Normalize phone number
                smsEntity.phoneNumber = smsEntity.phoneNumber.replaceAll("[^0-9+]", "");

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

                // Insert message into database
                Long messageId = smsDao.insertSms(smsEntity).blockingGet();
                Log.d(TAG, "SMS inserted with ID: " + messageId);

                // In a real implementation, this would trigger an actual SMS send
                // For now, we're just storing it in the database
                // A background service would handle the actual SMS sending

            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS", e);
                _errorState.postValue("Failed to send message: " + e.getMessage());
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
                        Telephony.Sms.READ
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
                                    smsEntity.isRead = read == 1;
                                    smsEntity.phoneNumber = address != null ? address : "";
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
                                    if (existing.isRead == null || existing.isRead != (read == 1)) {
                                        existing.isRead = read == 1;
                                        needsUpdate = true;
                                    }
                                    if (!java.util.Objects.equals(existing.phoneNumber, address)) {
                                        existing.phoneNumber = address != null ? address : "";
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
            return read == 0 ? "PENDING" : "DELIVERED";
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
                        Telephony.Sms.READ
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
                                    smsEntity.isRead = read == 1;
                                    smsEntity.phoneNumber = address != null ? address : "";
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
}
