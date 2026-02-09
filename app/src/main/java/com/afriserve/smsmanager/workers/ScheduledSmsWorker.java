package com.afriserve.smsmanager.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import androidx.hilt.work.HiltWorker;

@HiltWorker
public class ScheduledSmsWorker extends Worker {
    public static final String KEY_PHONE = "phone";
    public static final String KEY_MESSAGE = "message";
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
        this.smsRepository = smsRepository;
        this.conversationRepository = conversationRepository;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String phone = getInputData().getString(KEY_PHONE);
            String message = getInputData().getString(KEY_MESSAGE);
            long threadId = getInputData().getLong(KEY_THREAD_ID, -1L);

            if (phone == null || phone.trim().isEmpty() || message == null || message.trim().isEmpty()) {
                return Result.failure();
            }

            SmsEntity sms = new SmsEntity();
            sms.phoneNumber = phone;
            sms.message = message;
            sms.status = "PENDING";
            sms.createdAt = System.currentTimeMillis();
            sms.isRead = true;
            if (threadId > 0) {
                sms.threadId = threadId;
            }

            smsRepository.sendSms(sms, -1).blockingAwait();
            conversationRepository.updateConversationWithNewMessage(
                threadId > 0 ? threadId : null,
                phone,
                sms.createdAt,
                message,
                "SENT",
                false,
                System.currentTimeMillis()
            ).blockingAwait();

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
