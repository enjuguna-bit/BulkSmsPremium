package com.afriserve.smsmanager.data.sync;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.afriserve.smsmanager.data.dao.SmsDao;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import com.afriserve.smsmanager.sms.DefaultSmsAppManager;

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
    private static final long PROVIDER_MATCH_WINDOW_MS = 5 * 60 * 1000L;
    private static final long PROVIDER_MATCH_DELTA_MS = 2 * 60 * 1000L;

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
            syncSentMessagesToContentProviderInternal();
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Sync inbox messages from Room to Android SMS ContentProvider
     * This is called when the app is the default SMS app
     */
    public Completable syncInboxMessagesToContentProvider() {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                throw new IllegalStateException("Bidirectional sync not available - app must be default SMS app");
            }
            syncInboxMessagesToContentProviderInternal();
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Sync any missing messages (inbox + sent) to the ContentProvider.
     * Safe to call during startup; skips when not default SMS app.
     */
    public Completable syncMissingMessagesToContentProvider() {
        return Completable.fromAction(() -> {
            if (!isBidirectionalSyncAvailable()) {
                Log.w(TAG, "Bidirectional sync not available - skipping missing message sync");
                return;
            }
            syncInboxMessagesToContentProviderInternal();
            syncSentMessagesToContentProviderInternal();
        }).subscribeOn(Schedulers.io());
    }

    private void syncSentMessagesToContentProviderInternal() {
        Log.d(TAG, "Starting sync of sent messages to ContentProvider");

        List<SmsEntity> sentMessages = smsDao.getSentMessagesWithoutDeviceId().blockingGet();
        int syncedCount = 0;
        int matchedCount = 0;
        int errorCount = 0;

        for (SmsEntity message : sentMessages) {
            try {
                ProviderMatch match = findProviderMatch(message, Telephony.Sms.Sent.CONTENT_URI);
                if (match != null) {
                    message.deviceSmsId = match.deviceSmsId;
                    if (match.threadId != null) {
                        message.threadId = match.threadId;
                    }
                    smsDao.updateSms(message).blockingAwait();
                    matchedCount++;
                    continue;
                }

                Long deviceSmsId = addSentMessageToContentProvider(message);
                if (deviceSmsId != null) {
                    message.deviceSmsId = deviceSmsId;
                    Long threadId = resolveThreadIdFromProvider(deviceSmsId);
                    if (threadId != null) {
                        message.threadId = threadId;
                    }
                    smsDao.updateSms(message).blockingAwait();
                    syncedCount++;
                    Log.d(TAG, "Synced sent message to ContentProvider: " + deviceSmsId);
                }
            } catch (Exception e) {
                errorCount++;
                Log.e(TAG, "Failed to sync sent message to ContentProvider", e);
            }
        }

        Log.d(TAG, "Sent sync completed: " + syncedCount + " inserted, " + matchedCount + " matched, " + errorCount + " errors");
    }

    private void syncInboxMessagesToContentProviderInternal() {
        Log.d(TAG, "Starting sync of inbox messages to ContentProvider");

        List<SmsEntity> inboxMessages = smsDao.getInboxMessagesWithoutDeviceId().blockingGet();
        int syncedCount = 0;
        int matchedCount = 0;
        int errorCount = 0;

        for (SmsEntity message : inboxMessages) {
            try {
                ProviderMatch match = findProviderMatch(message, Telephony.Sms.Inbox.CONTENT_URI);
                if (match != null) {
                    message.deviceSmsId = match.deviceSmsId;
                    if (match.threadId != null) {
                        message.threadId = match.threadId;
                    }
                    smsDao.updateSms(message).blockingAwait();
                    matchedCount++;
                    continue;
                }

                Long deviceSmsId = addInboxMessageToContentProvider(message);
                if (deviceSmsId != null) {
                    message.deviceSmsId = deviceSmsId;
                    Long threadId = resolveThreadIdFromProvider(deviceSmsId);
                    if (threadId != null) {
                        message.threadId = threadId;
                    }
                    smsDao.updateSms(message).blockingAwait();
                    syncedCount++;
                    Log.d(TAG, "Synced inbox message to ContentProvider: " + deviceSmsId);
                }
            } catch (Exception e) {
                errorCount++;
                Log.e(TAG, "Failed to sync inbox message to ContentProvider", e);
            }
        }

        Log.d(TAG, "Inbox sync completed: " + syncedCount + " inserted, " + matchedCount + " matched, " + errorCount + " errors");
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
                updateExistingSentMessageInContentProvider(message);
                return;
            }

            if (!isOutgoingStatus(message.status)) {
                return;
            }

            ProviderMatch match = findProviderMatch(message, Telephony.Sms.Sent.CONTENT_URI);
            if (match != null) {
                message.deviceSmsId = match.deviceSmsId;
                if (match.threadId != null) {
                    message.threadId = match.threadId;
                }
                smsDao.updateSms(message).blockingAwait();
                return;
            }

            Long deviceSmsId = addSentMessageToContentProvider(message);
            if (deviceSmsId != null) {
                message.deviceSmsId = deviceSmsId;
                Long threadId = resolveThreadIdFromProvider(deviceSmsId);
                if (threadId != null) {
                    message.threadId = threadId;
                }
                smsDao.updateSms(message).blockingAwait();
                Log.d(TAG, "Synced single sent message to ContentProvider: " + deviceSmsId);
            }
        }).subscribeOn(Schedulers.io());
    }

    private boolean isOutgoingStatus(@Nullable String status) {
        return "PENDING".equals(status)
                || "PENDING_RETRY".equals(status)
                || "SENT".equals(status)
                || "DELIVERED".equals(status)
                || "FAILED".equals(status);
    }

    private void updateExistingSentMessageInContentProvider(@NonNull SmsEntity message) {
        if (message.deviceSmsId == null) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(Telephony.Sms.STATUS, mapStatusToProviderStatus(message.status));
            values.put(Telephony.Sms.READ, message.isRead != null && message.isRead ? 1 : 0);
            if (message.createdAt > 0) {
                values.put(Telephony.Sms.DATE, message.createdAt);
            }
            if (message.sentAt > 0) {
                values.put(Telephony.Sms.DATE_SENT, message.sentAt);
            }

            Uri uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, String.valueOf(message.deviceSmsId));
            int rows = contentResolver.update(uri, values, null, null);
            if (rows <= 0) {
                Log.w(TAG, "No provider rows updated for existing sent message id=" + message.deviceSmsId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed updating existing sent message in provider: id=" + message.deviceSmsId, e);
        }
    }

    /**
     * Add a sent message to the Android SMS ContentProvider
     * Only works when app is default SMS app
     */
    @Nullable
    private Long addSentMessageToContentProvider(@NonNull SmsEntity message) {
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
     * Add an inbox message to the Android SMS ContentProvider
     * Only works when app is default SMS app
     */
    @Nullable
    private Long addInboxMessageToContentProvider(@NonNull SmsEntity message) {
        try {
            ContentValues values = new ContentValues();

            boolean isRead = message.isRead != null && message.isRead;
            long createdAt = message.createdAt > 0 ? message.createdAt : System.currentTimeMillis();

            values.put(Telephony.Sms.ADDRESS, message.phoneNumber);
            values.put(Telephony.Sms.BODY, message.message != null ? message.message : "");
            values.put(Telephony.Sms.DATE, createdAt);
            values.put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX);
            values.put(Telephony.Sms.READ, isRead ? 1 : 0);
            values.put(Telephony.Sms.SEEN, isRead ? 1 : 0);

            Uri uri = contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values);
            if (uri != null) {
                long id = ContentUris.parseId(uri);
                return id > 0 ? id : null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add inbox message to ContentProvider", e);
        }

        return null;
    }

    @Nullable
    private Long resolveThreadIdFromProvider(long deviceSmsId) {
        Cursor cursor = null;
        try {
            Uri uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, deviceSmsId);
            cursor = contentResolver.query(uri, new String[] { Telephony.Sms.THREAD_ID }, null, null, null);
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

    @Nullable
    private ProviderMatch findProviderMatch(@NonNull SmsEntity message, @NonNull Uri contentUri) {
        String normalizedAddress = message.phoneNumber != null ? message.phoneNumber : "";
        if (normalizedAddress.trim().isEmpty()) {
            return null;
        }

        Cursor cursor = null;
        try {
            long timestamp = message.createdAt > 0 ? message.createdAt : System.currentTimeMillis();
            String lastDigits = PhoneNumberUtils.getLastNDigits(normalizedAddress, 7);

            String selection = Telephony.Sms.DATE + " >= ?";
            String[] selectionArgs = new String[] { String.valueOf(timestamp - PROVIDER_MATCH_WINDOW_MS) };

            if (lastDigits != null && !lastDigits.isEmpty()) {
                selection = Telephony.Sms.DATE + " >= ? AND " + Telephony.Sms.ADDRESS + " LIKE ?";
                selectionArgs = new String[] { String.valueOf(timestamp - PROVIDER_MATCH_WINDOW_MS), "%" + lastDigits };
            }

            String[] projection = new String[] {
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.THREAD_ID
            };

            cursor = contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor == null) {
                return null;
            }

            int checked = 0;
            String expectedBody = message.message != null ? message.message : "";
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
                if (Math.abs(dbDate - timestamp) > PROVIDER_MATCH_DELTA_MS) {
                    continue;
                }

                long id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                return new ProviderMatch(id, threadId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve provider match", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
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
                stats.inboxMessagesNeedingSync = smsDao.getInboxMessagesWithoutDeviceId().blockingGet().size();
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

    private static final class ProviderMatch {
        private final long deviceSmsId;
        private final Long threadId;

        private ProviderMatch(long deviceSmsId, Long threadId) {
            this.deviceSmsId = deviceSmsId;
            this.threadId = threadId;
        }
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
        public int inboxMessagesNeedingSync = 0;
        public int totalMessagesInRoom = 0;
        public int totalMessagesInContentProvider = 0;

        @Override
        public String toString() {
            return "SyncStatistics{" +
                    "bidirectionalSyncAvailable=" + bidirectionalSyncAvailable +
                    ", isDefaultSmsApp=" + isDefaultSmsApp +
                    ", sentMessagesNeedingSync=" + sentMessagesNeedingSync +
                    ", inboxMessagesNeedingSync=" + inboxMessagesNeedingSync +
                    ", totalMessagesInRoom=" + totalMessagesInRoom +
                    ", totalMessagesInContentProvider=" + totalMessagesInContentProvider +
                    '}';
        }
    }
}
