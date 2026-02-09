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
import com.afriserve.smsmanager.R

/**
 * Service to keep the app alive during long processing if needed,
 * though WorkManager handles most of this now.
 */
class BackgroundSmsSenderService : Service() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val timeOutRunnable = Runnable {
        Log.w(TAG, "Service timed out. Stopping self.")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceCompat()
        
        handler.removeCallbacks(timeOutRunnable)
        handler.postDelayed(timeOutRunnable, 120_000L) // 2 min timeout

        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeOutRunnable)
    }

    private fun startForegroundServiceCompat() {
        val channelId = "bulk_sms_background_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Bulk SMS Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sending Bulk SMS")
            .setContentText("Processing...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        const val TAG = "BackgroundSmsService"
    }
}
