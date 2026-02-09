package com.afriserve.smsmanager.ui.dashboard;

import android.content.Context;
import dagger.hilt.android.qualifiers.ApplicationContext;
import android.database.Cursor;
import android.provider.Telephony;

import com.afriserve.smsmanager.models.SmsModel;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for SMS data operations
 */
@Singleton
public class SmsRepository {
    private final Context context;

    @Inject
    public SmsRepository(@ApplicationContext Context context) {
        this.context = context;
    }

    /**
     * Get SMS statistics for today
     */
    public CompletableFuture<SmsStats> getSmsStats() {
        return CompletableFuture.supplyAsync(() -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            String[] projection = {"type"};
            String selection = "date >= ?";
            String[] selectionArgs = {String.valueOf(calendar.getTimeInMillis())};

            int sent = 0, delivered = 0, failed = 0, queued = 0;

            try (Cursor cursor = context.getContentResolver()
                    .query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, null)) {

                if (cursor != null) {
                    int typeIndex = cursor.getColumnIndex("type");

                    while (cursor.moveToNext()) {
                        int type = cursor.getInt(typeIndex);
                        switch (type) {
                            case Telephony.Sms.MESSAGE_TYPE_SENT:
                                sent++;
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                                queued++;
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_FAILED:
                                failed++;
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                // Handle permission or other errors gracefully
                sent = 0;
                delivered = sent - failed; // Approximation
                failed = 0;
                queued = 0;
            }

            return new SmsStats(sent, delivered, failed, queued);
        });
    }

    /**
     * Get recent SMS activity
     */
    public CompletableFuture<List<SmsModel>> getRecentSmsActivity() {
        return CompletableFuture.supplyAsync(() -> {
            List<SmsModel> activityList = new ArrayList<>();

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            String[] projection = {"_id", "type", "date", "address", "body"};
            String selection = "date >= ?";
            String[] selectionArgs = {String.valueOf(calendar.getTimeInMillis())};
            String sortOrder = "date DESC";
            String limit = "10";

            try (Cursor cursor = context.getContentResolver()
                    .query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs,
                          sortOrder + " LIMIT " + limit)) {

                if (cursor != null) {
                    int idIndex = cursor.getColumnIndex("_id");
                    int typeIndex = cursor.getColumnIndex("type");
                    int dateIndex = cursor.getColumnIndex("date");
                    int addressIndex = cursor.getColumnIndex("address");
                    int bodyIndex = cursor.getColumnIndex("body");

                    while (cursor.moveToNext()) {
                        String id = cursor.getString(idIndex);
                        int type = cursor.getInt(typeIndex);
                        long date = cursor.getLong(dateIndex);
                        String address = cursor.getString(addressIndex);
                        String body = cursor.getString(bodyIndex);

                        if (address == null) address = "Unknown";
                        if (body == null) body = "";

                        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(new Date(date));
                        String preview = body.length() > 40 ? body.substring(0, 40) + "..." : body;

                        activityList.add(new SmsModel(id, address, preview, time, date, type, false, 0));
                    }
                }
            } catch (Exception e) {
                // Handle permission errors gracefully
                activityList = new ArrayList<>();
            }

            return activityList;
        });
    }
}