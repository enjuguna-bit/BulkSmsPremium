package com.afriserve.smsmanager.data.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.sms.DefaultSmsAppManager;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Automatic sync manager for SMS synchronization
 * Handles initial sync, periodic sync, and event-driven sync
 */
@Singleton
public class AutoSyncManager {

    private static final String TAG = "AutoSyncManager";
    private static final String PREFS_NAME = "auto_sync_prefs";
    private static final String KEY_INITIAL_SYNC_COMPLETED = "initial_sync_completed";
    private static final String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";
    private static final String AUTO_SYNC_WORK_NAME = "auto_sms_sync";

    private final Context context;
    private final SmsRepository smsRepository;
    private final SmsSyncManager smsSyncManager;
    private final BidirectionalSmsSync bidirectionalSmsSync;
    private final DefaultSmsAppManager defaultSmsAppManager;
    private final SharedPreferences preferences;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public AutoSyncManager(@ApplicationContext Context context,
            SmsRepository smsRepository,
            SmsSyncManager smsSyncManager,
            BidirectionalSmsSync bidirectionalSmsSync,
            DefaultSmsAppManager defaultSmsAppManager) {
        this.context = context;
        this.smsRepository = smsRepository;
        this.smsSyncManager = smsSyncManager;
        this.bidirectionalSmsSync = bidirectionalSmsSync;
        this.defaultSmsAppManager = defaultSmsAppManager;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Initialize auto sync - should be called on app startup
     */
    public void initialize() {
        Log.d(TAG, "Initializing AutoSyncManager");

        // Check if initial sync is needed
        if (!isInitialSyncCompleted()) {
            Log.d(TAG, "Initial sync not completed, scheduling...");
            scheduleInitialSync();
        } else {
            Log.d(TAG, "Initial sync already completed");
            startPeriodicSync();
        }

        // Start real-time sync
        startRealTimeSync();

        // Monitor default SMS app status changes
        monitorDefaultSmsAppStatus();
    }

    /**
     * Schedule initial sync if not completed
     */
    public void scheduleInitialSync() {
        if (isInitialSyncCompleted()) {
            Log.d(TAG, "Initial sync already completed");
            return;
        }

        Log.d(TAG, "Scheduling initial SMS sync");

        OneTimeWorkRequest initialSyncRequest = new OneTimeWorkRequest.Builder(AutoSyncWorker.class)
                .addTag("initial_sync")
                .setConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "initial_sms_sync",
                ExistingWorkPolicy.KEEP,
                initialSyncRequest);

    }

