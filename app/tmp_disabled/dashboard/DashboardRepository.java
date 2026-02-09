package com.bulksms.smsmanager.dashboard;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

import androidx.annotation.NonNull;


import com.bulksms.smsmanager.models.SmsModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for dashboard data - abstracts data sources
 */
public class DashboardRepository {
    private final Context context;

    public DashboardRepository(Context context) {
        this.context = context;
    }

    public void loadDashboardStats(Callback<DashboardStats> callback) {
        // Load SMS stats
        CompletableFuture<SmsStats> smsStatsFuture = loadSmsStats();

        smsStatsFuture.thenAccept(smsStats -> {
            try {
                DashboardStats stats = new DashboardStats(smsStats);
                callback.onSuccess(stats);
            } catch (Exception e) {
                callback.onError("Failed to load dashboard data: " + e.getMessage());
            }
        }).exceptionally(throwable -> {
            callback.onError("Failed to load dashboard data: " + throwable.getMessage());
            return null;
        });
    }

    private CompletableFuture<SmsStats> loadSmsStats() {
        return CompletableFuture.supplyAsync(() -> {
            SmsStats stats = new SmsStats();
            List<SmsModel> recentActivity = new ArrayList<>();

            try {
                // Get today's SMS activity
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                String[] projection = {"_id", "type", "date", "address", "body"};
                String selection = "date >= ?";
                String[] selectionArgs = {String.valueOf(calendar.getTimeInMillis())};
                String sortOrder = "date DESC";

                try (Cursor cursor = context.getContentResolver()
                        .query(Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, sortOrder)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        int typeIndex = cursor.getColumnIndex("type");
                        int dateIndex = cursor.getColumnIndex("date");
                        int addressIndex = cursor.getColumnIndex("address");
                        int bodyIndex = cursor.getColumnIndex("body");
                        int idIndex = cursor.getColumnIndex("_id");

                        do {
                            int type = cursor.getInt(typeIndex);
                            long date = cursor.getLong(dateIndex);
                            String address = cursor.getString(addressIndex);
                            String body = cursor.getString(bodyIndex);
                            String id = cursor.getString(idIndex);

                            if (address == null) address = "Unknown";
                            if (body == null) body = "";

                            // Count sent/failed
                            if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                                stats.sentCount++;
                            } else if (type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                                stats.failedCount++;
                            }

                            // Add to recent activity (limit to 10)
                            if (recentActivity.size() < 10) {
                                String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                                        .format(new Date(date));
                                String preview = body.length() > 40 ? body.substring(0, 40) + "..." : body;
                                recentActivity.add(new SmsModel(id, address, preview, time, date, type, false, 0));
                            }
                        } while (cursor.moveToNext());
                    }
                }

                stats.recentActivity = recentActivity;

                // Calculate delivery rate
                if (stats.sentCount > 0) {
                    stats.deliveryRate = ((float) (stats.sentCount - stats.failedCount) / stats.sentCount) * 100;
                }

            } catch (SecurityException e) {
                throw new RuntimeException("SMS permission not granted", e);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load SMS data", e);
            }

            return stats;
        });
    }



    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }
}