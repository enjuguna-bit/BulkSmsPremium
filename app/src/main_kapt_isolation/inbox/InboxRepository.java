package com.afriserve.smsmanager.ui.inbox;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import dagger.hilt.android.qualifiers.ApplicationContext;
import com.afriserve.smsmanager.models.SmsModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Singleton
public class InboxRepository {
    private final Context context;

    @Inject
    public InboxRepository(@ApplicationContext Context context) {
        this.context = context;
    }

    public CompletableFuture<List<SmsModel>> getAllMessages() {
        return CompletableFuture.supplyAsync(() -> {
            List<SmsModel> messages = new ArrayList<>();

            try {
                String[] projection = { "_id", "address", "body", "date", "type", "read", "thread_id" };
                String sortOrder = "date DESC";

                try (Cursor cursor = context.getContentResolver().query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        null,
                        null,
                        sortOrder)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        int idIdx = cursor.getColumnIndex("_id");
                        int addrIdx = cursor.getColumnIndex("address");
                        int bodyIdx = cursor.getColumnIndex("body");
                        int dateIdx = cursor.getColumnIndex("date");
                        int typeIdx = cursor.getColumnIndex("type");
                        int readIdx = cursor.getColumnIndex("read");
                        int threadIdx = cursor.getColumnIndex("thread_id");

                        do {
                            String id = cursor.getString(idIdx);
                            String address = cursor.getString(addrIdx);
                            String body = cursor.getString(bodyIdx);
                            long timestamp = cursor.getLong(dateIdx);
                            int type = cursor.getInt(typeIdx);
                            boolean isRead = cursor.getInt(readIdx) == 1;
                            int threadId = cursor.getInt(threadIdx);

                            String dateStr = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                                    .format(new Date(timestamp));

                            messages.add(new SmsModel(
                                    id,
                                    address != null ? address : "Unknown",
                                    body != null ? body : "",
                                    dateStr,
                                    timestamp,
                                    type,
                                    isRead,
                                    threadId));
                        } while (cursor.moveToNext());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return messages;
        });
    }
}
