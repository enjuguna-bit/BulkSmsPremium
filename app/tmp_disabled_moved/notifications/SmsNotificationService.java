package com.bulksms.smsmanager.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.bulksms.smsmanager.MainActivity;
import com.bulksms.smsmanager.R;
import com.bulksms.smsmanager.data.contacts.ContactResolver;
import com.bulksms.smsmanager.ui.conversation.ConversationFragment;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * SMS Notification Service
 * Handles notifications for incoming SMS messages
 */
@Singleton
public class SmsNotificationService {
    
    private static final String TAG = "SmsNotificationService";
    
    // Notification channels
    private static final String CHANNEL_SMS = "sms_messages";
    private static final String CHANNEL_SMS_NAME = "SMS Messages";
    private static final String CHANNEL_FAILED = "failed_sms";
    private static final String CHANNEL_FAILED_NAME = "Failed SMS";
    
    // Notification IDs
    private static final int NOTIFICATION_SMS_BASE = 1000;
    private static final int NOTIFICATION_FAILED_BASE = 2000;
    
    private final Context context;
    private final ContactResolver contactResolver;
    private final NotificationManagerCompat notificationManager;
    private final SharedPreferences preferences;
    
    @Inject
    public SmsNotificationService(
            @ApplicationContext Context context,
            ContactResolver contactResolver
    ) {
        this.context = context;
        this.contactResolver = contactResolver;
        this.notificationManager = NotificationManagerCompat.from(context);
        this.preferences = context.getSharedPreferences("sms_notifications", Context.MODE_PRIVATE);
        
        createNotificationChannels();
    }
    
    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // SMS messages channel
            NotificationChannel smsChannel = new NotificationChannel(
                    CHANNEL_SMS,
                    CHANNEL_SMS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            smsChannel.setDescription("Notifications for incoming SMS messages");
            smsChannel.enableLights(true);
            smsChannel.enableVibration(true);
            smsChannel.setShowBadge(true);
            
            // Failed SMS channel
            NotificationChannel failedChannel = new NotificationChannel(
                    CHANNEL_FAILED,
                    CHANNEL_FAILED_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            failedChannel.setDescription("Notifications for failed SMS messages");
            failedChannel.enableLights(true);
            failedChannel.enableVibration(false);
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(smsChannel);
                manager.createNotificationChannel(failedChannel);
                Log.d(TAG, "Notification channels created");
            }
        }
    }
    
    /**
     * Show notification for incoming SMS
     */
    public void showIncomingSmsNotification(String phoneNumber, String message, long messageId) {
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled");
            return;
        }
        
        try {
            String contactName = contactResolver.getContactName(phoneNumber);
            String displayName = contactName != null ? contactName : phoneNumber;
            
            // Create intent for opening conversation
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra("open_conversation", phoneNumber);
            intent.putExtra("message_id", messageId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    (int) (messageId % Integer.MAX_VALUE),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SMS)
                    .setSmallIcon(R.drawable.ic_sms_notification)
                    .setContentTitle("New message from " + displayName)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(pendingIntent)
                    .setNumber(getUnreadCount() + 1);
            
            // Show notification
            int notificationId = NOTIFICATION_SMS_BASE + (int) (messageId % Integer.MAX_VALUE);
            notificationManager.notify(notificationId, builder.build());
            
            Log.d(TAG, "Showing SMS notification for: " + displayName);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show SMS notification", e);
        }
    }
    
    /**
     * Show notification for failed SMS
     */
    public void showFailedSmsNotification(String phoneNumber, String message, String error) {
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled");
            return;
        }
        
        try {
            String contactName = contactResolver.getContactName(phoneNumber);
            String displayName = contactName != null ? contactName : phoneNumber;
            
            // Create intent for opening app
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_FAILED)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Failed to send message to " + displayName)
                    .setContentText("Error: " + error)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Failed to send: " + message + "\nError: " + error))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            
            // Show notification
            int notificationId = NOTIFICATION_FAILED_BASE + (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            notificationManager.notify(notificationId, builder.build());
            
            Log.d(TAG, "Showing failed SMS notification for: " + displayName);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show failed SMS notification", e);
        }
    }
    
    /**
     * Clear SMS notification for a specific message
     */
    public void clearSmsNotification(long messageId) {
        int notificationId = NOTIFICATION_SMS_BASE + (int) (messageId % Integer.MAX_VALUE);
        notificationManager.cancel(notificationId);
        Log.d(TAG, "Cleared SMS notification: " + notificationId);
    }
    
    /**
     * Clear all SMS notifications
     */
    public void clearAllSmsNotifications() {
        // Cancel all notifications in SMS channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.cancel(CHANNEL_SMS, 0);
        } else {
            // For older versions, cancel a range of IDs
            for (int i = NOTIFICATION_SMS_BASE; i < NOTIFICATION_SMS_BASE + 100; i++) {
                notificationManager.cancel(i);
            }
        }
        Log.d(TAG, "Cleared all SMS notifications");
    }
    
    /**
     * Check if notifications are enabled
     */
    private boolean areNotificationsEnabled() {
        // Check system-level notification permission
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "System notifications are disabled");
            return false;
        }
        
        // Check app-level preference
        return preferences.getBoolean("notifications_enabled", true);
    }
    
    /**
     * Get unread message count
     */
    private int getUnreadCount() {
        return preferences.getInt("unread_count", 0);
    }
    
    /**
     * Update unread count
     */
    public void updateUnreadCount(int count) {
        preferences.edit()
                .putInt("unread_count", count)
                .apply();
        Log.d(TAG, "Updated unread count: " + count);
    }
    
    /**
     * Enable/disable notifications
     */
    public void setNotificationsEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean("notifications_enabled", enabled)
                .apply();
        Log.d(TAG, "Notifications " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if app has notification permission
     */
    public boolean hasNotificationPermission() {
        return notificationManager.areNotificationsEnabled();
    }
    
    /**
     * Open app notification settings
     */
    public void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
