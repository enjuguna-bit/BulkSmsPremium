package com.afriserve.smsmanager.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.afriserve.smsmanager.AppConfig
import com.afriserve.smsmanager.BulkSmsService
import com.afriserve.smsmanager.R
import com.afriserve.smsmanager.data.dao.ScheduledCampaignDao
import com.afriserve.smsmanager.data.dao.getScheduledCampaignByCampaignIdSuspend
import com.afriserve.smsmanager.data.dao.updateScheduledCampaignSuspend
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that drives a bulk-SMS sending session.
 *
 * v2 fix: replaced all [blockingGet] / [blockingAwait] calls on [ScheduledCampaignDao]
 * with proper suspend extensions ([getScheduledCampaignByCampaignIdSuspend] /
 * [updateScheduledCampaignSuspend]).  The previous blocking calls could deadlock when
 * the RxJava IO thread pool was saturated by an active bulk-send.
 */
@HiltWorker
class BulkSmsSendingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bulkSmsService: BulkSmsService,
    private val uploadPersistence: UploadPersistenceService,
    private val scheduledCampaignDao: ScheduledCampaignDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BulkSmsSendingWorker"

        // Input keys
        const val KEY_SESSION_ID = "session_id"

        // Progress keys
        const val PROGRESS_TOTAL     = "progress_total"
        const val PROGRESS_PROCESSED = "progress_processed"
        const val PROGRESS_PERCENT   = "progress_percent"
        const val PROGRESS_SENT      = "progress_sent"
        const val PROGRESS_FAILED    = "progress_failed"
        const val PROGRESS_SKIPPED   = "progress_skipped"
        const val PROGRESS_STATUS    = "progress_status"

        // Output keys
        const val RESULT_STATUS  = "result_status"
        const val RESULT_ERROR   = "result_error"
        const val RESULT_SENT    = "result_sent"
        const val RESULT_FAILED  = "result_failed"
        const val RESULT_SKIPPED = "result_skipped"
        const val RESULT_TOTAL   = "result_total"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        // ── Validate input ──────────────────────────────────────────────
        val sessionId = inputData.getString(KEY_SESSION_ID)
        if (sessionId.isNullOrBlank()) {
            Log.e(TAG, "Worker started without a session_id — cannot proceed.")
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "Missing session id").build()
            )
        }

        val session = uploadPersistence.loadSessionSync(sessionId)
        if (session == null) {
            Log.e(TAG, "Session '$sessionId' not found in persistence store.")
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "Session not found: $sessionId").build()
            )
        }

        if (session.recipients.isNullOrEmpty()) {
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "No recipients in session '$sessionId'").build()
            )
        }
        if (session.template.isNullOrBlank()) {
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "No message template in session '$sessionId'").build()
            )
        }

        val totalCount = session.recipients.size
        Log.d(TAG, "Starting bulk send: sessionId=$sessionId, recipients=$totalCount, runAttempt=$runAttemptCount")

        // ── Start foreground ────────────────────────────────────────────
        try {
            setForeground(buildForegroundInfo(0, totalCount))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to promote worker to foreground", e)
        }

        // ── Mark scheduled campaign as executing (non-blocking) ─────────
        if (session.campaignId > 0) {
            runCatching {
                val scheduled = scheduledCampaignDao
                    .getScheduledCampaignByCampaignIdSuspend(session.campaignId)
                if (scheduled != null) {
                    scheduled.markAsExecuting()
                    scheduledCampaignDao.updateScheduledCampaignSuspend(scheduled)
                }
            }.onFailure { Log.w(TAG, "Failed to mark scheduled campaign as executing", it) }
        }

        // ── Do the work ─────────────────────────────────────────────────
        val sendResult = runCatching {
            bulkSmsService.sendBulkSmsSession(session) { processed, total ->
                val percent = if (total > 0) (processed * 100 / total) else 0
                val progressData = Data.Builder()
                    .putInt(PROGRESS_TOTAL, total)
                    .putInt(PROGRESS_PROCESSED, processed)
                    .putInt(PROGRESS_PERCENT, percent)
                    .putInt(PROGRESS_SENT, session.sentCount)
                    .putInt(PROGRESS_FAILED, session.failedCount)
                    .putInt(PROGRESS_SKIPPED, session.skippedCount)
                    .putString(PROGRESS_STATUS, "sending")
                    .build()
                setProgressAsync(progressData)
                updateProgressNotification(processed, total)
            }
        }

        if (sendResult.isFailure) {
            val err = sendResult.exceptionOrNull()
            Log.e(TAG, "sendBulkSmsSession threw unexpectedly", err)
            return@withContext if (runAttemptCount < AppConfig.Worker.MAX_RETRY_COUNT) {
                Log.w(TAG, "Scheduling retry (attempt ${runAttemptCount + 1}/${AppConfig.Worker.MAX_RETRY_COUNT})")
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder().putString(RESULT_ERROR, err?.message ?: "Unknown error").build()
                )
            }
        }

        val result = sendResult.getOrThrow()

        // ── Update scheduled campaign status (non-blocking) ─────────────
        if (session.campaignId > 0) {
            runCatching {
                val scheduled = scheduledCampaignDao
                    .getScheduledCampaignByCampaignIdSuspend(session.campaignId)
                if (scheduled != null) {
                    when (result.status) {
                        BulkSmsService.RESULT_COMPLETED -> {
                            scheduled.markAsCompleted()
                            scheduledCampaignDao.updateScheduledCampaignSuspend(scheduled)
                        }
                        BulkSmsService.RESULT_PAUSED -> {
                            scheduled.status = "SCHEDULED"
                            scheduled.updatedAt = System.currentTimeMillis()
                            scheduledCampaignDao.updateScheduledCampaignSuspend(scheduled)
                        }
                        BulkSmsService.RESULT_STOPPED -> {
                            scheduled.cancel()
                            scheduledCampaignDao.updateScheduledCampaignSuspend(scheduled)
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to update scheduled campaign status", it) }
        }

        // ── Build output ────────────────────────────────────────────────
        val output = Data.Builder()
            .putString(RESULT_STATUS, result.status)
            .putInt(RESULT_SENT,    result.sentCount)
            .putInt(RESULT_FAILED,  result.failedCount)
            .putInt(RESULT_SKIPPED, result.skippedCount)
            .putInt(RESULT_TOTAL,   result.totalCount)
            .putString(RESULT_ERROR, result.error)
            .build()

        Log.d(TAG, "Bulk send finished: status=${result.status}, sent=${result.sentCount}, failed=${result.failedCount}")

        return@withContext if (result.status == BulkSmsService.RESULT_FAILED) {
            Result.failure(output)
        } else {
            Result.success(output)
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────

    private fun buildForegroundInfo(current: Int, total: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, AppConfig.Worker.CHANNEL_ID)
            .setContentTitle("Sending Bulk SMS")
            .setContentText(if (total > 0) "Sending $current of $total…" else "Preparing…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), current, current == 0 && total == 0)
            .build()

        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(AppConfig.Worker.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(AppConfig.Worker.NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.Worker.CHANNEL_ID,
                "Bulk SMS Sending",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while bulk SMS messages are being sent."
            }
            appContext.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private var lastNotificationUpdateMs = 0L

    private fun updateProgressNotification(current: Int, total: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs < AppConfig.Worker.PROGRESS_NOTIFICATION_UPDATE_MS) return
        lastNotificationUpdateMs = now
        try {
            setForegroundAsync(buildForegroundInfo(current, total))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update progress notification", e)
        }
    }
}
