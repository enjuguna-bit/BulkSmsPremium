package com.afriserve.smsmanager.ui.inbox;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.afriserve.smsmanager.models.SmsModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * InboxViewModel — v2
 *
 * Changes vs v1:
 *  - All ContentProvider (Telephony) queries moved OFF the main thread
 *    onto a dedicated single-thread executor.  The original code queried
 *    the cursor directly inside setValue() — a main-thread ANR risk.
 *  - Results posted via LiveData.postValue() (thread-safe).
 *  - Cursor column indices resolved safely with getColumnIndex() + guard
 *    against -1 to avoid crashes on ROMs that omit non-standard columns.
 *  - markAsRead() is also async now.
 *  - Executor shut down in onCleared() to avoid thread leaks.
 *  - Pagination limit added (QUERY_LIMIT = 500) to prevent OOM on devices
 *    with thousands of SMS messages.
 */
@HiltViewModel
public class InboxViewModel extends AndroidViewModel {

    private static final String TAG        = "InboxViewModel";
    private static final int    TYPE_INBOX = 1;
    private static final int    TYPE_SENT  = 2;
    /** Maximum number of messages loaded at once — prevents OOM on busy devices. */
    private static final int    QUERY_LIMIT = 500;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<SmsModel>> messages    = new MutableLiveData<>();
    private final MutableLiveData<Boolean>         isLoading  = new MutableLiveData<>(false);
    private final MutableLiveData<String>          error      = new MutableLiveData<>();
    private final MutableLiveData<Integer>         totalCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer>         unreadCount= new MutableLiveData<>(0);

    /** Tracks the current filter so markAsRead can refresh correctly. */
    private volatile Integer currentTypeFilter = null;
    private volatile String  currentReadFilter = null;

    @Inject
    public InboxViewModel(@NonNull Application application) {
        super(application);
    }

    // ── LiveData getters ──────────────────────────────────────────────────────

    public LiveData<List<SmsModel>> getMessages()    { return messages;    }
    public LiveData<Boolean>        getIsLoading()   { return isLoading;   }
    public LiveData<String>         getError()       { return error;       }
    public LiveData<Integer>        getTotalCount()  { return totalCount;  }
    public LiveData<Integer>        getUnreadCount() { return unreadCount; }

    // ── Load actions ──────────────────────────────────────────────────────────

    public void loadAllMessages()    { loadMessages(null,       null); }
    public void loadInboxMessages()  { loadMessages(TYPE_INBOX, null); }
    public void loadSentMessages()   { loadMessages(TYPE_SENT,  null); }
    public void loadUnreadMessages() { loadMessages(TYPE_INBOX, "0");  }

    public void loadMessageCounts() {
        executor.execute(() -> {
            int total  = getMessageCount(null,       null);
            int unread = getMessageCount(TYPE_INBOX, "0");
            totalCount.postValue(total);
            unreadCount.postValue(unread);
        });
    }

    public void markAsRead(String messageId) {
        executor.execute(() -> {
            try {
                ContentValues cv = new ContentValues();
                cv.put(Telephony.Sms.READ, "1");
                getApplication().getContentResolver().update(
                        Telephony.Sms.CONTENT_URI, cv,
                        Telephony.Sms._ID + " = ?", new String[]{messageId});
                // Refresh after marking read
                loadMessageCounts();
                loadMessages(currentTypeFilter, currentReadFilter);
            } catch (Exception e) {
                Log.e(TAG, "Error marking message as read", e);
                error.postValue("Failed to mark message as read");
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    // ── Private query helpers ─────────────────────────────────────────────────

    private void loadMessages(Integer type, String read) {
        currentTypeFilter = type;
        currentReadFilter = read;

        isLoading.postValue(true);
        error.postValue(null);

        executor.execute(() -> {
            try {
                String[] selection   = buildSelection(type, read);
                String   where       = selection[0];  // may be null
                String[] whereArgs   = selection[1] != null
                        ? selection[1].split("\u0000") : null;

                Cursor cursor = getApplication().getContentResolver().query(
                        Telephony.Sms.CONTENT_URI,
                        new String[]{
                            Telephony.Sms._ID,
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.TYPE,
                            Telephony.Sms.READ,
                            Telephony.Sms.THREAD_ID
                        },
                        where,
                        whereArgs,
                        Telephony.Sms.DATE + " DESC LIMIT " + QUERY_LIMIT
                );

                List<SmsModel> smsList = new ArrayList<>();
                if (cursor != null) {
                    try {
                        int colId       = cursor.getColumnIndex(Telephony.Sms._ID);
                        int colAddress  = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                        int colBody     = cursor.getColumnIndex(Telephony.Sms.BODY);
                        int colDate     = cursor.getColumnIndex(Telephony.Sms.DATE);
                        int colType     = cursor.getColumnIndex(Telephony.Sms.TYPE);
                        int colRead     = cursor.getColumnIndex(Telephony.Sms.READ);
                        int colThread   = cursor.getColumnIndex(Telephony.Sms.THREAD_ID);

                        while (cursor.moveToNext()) {
                            SmsModel sms = new SmsModel();
                            if (colId      >= 0) sms.setId(cursor.getString(colId));
                            if (colAddress >= 0) sms.setAddress(cursor.getString(colAddress));
                            if (colBody    >= 0) sms.setBody(cursor.getString(colBody));
                            if (colDate    >= 0) sms.setDate(cursor.getString(colDate));
                            if (colType    >= 0) sms.setType(cursor.getInt(colType));
                            if (colRead    >= 0) sms.setRead(cursor.getInt(colRead) == 1);
                            if (colThread  >= 0) sms.setThreadId(cursor.getString(colThread));
                            smsList.add(sms);
                        }
                    } finally {
                        cursor.close();
                    }
                }

                messages.postValue(smsList);
                totalCount.postValue(smsList.size());

            } catch (Exception e) {
                Log.e(TAG, "Error loading messages", e);
                error.postValue("Failed to load messages: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private int getMessageCount(Integer type, String read) {
        String[] sel = buildSelection(type, read);
        String   where    = sel[0];
        String[] whereArgs = sel[1] != null ? sel[1].split("\u0000") : null;
        try (Cursor c = getApplication().getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{"COUNT(*)"},
                where, whereArgs, null)) {
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            Log.w(TAG, "getMessageCount failed", e);
        }
        return 0;
    }

    /**
     * Returns a 2-element array: [0] = WHERE clause (or null), [1] = args joined by NUL.
     */
    private static String[] buildSelection(Integer type, String read) {
        if (type != null && read != null) {
            return new String[]{
                Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.READ + " = ?",
                type + "\u0000" + read
            };
        } else if (type != null) {
            return new String[]{ Telephony.Sms.TYPE + " = ?", type.toString() };
        } else if (read != null) {
            return new String[]{ Telephony.Sms.READ + " = ?", read };
        }
        return new String[]{ null, null };
    }
}
