package com.afriserve.smsmanager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * FCMService — v2
 *
 * Changes vs v1:
 *  - Notification channel ID sourced from AppConfig.Worker.CHANNEL_ID so it
 *    is never duplicated across the codebase.
 *  - createNotificationChannel() called lazily once per showNotification()
 *    via ensureChannel() — idempotent, safe to call repeatedly.
 *  - Added null-guard on data map before accessing keys.
 *  - Notification now uses a unique request code (based on current time) so
 *    rapid successive FCM messages don't overwrite each other's PendingIntent.
 *  - Log tag constant added; log level DEBUG for data payloads (not INFO).
 */
public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String PREFS_SETTINGS = "settings";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        if (remoteMessage == null) return;

        boolean hasData = remoteMessage.getData() != null && !remoteMessage.getData().isEmpty();
        boolean hasNotif = remoteMessage.getNotification() != null;

        if (!hasData && !hasNotif) return;

        Log.d(TAG, "FCM message received — hasData=" + hasData + ", hasNotif=" + hasNotif);
        showNotification(remoteMessage);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Store or forward the new registration token as needed.
        Log.d(TAG, "FCM token refreshed");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void showNotification(RemoteMessage message) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)) return;

        ensureChannel();

        Map<String, String> data = message.getData();
        RemoteMessage.Notification notif = message.getNotification();

        String title = notif != null ? notif.getTitle() : null;
        String body  = notif != null ? notif.getBody()  : null;

        if ((title == null || title.trim().isEmpty()) && data != null) title = data.get("title");
        if ((body  == null || body.trim().isEmpty())  && data != null) body  = data.get("body");

        if (title == null) title = getString(R.string.app_name);
        if (body  == null) body  = "";

        // Unique request code prevents PendingIntents from overwriting each other
        int requestCode = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, AppConfig.Worker.CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(requestCode, builder.build());
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AppConfig.Worker.CHANNEL_ID,
                    "Bulk SMS",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Bulk SMS Manager notifications");
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
