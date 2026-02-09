package com.afriserve.smsmanager.data.sync;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.sms.DefaultSmsAppManager;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Bidirectional SMS synchronization manager
 * Handles two-way sync between Room database and Android SMS ContentProvider
 * Only works when app is set as default SMS app
 */
@Singleton
public class BidirectionalSmsSync {

    private static final String TAG = "BidirectionalSmsSync";

    private final Context context;
    private final SmsDao smsDao;
    private final DefaultSmsAppManager defaultSmsAppManager;
    private final ContentResolver contentResolver;

    @Inject
    public BidirectionalSmsSync(@ApplicationContext Context context,
            SmsDao smsDao,
            DefaultSmsAppManager defaultSmsAppManager) {
        this.context = context;
        this.smsDao = smsDao;
        this.defaultSmsAppManager = defaultSmsAppManager;
        this.contentResolver = context.getContentResolver();
    }

    /**
     * Check if bidirectional sync is available
     */
    public boolean isBidirectionalSyncAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                defaultSmsAppManager.isDefaultSmsApp();
    }

    /**
     * Sync sent messages from Room to Android SMS ContentProvider
     * This is called when the app is the default SMS app
     */
    public Completable syncSentMessagesToContentProvider() {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                throw new IllegalStateException("Bidirectional sync not available - app must be default SMS app");
            }

            Log.d(TAG, "Starting sync of sent messages to ContentProvider");

            // Get all sent messages from Room that don't have deviceSmsId
            List<SmsEntity> sentMessages = smsDao.getSentMessagesWithoutDeviceId().blockingGet();

            int syncedCount = 0;
            int errorCount = 0;

            for (SmsEntity message : sentMessages) {
                try {
                    Long deviceSmsId = addMessageToContentProvider(message);
                    if (deviceSmsId != null) {
                        // Update Room entity with deviceSmsId
                        message.deviceSmsId = deviceSmsId;
                        smsDao.updateSms(message).blockingAwait();
                        syncedCount++;
                        Log.d(TAG, "Synced sent message to ContentProvider: " + deviceSmsId);
                    }
                } catch (Exception e) {
                    errorCount++;
                    Log.e(TAG, "Failed to sync sent message to ContentProvider", e);
                }
            }

            Log.d(TAG, "Sync completed: " + syncedCount + " synced, " + errorCount + " errors");

        }).subscribeOn(Schedulers.io());
    }

    /**
     * Sync a single sent message to the Android SMS ContentProvider
     * Safe to call repeatedly; it will skip messages already synced.
     */
    public Completable syncSentMessageToContentProvider(long smsId) {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                Log.w(TAG, "Bidirectional sync not available - skipping single message sync");
                return;
            }

            SmsEntity message = smsDao.getSmsById(smsId).blockingGet();
            if (message == null) {
                Log.w(TAG, "No message found for id=" + smsId);
                return;
            }

            if (message.deviceSmsId != null) {
                return;
            }

            if (!"SENT".equals(message.status) && !"DELIVERED".equals(message.status)) {
                return;
            }

            Long deviceSmsId = addMessageToContentProvider(message);
            if (deviceSmsId != null) {
                message.deviceSmsId = deviceSmsId;
                smsDao.updateSms(message).blockingAwait();
                Log.d(TAG, "Synced single sent message to ContentProvider: " + deviceSmsId);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Add a message to the Android SMS ContentProvider
     * Only works when app is default SMS app
     */
    @Nullable
    private Long addMessageToContentProvider(@NonNull SmsEntity message) {
        try {
            ContentValues values = new ContentValues();

            // Required fields
            values.put(Telephony.Sms.ADDRESS, message.phoneNumber);
            values.put(Telephony.Sms.BODY, message.message);
            values.put(Telephony.Sms.DATE, message.createdAt);
            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT);
            values.put(Telephony.Sms.READ, message.isRead != null ? (message.isRead ? 1 : 0) : 1);
            values.put(Telephony.Sms.STATUS, mapStatusToProviderStatus(message.status));

            // Optional fields
            if (message.sentAt > 0) {
                values.put(Telephony.Sms.DATE_SENT, message.sentAt);
            }

            // Insert into ContentProvider
            Uri uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values);

            if (uri != null) {
                // Extract the ID from the returned URI
                String idString = uri.getLastPathSegment();
                if (idString != null) {
                    return Long.parseLong(idString);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to add message to ContentProvider", e);
        }

        return null;
    }

    /**
     * Update message status in ContentProvider
     */
    public Completable updateMessageStatusInContentProvider(long deviceSmsId, String newStatus) {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                Log.w(TAG, "Cannot update status - bidirectional sync not available");
                return;
            }

            try {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.STATUS, mapStatusToProviderStatus(newStatus));

                Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(deviceSmsId));
                int rowsUpdated = contentResolver.update(uri, values, null, null);

                if (rowsUpdated > 0) {
                    Log.d(TAG, "Updated status for message " + deviceSmsId + " to " + newStatus);
                } else {
                    Log.w(TAG, "No rows updated for message " + deviceSmsId);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to update message status in ContentProvider", e);
                throw e;
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Update message read status in ContentProvider
     */
    public Completable updateMessageReadStatusInContentProvider(long deviceSmsId, boolean isRead) {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                Log.w(TAG, "Cannot update read status - bidirectional sync not available");
                return;
            }

            try {
                ContentValues values = new ContentValues();
                values.put(Telephony.Sms.READ, isRead ? 1 : 0);

                Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(deviceSmsId));
                int rowsUpdated = contentResolver.update(uri, values, null, null);

                if (rowsUpdated > 0) {
                    Log.d(TAG, "Updated read status for message " + deviceSmsId + " to " + isRead);
                } else {
                    Log.w(TAG, "No rows updated for message " + deviceSmsId);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to update message read status in ContentProvider", e);
                throw e;
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Delete message from ContentProvider
     */
    public Completable deleteMessageFromContentProvider(long deviceSmsId) {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                Log.w(TAG, "Cannot delete message - bidirectional sync not available");
                return;
            }

            try {
                Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(deviceSmsId));
                int rowsDeleted = contentResolver.delete(uri, null, null);

                if (rowsDeleted > 0) {
                    Log.d(TAG, "Deleted message " + deviceSmsId + " from ContentProvider");
                } else {
                    Log.w(TAG, "No rows deleted for message " + deviceSmsId);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to delete message from ContentProvider", e);
                throw e;
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Get sync statistics
     */
    public Single<SyncStatistics> getSyncStatistics() {
        return Single.fromCallable(() -> {
            SyncStatistics stats = new SyncStatistics();

            if (!isBidirectionalSyncAvailable()) {
                stats.bidirectionalSyncAvailable = false;
                return stats;
            }

            stats.bidirectionalSyncAvailable = true;
            stats.isDefaultSmsApp = defaultSmsAppManager.isDefaultSmsApp();

            // Count messages needing sync
            try {
                stats.sentMessagesNeedingSync = smsDao.getSentMessagesWithoutDeviceId().blockingGet().size();
                stats.totalMessagesInRoom = smsDao.getTotalCountSingle().blockingGet();

                // Count messages in ContentProvider
                Cursor cursor = contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        new String[] { "COUNT(*) as count" },
                        null, null, null);

                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            stats.totalMessagesInContentProvider = cursor.getInt(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error getting sync statistics", e);
            }

            return stats;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Map app status to ContentProvider status
     */
    private int mapStatusToProviderStatus(String appStatus) {
        switch (appStatus) {
            case "PENDING":
                return Telephony.Sms.STATUS_PENDING;
            case "SENT":
                return Telephony.Sms.STATUS_COMPLETE;
            case "DELIVERED":
                return Telephony.Sms.STATUS_COMPLETE;
            case "FAILED":
                return Telephony.Sms.STATUS_FAILED;
            default:
                return Telephony.Sms.STATUS_NONE;
        }
    }

    /**
     * Sync statistics data class
     */
    public static class SyncStatistics {
        public boolean bidirectionalSyncAvailable = false;
        public boolean isDefaultSmsApp = false;
        public int sentMessagesNeedingSync = 0;
        public int totalMessagesInRoom = 0;
        public int totalMessagesInContentProvider = 0;

        @Override
        public String toString() {
            return "SyncStatistics{" +
                    "bidirectionalSyncAvailable=" + bidirectionalSyncAvailable +
                    ", isDefaultSmsApp=" + isDefaultSmsApp +
                    ", sentMessagesNeedingSync=" + sentMessagesNeedingSync +
                    ", totalMessagesInRoom=" + totalMessagesInRoom +
                    ", totalMessagesInContentProvider=" + totalMessagesInContentProvider +
                    '}';
        }
    }
}
