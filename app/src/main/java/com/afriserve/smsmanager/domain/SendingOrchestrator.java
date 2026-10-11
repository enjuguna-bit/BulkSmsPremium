package com.afriserve.smsmanager.domain;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.WorkInfo;

import com.afriserve.smsmanager.BulkSmsService;
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService.UploadSession;
import com.afriserve.smsmanager.worker.BulkSmsSendingWorker;
import com.afriserve.smsmanager.worker.BulkSmsWorkManager;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Orchestrates the sending lifecycle: enqueue, observe, pause, resume, stop.
 * Owns WorkManager observation. Extracted from BulkSmsViewModel.
 *
 * v3 changes:
 *  - observeWork() now accepts an optional {@link LifecycleOwner}.  When one is
 *    provided it uses {@code observe(lifecycleOwner, observer)} so the LiveData
 *    framework automatically removes the observer when the owner reaches DESTROYED,
 *    eliminating the need for callers to remember to call clearObservers().
 *  - When no LifecycleOwner is available (e.g. from a non-lifecycle context) the
 *    method falls back to {@code observeForever} and the existing
 *    {@link #clearObservers()} / {@link #removeWorkObserver()} contract applies.
 *  - observeWork() always tears down any stale observer first.
 *  - handleWorkInfo() is null-safe throughout.
 */
@Singleton
public class SendingOrchestrator {
    private static final String TAG = "SendingOrchestrator";

    private final Application application;

    private LiveData<java.util.List<WorkInfo>> workInfoLiveData;
    private Observer<java.util.List<WorkInfo>> workObserver;
    /** True when the current observer was registered with observeForever (needs manual removal). */
    private boolean observingForever = false;

    @Inject
    public SendingOrchestrator(Application application) {
        this.application = application;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enqueue a bulk send work request and observe progress via a LifecycleOwner.
     *
     * <p>Prefer this overload from a Fragment/Activity so the observer is automatically
     * removed on lifecycle DESTROY without requiring an explicit {@link #clearObservers()}
     * call.
     */
    public void enqueueWork(UploadSession session, boolean replaceExisting,
                            LifecycleOwner lifecycleOwner, WorkProgressCallback callback) {
        if (session == null || session.fileId == null) {
            Log.w(TAG, "enqueueWork called with null session or fileId — ignored");
            return;
        }
        BulkSmsWorkManager.enqueueBulkSend(application, session.fileId, 0L, replaceExisting);
        observeWork(session.fileId, session, lifecycleOwner, callback);
    }

    /**
     * Enqueue a bulk send work request without a LifecycleOwner (e.g. from a ViewModel).
     * Caller MUST call {@link #clearObservers()} from {@code ViewModel.onCleared()}.
     */
    public void enqueueWork(UploadSession session, boolean replaceExisting,
                            WorkProgressCallback callback) {
        if (session == null || session.fileId == null) {
            Log.w(TAG, "enqueueWork called with null session or fileId — ignored");
            return;
        }
        BulkSmsWorkManager.enqueueBulkSend(application, session.fileId, 0L, replaceExisting);
        observeWork(session.fileId, session, null, callback);
    }

    /** Enqueue a scheduled bulk send. */
    public void enqueueScheduledWork(String fileId, long delayMs) {
        BulkSmsWorkManager.enqueueBulkSend(application, fileId, delayMs, true);
    }

    /** Cancel an in-progress work request. */
    public void cancelWork(String fileId) {
        if (fileId != null) {
            BulkSmsWorkManager.cancel(application, fileId);
        }
    }

    /**
     * Remove the current work observer.
     * Only necessary when the observer was registered without a LifecycleOwner
     * (i.e. via {@link #enqueueWork(UploadSession, boolean, WorkProgressCallback)}).
     * Call from {@code ViewModel.onCleared()}.
     */
    public void removeWorkObserver() {
        if (workInfoLiveData != null && workObserver != null) {
            if (observingForever) {
                workInfoLiveData.removeObserver(workObserver);
            }
            workInfoLiveData = null;
            workObserver = null;
            observingForever = false;
            Log.d(TAG, "WorkInfo observer removed");
        }
    }

    /** Alias for {@link #removeWorkObserver()} — use in {@code ViewModel.onCleared()}. */
    public void clearObservers() {
        removeWorkObserver();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Observe WorkInfo changes for a session and dispatch to the callback.
     * Always tears down any existing observer first.
     *
     * @param lifecycleOwner when non-null, uses lifecycle-aware observe() so the
     *                       framework handles cleanup automatically.
     */
    private void observeWork(String sessionId, UploadSession session,
                             LifecycleOwner lifecycleOwner, WorkProgressCallback callback) {
        removeWorkObserver();

        workInfoLiveData = BulkSmsWorkManager.getWorkInfos(application, sessionId);
        workObserver = workInfos -> {
            if (workInfos == null || workInfos.isEmpty()) return;
            handleWorkInfo(workInfos.get(0), session, callback);
        };

        if (lifecycleOwner != null) {
            // Lifecycle-aware: observer removed automatically on DESTROYED.
            workInfoLiveData.observe(lifecycleOwner, workObserver);
            observingForever = false;
            Log.d(TAG, "Observing work (lifecycle-aware) for session: " + sessionId);
        } else {
            // Manual: caller must call clearObservers() from onCleared().
            workInfoLiveData.observeForever(workObserver);
            observingForever = true;
            Log.d(TAG, "Observing work (forever) for session: " + sessionId);
        }
    }

    /** Translate WorkInfo into meaningful status updates. */
    private void handleWorkInfo(WorkInfo info, UploadSession session, WorkProgressCallback callback) {
        if (info == null) return;

        WorkInfo.State state = info.getState();
        int recipientCount = session != null && session.recipients != null
                ? session.recipients.size() : 0;
        int sessionIndex = session != null ? session.lastProcessedIndex : 0;

        if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
            int total     = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_TOTAL, recipientCount);
            int processed = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_PROCESSED, sessionIndex);
            int sent      = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_SENT,
                    session != null ? session.sentCount : 0);
            int failed    = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_FAILED,
                    session != null ? session.failedCount : 0);
            int skipped   = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_SKIPPED,
                    session != null ? session.skippedCount : 0);
            int percent   = info.getProgress().getInt(BulkSmsSendingWorker.PROGRESS_PERCENT,
                    total > 0 ? (processed * 100 / total) : 0);

            String status = state == WorkInfo.State.ENQUEUED
                    ? "Queued for sending…"
                    : "Sending: " + processed + "/" + total;

            callback.onProgress(percent, sent, failed, Math.max(0, total - processed), status,
                    true, false);
            return;
        }

        if (state == WorkInfo.State.SUCCEEDED) {
            String resultStatus = info.getOutputData().getString(BulkSmsSendingWorker.RESULT_STATUS);
            int total   = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_TOTAL, 0);
            int sent    = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_SENT, 0);
            int failed  = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_FAILED, 0);
            int skipped = info.getOutputData().getInt(BulkSmsSendingWorker.RESULT_SKIPPED, 0);
            int queued  = Math.max(0, total - (sent + failed + skipped));

            if (BulkSmsService.RESULT_PAUSED.equals(resultStatus)) {
                callback.onProgress(0, sent, failed, queued, "Sending paused", false, true);
            } else if (BulkSmsService.RESULT_STOPPED.equals(resultStatus)) {
                callback.onProgress(0, sent, failed, queued, "Sending stopped", false, false);
            } else {
                callback.onProgress(100, sent, failed, queued,
                        "Bulk SMS sending completed", false, false);
            }
            return;
        }

        if (state == WorkInfo.State.CANCELLED) {
            callback.onProgress(0, 0, 0, 0, "Sending cancelled", false, false);
            return;
        }

        if (state == WorkInfo.State.FAILED) {
            String error = info.getOutputData().getString(BulkSmsSendingWorker.RESULT_ERROR);
            callback.onError(error != null ? "Sending failed: " + error : "Sending failed");
        }
    }

    // ── Callback interface ────────────────────────────────────────────────────

    public interface WorkProgressCallback {
        void onProgress(int percent, int sent, int failed, int queued, String status,
                        boolean isSending, boolean isPaused);
        void onError(String message);
    }
}
