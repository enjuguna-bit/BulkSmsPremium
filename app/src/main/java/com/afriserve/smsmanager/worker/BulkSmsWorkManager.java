package com.afriserve.smsmanager.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.lifecycle.LiveData;

import com.afriserve.smsmanager.AppConfig;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * BulkSmsWorkManager — v2
 *
 * Changes vs v1:
 *  - {@link #enqueueBulkSend} now wires up proper exponential back-off using
 *    {@link AppConfig.Worker#INITIAL_BACKOFF_MS} so failed workers retry
 *    automatically (up to {@link AppConfig.Worker#MAX_RETRY_COUNT} times).
 *  - Added a {@link Constraints} check for sufficient storage — avoids
 *    enqueuing when the device is critically low on space.
 *  - Added {@link #cancelAll} for aborting every pending bulk-send job.
 *  - {@link #isRunning} convenience helper for the UI.
 *  - Improved logging throughout.
 */
public final class BulkSmsWorkManager {

    private static final String TAG = "BulkSmsWorkManager";

    public static final String TAG_BULK_SEND    = "bulk_sms_send";
    private static final String UNIQUE_PREFIX   = "bulk_sms_send_";

    private BulkSmsWorkManager() {}

    // ── Work name ─────────────────────────────────────────────────────────────

    @NonNull
    public static String uniqueWorkName(@NonNull String sessionId) {
        return UNIQUE_PREFIX + sessionId;
    }

    // ── Enqueue ───────────────────────────────────────────────────────────────

    /**
     * Enqueues (or replaces) a bulk-send work request for {@code sessionId}.
     *
     * @param context       Application context.
     * @param sessionId     Unique session identifier passed to the worker.
     * @param initialDelayMs Optional delay in ms before the work starts (0 = immediate).
     * @param replace       When {@code true}, any existing work for this session is cancelled
     *                      and replaced; when {@code false}, the existing work is kept.
     * @return The {@link UUID} of the enqueued {@link OneTimeWorkRequest}.
     */
    @NonNull
    public static UUID enqueueBulkSend(
            @NonNull Context context,
            @NonNull String sessionId,
            long initialDelayMs,
            boolean replace
    ) {
        Data inputData = new Data.Builder()
                .putString(BulkSmsSendingWorker.KEY_SESSION_ID, sessionId)
                .build();

        // Require sufficient storage to avoid writing SMS records when disk is full
        Constraints constraints = new Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BulkSmsSendingWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        AppConfig.Worker.INITIAL_BACKOFF_MS,
                        TimeUnit.MILLISECONDS
                )
                .addTag(TAG_BULK_SEND)
                .addTag(TAG_BULK_SEND + "_" + sessionId)
                .setInitialDelay(Math.max(0, initialDelayMs), TimeUnit.MILLISECONDS)
                .build();

        ExistingWorkPolicy policy = replace ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP;
        WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueWorkName(sessionId), policy, request);

        Log.d(TAG, "Enqueued bulk send — session=" + sessionId
                + ", delayMs=" + initialDelayMs + ", policy=" + policy.name());
        return request.getId();
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    @NonNull
    public static LiveData<List<WorkInfo>> getWorkInfos(
            @NonNull Context context, @NonNull String sessionId) {
        return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(uniqueWorkName(sessionId));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /** Cancels work for a specific session. */
    public static void cancel(@NonNull Context context, @NonNull String sessionId) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(sessionId));
        Log.d(TAG, "Cancelled work — session=" + sessionId);
    }

    /** Cancels ALL pending bulk-send work (e.g. on sign-out or reset). */
    public static void cancelAll(@NonNull Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_BULK_SEND);
        Log.d(TAG, "Cancelled all bulk-send work");
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when there is at least one RUNNING or ENQUEUED
     * work request for the given session.  Blocking — call off the main thread.
     */
    public static boolean isRunning(@NonNull Context context, @NonNull String sessionId) {
        try {
            List<WorkInfo> infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(uniqueWorkName(sessionId))
                    .get();
            if (infos == null) return false;
            for (WorkInfo info : infos) {
                WorkInfo.State s = info.getState();
                if (s == WorkInfo.State.RUNNING || s == WorkInfo.State.ENQUEUED) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "isRunning() check failed — assuming not running", e);
        }
        return false;
    }
}
