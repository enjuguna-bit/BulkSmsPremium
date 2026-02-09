package com.afriserve.smsmanager.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.data.utils.PhoneNumberUtils;
import com.afriserve.smsmanager.receivers.SmsReplyReceiver;
import com.afriserve.smsmanager.ui.conversation.ConversationActivity;

/**
 * Handles SMS notifications with inline reply and messaging style.
 */
public class SmsNotificationService {
    public static final String EXTRA_ADDRESS = "extra_address";
    public static final String EXTRA_THREAD_ID = "extra_thread_id";
    public static final String KEY_TEXT_REPLY = "key_text_reply";

    private static final String TAG = "SmsNotificationService";
    private static final String CHANNEL_ID = "sms_messages";
    private static final String CHANNEL_NAME = "SMS Messages";
    private static final String GROUP_KEY_SMS = "com.afriserve.smsmanager.SMS_GROUP";
    private static final int SUMMARY_ID = 9000;

    private final Context context;
    private final ContactResolver contactResolver;

    public SmsNotificationService(Context context, ContactResolver contactResolver) {
        this.context = context.getApplicationContext();
        this.contactResolver = contactResolver;
    }

    public void showIncomingSmsNotification(String address, String body, long threadId, long timestamp) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }

        if (!areNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled; skipping notify");
            return;
        }

        createNotificationChannel();

        String normalized = PhoneNumberUtils.normalizePhoneNumber(address);
        String key = normalized != null ? normalized : address;
        String displayName = contactResolver != null ? contactResolver.getContactName(address) : address;
        String messageBody = !TextUtils.isEmpty(body) ? body : "New message";
        long when = timestamp > 0 ? timestamp : System.currentTimeMillis();

        int notificationId = getNotificationId(key, threadId);

        Person sender = new Person.Builder()
            .setName(displayName)
            .build();
        Person me = new Person.Builder()
            .setName("You")
            .build();

        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(me)
            .setConversationTitle(displayName)
            .addMessage(messageBody, when, sender);

        PendingIntent contentIntent = buildContentIntent(address, threadId, notificationId);
        NotificationCompat.Action replyAction = buildReplyAction(address, threadId, notificationId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setContentTitle(displayName)
            .setContentText(messageBody)
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_SMS);

        if (replyAction != null) {
            builder.addAction(replyAction);
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        maybeShowSummary(displayName, messageBody, notificationId);
    }

    public void cancelNotificationForConversation(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        String normalized = PhoneNumberUtils.normalizePhoneNumber(address);
        String key = normalized != null ? normalized : address;
        int notificationId = getNotificationId(key, -1L);
        NotificationManagerCompat.from(context).cancel(notificationId);
        cleanupSummaryIfNeeded();
    }

    private boolean areNotificationsEnabled() {
        try {
            android.content.SharedPreferences prefs =
                context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("notifications_enabled", true);
            return enabled && NotificationManagerCompat.from(context).areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Incoming SMS notifications");
        notificationManager.createNotificationChannel(channel);
    }

    private PendingIntent buildContentIntent(String address, long threadId, int requestCode) {
        Intent intent = new Intent(context, ConversationActivity.class);
        intent.putExtra("address", address);
        if (threadId > 0) {
            intent.putExtra("thread_id", threadId);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            buildPendingIntentFlags(false)
        );
    }

    private NotificationCompat.Action buildReplyAction(String address, long threadId, int requestCode) {
        try {
            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Reply")
                .build();

            Intent replyIntent = new Intent(context, SmsReplyReceiver.class);
            replyIntent.setAction(SmsReplyReceiver.ACTION_REPLY);
            replyIntent.putExtra(EXTRA_ADDRESS, address);
            replyIntent.putExtra(EXTRA_THREAD_ID, threadId);

            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                replyIntent,
                buildPendingIntentFlags(true)
            );

            return new NotificationCompat.Action.Builder(
                R.drawable.ic_send_sms_action,
                "Reply",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build();
        } catch (Exception e) {
            Log.w(TAG, "Failed to build reply action", e);
            return null;
        }
    }

    private int buildPendingIntentFlags(boolean mutable) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private int getNotificationId(String key, long threadId) {
        if (threadId > 0) {
            long safe = threadId % Integer.MAX_VALUE;
            return (int) (safe + 1000);
        }
        int hash = key != null ? key.hashCode() : 0;
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }
        return Math.abs(hash) + 1000;
    }

    private void maybeShowSummary(String displayName, String messageBody, int currentId) {
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        if (!shouldShowSummary(notificationManager, currentId)) {
            return;
        }

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle()
            .addLine(displayName + ": " + messageBody);

        NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setContentTitle("New messages")
            .setContentText("You have new messages")
            .setStyle(inboxStyle)
            .setGroup(GROUP_KEY_SMS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summaryBuilder.build());
    }

    private boolean shouldShowSummary(NotificationManager notificationManager, int currentId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        try {
            StatusBarNotification[] active = notificationManager.getActiveNotifications();
            int count = 0;
            for (StatusBarNotification sbn : active) {
                if (sbn == null) continue;
                if (sbn.getId() == SUMMARY_ID || sbn.getId() == currentId) continue;
                String group = sbn.getNotification().getGroup();
                if (GROUP_KEY_SMS.equals(group)) {
                    count++;
                }
            }
            return count >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupSummaryIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        try {
            StatusBarNotification[] active = notificationManager.getActiveNotifications();
            boolean hasOther = false;
            for (StatusBarNotification sbn : active) {
                if (sbn == null) continue;
                if (sbn.getId() == SUMMARY_ID) continue;
                String group = sbn.getNotification().getGroup();
                if (GROUP_KEY_SMS.equals(group)) {
                    hasOther = true;
                    break;
                }
            }
            if (!hasOther) {
                NotificationManagerCompat.from(context).cancel(SUMMARY_ID);
            }
        } catch (Exception ignored) {
        }
    }
}