    /**
     * Start periodic sync every 6 hours
     */
    public void startPeriodicSync() {
        Log.d(TAG, "Starting periodic SMS sync");

        PeriodicWorkRequest periodicSyncRequest = new PeriodicWorkRequest.Builder(
                AutoSyncWorker.class,
                6, // repeat interval
                TimeUnit.HOURS)
                .addTag("periodic_sync")
                .setConstraints(new Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build())
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AUTO_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncRequest);
    }

    /**
     * Start real-time sync using ContentObserver
     */
    public void startRealTimeSync() {
        Log.d(TAG, "Starting real-time SMS sync");
        smsSyncManager.startRealTimeSync();
    }

    /**
     * Stop real-time sync
     */
    public void stopRealTimeSync() {
        Log.d(TAG, "Stopping real-time SMS sync");
        smsSyncManager.stopRealTimeSync();
    }

    /**
     * Force immediate sync
     */
    public Completable forceSyncNow() {
        Log.d(TAG, "Force immediate sync requested");

        return Completable.fromAction(() -> {
            // Perform initial sync
            smsRepository.syncNewMessages()
                    .blockingAwait();

            // If default SMS app, sync bidirectional
            if (bidirectionalSmsSync.isBidirectionalSyncAvailable()) {
                bidirectionalSmsSync.syncSentMessagesToContentProvider()
                        .blockingAwait();
            }

            // Update last sync timestamp
            updateLastSyncTimestamp();

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Monitor default SMS app status and adjust sync accordingly
     */
    private void monitorDefaultSmsAppStatus() {
        disposables.add(
                Completable.fromAction(() -> {
                    boolean wasDefaultSmsApp = preferences.getBoolean("was_default_sms_app", false);
                    boolean isDefaultSmsApp = defaultSmsAppManager.isDefaultSmsApp();

                    if (isDefaultSmsApp && !wasDefaultSmsApp) {
                        // App just became default SMS app
                        Log.d(TAG, "App became default SMS app, enabling bidirectional sync");
                        onBecameDefaultSmsApp();
                    } else if (!isDefaultSmsApp && wasDefaultSmsApp) {
                        // App is no longer default SMS app
                        Log.d(TAG, "App is no longer default SMS app, disabling bidirectional sync");
                        onNoLongerDefaultSmsApp();
                    }

                    // Update preference
                    preferences.edit()
                            .putBoolean("was_default_sms_app", isDefaultSmsApp)
                            .apply();

                })
                        .subscribeOn(Schedulers.io())
                        .subscribe());
    }

    /**
     * Handle app becoming default SMS app
     */
    private void onBecameDefaultSmsApp() {
        Log.d(TAG, "Handling app becoming default SMS app");

        // Enable bidirectional sync features
        disposables.add(
                bidirectionalSmsSync.syncSentMessagesToContentProvider()
                        .subscribe(
                                () -> Log.d(TAG, "Bidirectional sync enabled successfully"),
                                error -> Log.e(TAG, "Failed to enable bidirectional sync", error)));

        // Start periodic sync more frequently when default SMS app
        startPeriodicSync();
    }

    /**
     * Handle app no longer being default SMS app
     */
    private void onNoLongerDefaultSmsApp() {
        Log.d(TAG, "Handling app no longer being default SMS app");

        // Continue with normal sync but reduce frequency
        startPeriodicSync();
    }

    /**
     * Check if initial sync has been completed
     */
    public boolean isInitialSyncCompleted() {
        return preferences.getBoolean(KEY_INITIAL_SYNC_COMPLETED, false);
    }

    /**
     * Mark initial sync as completed
     */
    public void markInitialSyncCompleted() {
        preferences.edit()
                .putBoolean(KEY_INITIAL_SYNC_COMPLETED, true)
                .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                .apply();

        Log.d(TAG, "Initial sync marked as completed");

        // Start periodic sync after initial sync
        startPeriodicSync();
    }

    /**
     * Get last sync timestamp
     */
    public long getLastSyncTimestamp() {
        return preferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0);
    }

    /**
     * Update last sync timestamp
     */
    private void updateLastSyncTimestamp() {
        preferences.edit()
                .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    /**
     * Get sync status information
     */
    public Single<SyncStatus> getSyncStatus() {
        return Single.fromCallable(() -> {
            SyncStatus status = new SyncStatus();
            status.initialSyncCompleted = isInitialSyncCompleted();
            status.lastSyncTimestamp = getLastSyncTimestamp();
            status.isDefaultSmsApp = defaultSmsAppManager.isDefaultSmsApp();
            status.bidirectionalSyncAvailable = bidirectionalSmsSync.isBidirectionalSyncAvailable();
            status.realTimeSyncActive = smsSyncManager.getSyncState().getValue() == SmsSyncManager.SyncState.ACTIVE;

            // Calculate time since last sync
            if (status.lastSyncTimestamp > 0) {
                long timeSince = System.currentTimeMillis() - status.lastSyncTimestamp;
                status.timeSinceLastSync = timeSince;
            }

            return status;
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Reset sync state (for testing or troubleshooting)
     */
    public void resetSyncState() {
        Log.d(TAG, "Resetting sync state");

        preferences.edit()
                .remove(KEY_INITIAL_SYNC_COMPLETED)
                .remove(KEY_LAST_SYNC_TIMESTAMP)
                .apply();

        // Cancel existing work
        WorkManager.getInstance(context).cancelAllWorkByTag(AUTO_SYNC_WORK_NAME);
        WorkManager.getInstance(context).cancelAllWorkByTag("initial_sync");
        WorkManager.getInstance(context).cancelAllWorkByTag("periodic_sync");

        // Stop real-time sync
        stopRealTimeSync();
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up AutoSyncManager");
        disposables.clear();
        stopRealTimeSync();
    }

    /**
     * Sync status data class
     */
    public static class SyncStatus {
        public boolean initialSyncCompleted = false;
        public long lastSyncTimestamp = 0;
        public long timeSinceLastSync = 0;
        public boolean isDefaultSmsApp = false;
        public boolean bidirectionalSyncAvailable = false;
        public boolean realTimeSyncActive = false;

        public String getLastSyncFormatted() {
            if (lastSyncTimestamp == 0) {
                return "Never";
            }

            long now = System.currentTimeMillis();
            long diff = now - lastSyncTimestamp;

            if (diff < 60000) { // Less than 1 minute
                return "Just now";
            } else if (diff < 3600000) { // Less than 1 hour
                long minutes = diff / 60000;
                return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
            } else if (diff < 86400000) { // Less than 1 day
                long hours = diff / 3600000;
                return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
            } else {
                long days = diff / 86400000;
                return days + " day" + (days == 1 ? "" : "s") + " ago";
            }
        }

        @Override
        public String toString() {
            return "SyncStatus{" +
                    "initialSyncCompleted=" + initialSyncCompleted +
                    ", lastSyncTimestamp=" + lastSyncTimestamp +
                    ", timeSinceLastSync=" + timeSinceLastSync +
                    ", isDefaultSmsApp=" + isDefaultSmsApp +
                    ", bidirectionalSyncAvailable=" + bidirectionalSyncAvailable +
                    ", realTimeSyncActive=" + realTimeSyncActive +
                    '}';
        }
    }
}
