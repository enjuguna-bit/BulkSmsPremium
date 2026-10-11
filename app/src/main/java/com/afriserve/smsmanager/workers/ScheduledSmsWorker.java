package com.afriserve.smsmanager.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.WorkerParameters;

import com.afriserve.smsmanager.AppConfig;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.ConversationRepository;
import com.afriserve.smsmanager.data.repository.SmsRepository;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;

/**
 * ScheduledSmsWorker — v2
 *
 * Sends a single pre-composed SMS that was scheduled by the user.
 *
 * Changes vs v1:
 *  - Explicit error logging with the exception message.
 *  - Returns {@link Result#failure()} for permanent errors (null phone/message)
 *    rather than retrying indefinitely.
 *  - Retries transient failures respecting {@link AppConfig.Worker#MAX_RETRY_COUNT}.
 *  - Guards conversation-update from swallowing the failure result.
 *  - Backoff is set by the caller via {@link BackoffPolicy#EXPONENTIAL}; the
 *    worker itself just signals retry/failure appropriately.
 *
 * Note: Extends {@link Worker} (synchronous) because {@code SmsRepository.sendSms}
 * and {@code ConversationRepository.updateConversationWithNewMessage} are RxJava
 * Completables that are already blocking-awaited.  If those APIs gain suspend-
 * function overloads in future, migrate to {@code CoroutineWorker}.
 */
@HiltWorker
public class ScheduledSmsWorker extends Worker {

    private static final String TAG = "ScheduledSmsWorker";

    public static final String KEY_PHONE    = "phone";
    public static final String KEY_MESSAGE  = "message";
    public static final String KEY_THREAD_ID = "thread_id";

    private final SmsRepository smsRepository;
    private final ConversationRepository conversationRepository;

    @AssistedInject
    public ScheduledSmsWorker(
            @Assisted @NonNull Context context,
            @Assisted @NonNull WorkerParameters workerParams,
            SmsRepository smsRepository,
            ConversationRepository conversationRepository
    ) {
        super(context, workerParams);
        this.smsRepository          = smsRepository;
        this.conversationRepository = conversationRepository;
    }

    @NonNull
    @Override
    public Result doWork() {
        String phone   = getInputData().getString(KEY_PHONE);
        String message = getInputData().getString(KEY_MESSAGE);
        long threadId  = getInputData().getLong(KEY_THREAD_ID, -1L);

        // ── Validate input ────────────────────────────────────────────────────
        if (phone == null || phone.isBlank()) {
            Log.e(TAG, "doWork: missing phone number — permanent failure");
            return Result.failure();
        }
        if (message == null || message.isBlank()) {
            Log.e(TAG, "doWork: missing message body — permanent failure");
            return Result.failure();
        }

        Log.d(TAG, "Sending scheduled SMS to " + phone + " (attempt " + (getRunAttemptCount() + 1)
                + "/" + AppConfig.Worker.MAX_RETRY_COUNT + ")");

        try {
            // ── Build entity ──────────────────────────────────────────────────
            SmsEntity sms = new SmsEntity();
            sms.phoneNumber = phone;
            sms.message     = message;
            sms.status      = "PENDING";
            sms.createdAt   = System.currentTimeMillis();
            sms.isRead      = true;
            if (threadId > 0) sms.threadId = threadId;

            // ── Send ──────────────────────────────────────────────────────────
            smsRepository.sendSms(sms, -1).blockingAwait();

            // ── Update conversation ───────────────────────────────────────────
            try {
                conversationRepository.updateConversationWithNewMessage(
                        threadId > 0 ? threadId : null,
                        phone,
                        sms.createdAt,
                        message,
                        "SENT",
                        false,
                        System.currentTimeMillis()
                ).blockingAwait();
            } catch (Exception convEx) {
                // Non-fatal: conversation sync failure should not fail the send
                Log.w(TAG, "Conversation update failed (non-fatal)", convEx);
            }

            Log.d(TAG, "Scheduled SMS sent to " + phone);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Failed to send scheduled SMS to " + phone + ": " + e.getMessage(), e);
            // Retry if we haven't exceeded the limit, else give up
            if (getRunAttemptCount() < AppConfig.Worker.MAX_RETRY_COUNT - 1) {
                Log.w(TAG, "Scheduling retry for " + phone);
                return Result.retry();
            }
            Log.e(TAG, "Max retries exceeded for " + phone + " — permanent failure");
            return Result.failure();
        }
    }
}
