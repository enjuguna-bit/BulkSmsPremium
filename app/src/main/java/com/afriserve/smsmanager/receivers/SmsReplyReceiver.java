package com.afriserve.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.RemoteInput;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.notifications.SmsNotificationService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * Handles inline reply actions from notifications.
 */
public class SmsReplyReceiver extends BroadcastReceiver {
    public static final String ACTION_REPLY = "com.afriserve.smsmanager.ACTION_INLINE_REPLY";
    private static final String TAG = "SmsReplyReceiver";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_REPLY.equals(intent.getAction())) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        final SmsReplyEntryPoint entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            SmsReplyEntryPoint.class
        );
        final SmsRepository smsRepository = entryPoint.smsRepository();
        final ConversationRepository conversationRepository = entryPoint.conversationRepository();
        executor.execute(() -> {
            try {
                CharSequence replyText = getMessageText(intent);
                String address = intent.getStringExtra(SmsNotificationService.EXTRA_ADDRESS);
                long threadId = intent.getLongExtra(SmsNotificationService.EXTRA_THREAD_ID, -1L);

                if (replyText == null || address == null || address.trim().isEmpty()) {
                    return;
                }

                SmsEntity sms = new SmsEntity();
                sms.phoneNumber = address;
                sms.message = replyText.toString();
                sms.status = "PENDING";
                sms.createdAt = System.currentTimeMillis();
                sms.isRead = true;
                if (threadId > 0) {
                    sms.threadId = threadId;
                }

                smsRepository.sendSms(sms, -1).blockingAwait();
                conversationRepository.updateConversationWithNewMessage(
                    threadId > 0 ? threadId : null,
                    address,
                    sms.createdAt,
                    sms.message,
                    "SENT",
                    false,
                    System.currentTimeMillis()
                ).blockingAwait();

            } catch (Exception e) {
                Log.e(TAG, "Failed to send inline reply", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private CharSequence getMessageText(Intent intent) {
        try {
            android.os.Bundle results = RemoteInput.getResultsFromIntent(intent);
            if (results != null) {
                return results.getCharSequence(SmsNotificationService.KEY_TEXT_REPLY);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface SmsReplyEntryPoint {
        SmsRepository smsRepository();
        ConversationRepository conversationRepository();
    }
}
