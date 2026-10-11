package com.afriserve.smsmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.afriserve.smsmanager.AppConfig
import com.afriserve.smsmanager.R

/**
 * Lightweight foreground service that keeps the app process alive during a
 * bulk-send session when WorkManager alone is not sufficient.
 *
 * WorkManager handles most scheduling; start this service only as a safety net
 * for long-running campaigns that exceed WorkManager's 10-minute window.
 *
 * The service self-stops after [AppConfig.Service.SERVICE_TIMEOUT_MS] to
 * prevent indefinite battery drain.
 */
class BackgroundSmsSenderService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val timeOutRunnable = Runnable {
        Log.w(TAG, "Service timed out after ${AppConfig.Service.SERVICE_TIMEOUT_MS / 1_000}s. Stopping self.")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started (startId=$startId)")
        startForegroundServiceCompat()

        // Reset the watchdog timer on each START command so the service can
        // be kept alive by re-sending the intent, up to the max timeout.
        handler.removeCallbacks(timeOutRunnable)
        handler.postDelayed(timeOutRunnable, AppConfig.Service.SERVICE_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeOutRunnable)
        Log.d(TAG, "Service destroyed.")
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun startForegroundServiceCompat() {
        val channelId = "bulk_sms_background_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Bulk SMS Service",
                NotificationManager.IMPORTANCE_LOW
            )
            chan.description = "Keeps the bulk-SMS sending process alive in the background."
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sending Bulk SMS")
            .setContentText("Processing in background...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                AppConfig.Service.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AppConfig.Service.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "BackgroundSmsService"
    }
}
