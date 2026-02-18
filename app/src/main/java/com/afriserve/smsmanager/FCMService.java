package com.afriserve.smsmanager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.util.Map;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FCMService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "app_notifications";
    
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage == null) {
            return;
        }

        boolean hasDataPayload = remoteMessage.getData() != null && !remoteMessage.getData().isEmpty();
        boolean hasNotificationPayload = remoteMessage.getNotification() != null;
        if (!hasDataPayload && !hasNotificationPayload) {
            return;
        }

        showNotification(remoteMessage);
    }
    
    private void showNotification(RemoteMessage remoteMessage) {
        // Check if notifications are enabled
        SharedPreferences prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        
        if (!notificationsEnabled) {
            return;
        }
        
        Intent intent = new Intent(this, com.afriserve.smsmanager.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        Map<String, String> data = remoteMessage.getData();

        String title = notification != null ? notification.getTitle() : null;
        String body = notification != null ? notification.getBody() : null;

        if ((title == null || title.trim().isEmpty()) && data != null) {
            title = data.get("title");
        }
        if ((body == null || body.trim().isEmpty()) && data != null) {
            body = data.get("body");
            if (body == null || body.trim().isEmpty()) {
                body = data.get("message");
            }
        }

        if (title == null || title.trim().isEmpty()) {
            title = "Bulk SMS Manager";
        }
        if (body == null || body.trim().isEmpty()) {
            body = "You have a new notification.";
        }
        
        NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        
        // Create channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Billing Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        
        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
    
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Send token to server
        sendRegistrationToServer(token);
    }
    
    private void sendRegistrationToServer(String token) {
        // Store FCM token in Firestore for notifications
        // Implementation depends on your user management
    }
}
