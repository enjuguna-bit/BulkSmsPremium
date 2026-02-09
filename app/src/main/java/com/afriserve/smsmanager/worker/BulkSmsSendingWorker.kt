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
import com.afriserve.smsmanager.BulkSmsService
import com.afriserve.smsmanager.R
import com.afriserve.smsmanager.data.dao.ScheduledCampaignDao
import com.afriserve.smsmanager.data.persistence.UploadPersistenceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        const val KEY_SESSION_ID = "session_id"

        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PROCESSED = "progress_processed"
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_SENT = "progress_sent"
        const val PROGRESS_FAILED = "progress_failed"
        const val PROGRESS_SKIPPED = "progress_skipped"
        const val PROGRESS_STATUS = "progress_status"

        const val RESULT_STATUS = "result_status"
        const val RESULT_ERROR = "result_error"
        const val RESULT_SENT = "result_sent"
        const val RESULT_FAILED = "result_failed"
        const val RESULT_SKIPPED = "result_skipped"
        const val RESULT_TOTAL = "result_total"

        private const val NOTIFICATION_CHANNEL_ID = "bulk_sms_sending_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID)
        if (sessionId.isNullOrBlank()) {
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "Missing session id").build()
            )
        }

        val session = uploadPersistence.loadSessionSync(sessionId)
            ?: return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "Session not found").build()
            )

        if (session.recipients == null || session.recipients.isEmpty()) {
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "No recipients").build()
            )
        }
        if (session.template.isNullOrBlank()) {
            return@withContext Result.failure(
                Data.Builder().putString(RESULT_ERROR, "No template").build()
            )
        }

        try {
            setForeground(createForegroundInfo(0, session.recipients.size))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set foreground", e)
        }

        // Mark scheduled campaign as executing if applicable
        if (session.campaignId > 0) {
            try {
                val scheduled = scheduledCampaignDao
                    .getScheduledCampaignByCampaignId(session.campaignId)
                    .blockingGet()
                if (scheduled != null) {
                    scheduled.markAsExecuting()
                    scheduledCampaignDao.updateScheduledCampaign(scheduled).blockingAwait()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark scheduled campaign executing", e)
            }
        }

        val result = bulkSmsService.sendBulkSmsSession(session) { processed, total ->
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

        // Update scheduled campaign status
        if (session.campaignId > 0) {
            try {
                val scheduled = scheduledCampaignDao
                    .getScheduledCampaignByCampaignId(session.campaignId)
                    .blockingGet()
                if (scheduled != null) {
                    when (result.status) {
                        BulkSmsService.RESULT_COMPLETED -> {
                            scheduled.markAsCompleted()
                            scheduledCampaignDao.updateScheduledCampaign(scheduled).blockingAwait()
                        }
                        BulkSmsService.RESULT_PAUSED -> {
                            scheduled.status = "SCHEDULED"
                            scheduled.updatedAt = System.currentTimeMillis()
                            scheduledCampaignDao.updateScheduledCampaign(scheduled).blockingAwait()
                        }
                        BulkSmsService.RESULT_STOPPED -> {
                            scheduled.cancel()
                            scheduledCampaignDao.updateScheduledCampaign(scheduled).blockingAwait()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update scheduled campaign", e)
            }
        }

        val output = Data.Builder()
            .putString(RESULT_STATUS, result.status)
            .putInt(RESULT_SENT, result.sentCount)
            .putInt(RESULT_FAILED, result.failedCount)
            .putInt(RESULT_SKIPPED, result.skippedCount)
            .putInt(RESULT_TOTAL, result.totalCount)
            .putString(RESULT_ERROR, result.error)
            .build()

        return@withContext if (result.status == BulkSmsService.RESULT_FAILED) {
            Result.failure(output)
        } else {
            Result.success(output)
        }
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sending Bulk SMS")
            .setContentText("Sending $current of $total...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), current, false)
            .build()

        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bulk SMS",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateProgressNotification(current: Int, total: Int) {
        try {
            setForegroundAsync(createForegroundInfo(current, total))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update foreground", e)
        }
    }
}
