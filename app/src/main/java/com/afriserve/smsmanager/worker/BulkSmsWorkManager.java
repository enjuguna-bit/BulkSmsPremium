package com.afriserve.smsmanager.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Helper for scheduling bulk SMS work by session id.
 */
public final class BulkSmsWorkManager {
    public static final String TAG_BULK_SEND = "bulk_sms_send";
    private static final String UNIQUE_WORK_PREFIX = "bulk_sms_send_";

    private BulkSmsWorkManager() {}

    @NonNull
    public static String uniqueWorkName(@NonNull String sessionId) {
        return UNIQUE_WORK_PREFIX + sessionId;
    }

    @NonNull
    public static UUID enqueueBulkSend(@NonNull Context context, @NonNull String sessionId, long initialDelayMs, boolean replace) {
        Data data = new Data.Builder()
                .putString(BulkSmsSendingWorker.KEY_SESSION_ID, sessionId)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BulkSmsSendingWorker.class)
                .setInputData(data)
                .addTag(TAG_BULK_SEND)
                .addTag(TAG_BULK_SEND + "_" + sessionId)
                .setInitialDelay(Math.max(0, initialDelayMs), TimeUnit.MILLISECONDS)
                .build();

        ExistingWorkPolicy policy = replace ? ExistingWorkPolicy.REPLACE : ExistingWorkPolicy.KEEP;
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(sessionId), policy, request);
        return request.getId();
    }

    @NonNull
    public static LiveData<List<WorkInfo>> getWorkInfos(@NonNull Context context, @NonNull String sessionId) {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(uniqueWorkName(sessionId));
    }

    public static void cancel(@NonNull Context context, @NonNull String sessionId) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(sessionId));
    }
}
