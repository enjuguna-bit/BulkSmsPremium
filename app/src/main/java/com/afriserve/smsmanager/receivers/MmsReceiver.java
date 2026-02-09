package com.afriserve.smsmanager.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

/**
 * BroadcastReceiver for MMS messages (WAP_PUSH)
 * Required for default SMS app functionality
 */
public class MmsReceiver extends BroadcastReceiver {
    
    private static final String TAG = "MmsReceiver";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        Log.d(TAG, "MMS received: " + intent.getAction());

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        final MmsReceiverEntryPoint entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            MmsReceiverEntryPoint.class
        );
        final SmsRepository smsRepository = entryPoint.smsRepository();
        final ConversationRepository conversationRepository = entryPoint.conversationRepository();

        executor.execute(() -> {
            try {
                smsRepository.syncRecentMessages()
                    .andThen(conversationRepository.syncConversationsFromMessages())
                    .blockingAwait();
                Log.d(TAG, "MMS sync completed");
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync MMS messages", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface MmsReceiverEntryPoint {
        SmsRepository smsRepository();
        ConversationRepository conversationRepository();
    }
}
