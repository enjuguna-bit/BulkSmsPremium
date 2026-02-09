package com.afriserve.smsmanager.ui.inbox;

import android.app.Application;
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

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class InboxViewModel extends AndroidViewModel {
    private static final String TAG = "InboxViewModel";
    private static final int TYPE_INBOX = 1;
    private static final int TYPE_SENT = 2;
    
    private final MutableLiveData<List<SmsModel>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Integer> totalCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    
    @Inject
    public InboxViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<SmsModel>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getTotalCount() {
        return totalCount;
    }

    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    public void loadAllMessages() {
        loadMessages(null, null);
    }

    public void loadInboxMessages() {
        loadMessages(TYPE_INBOX, null);
    }

    public void loadSentMessages() {
        loadMessages(TYPE_SENT, null);
    }

    public void loadUnreadMessages() {
        loadMessages(TYPE_INBOX, "0"); // read = 0 means unread
    }

    public void loadMessageCounts() {
        try {
            int total = getMessageCount(null, null);
            int unread = getMessageCount(TYPE_INBOX, "0");
            
            totalCount.postValue(total);
            unreadCount.postValue(unread);
        } catch (Exception e) {
            Log.e(TAG, "Error loading message counts", e);
            error.postValue("Failed to load message counts");
        }
    }

    private void loadMessages(Integer type, String read) {
        isLoading.setValue(true);
        error.setValue(null);

        try {
            List<SmsModel> smsList = new ArrayList<>();
            String selection = null;
            String[] selectionArgs = null;

            if (type != null && read != null) {
                selection = Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.READ + " = ?";
                selectionArgs = new String[]{type.toString(), read};
            } else if (type != null) {
                selection = Telephony.Sms.TYPE + " = ?";
                selectionArgs = new String[]{type.toString()};
            } else if (read != null) {
                selection = Telephony.Sms.READ + " = ?";
                selectionArgs = new String[]{read};
            }

            Cursor cursor = getApplication().getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    selection,
                    selectionArgs,
                    Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                try {
                    int idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID);
                    int addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
                    int bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
                    int dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
                    int typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE);
                    int readIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.READ);
                    int threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID);

                    while (cursor.moveToNext()) {
                        SmsModel sms = new SmsModel();
                        sms.setId(cursor.getString(idIndex));
                        sms.setAddress(cursor.getString(addressIndex));
                        sms.setBody(cursor.getString(bodyIndex));
                        sms.setDate(cursor.getString(dateIndex));
                        sms.setType(cursor.getInt(typeIndex));
                        sms.setRead(cursor.getInt(readIndex) == 1);
                        sms.setThreadId(cursor.getString(threadIdIndex));
                        
                        smsList.add(sms);
                    }
                } finally {
                    cursor.close();
                }
            }

            messages.postValue(smsList);
            isLoading.postValue(false);

        } catch (Exception e) {
            Log.e(TAG, "Error loading messages", e);
            error.postValue("Failed to load messages: " + e.getMessage());
            isLoading.postValue(false);
        }
    }

    private int getMessageCount(Integer type, String read) {
        try {
            String selection = null;
            String[] selectionArgs = null;

            if (type != null && read != null) {
                selection = Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.READ + " = ?";
                selectionArgs = new String[]{type.toString(), read};
            } else if (type != null) {
                selection = Telephony.Sms.TYPE + " = ?";
                selectionArgs = new String[]{type.toString()};
            } else if (read != null) {
                selection = Telephony.Sms.READ + " = ?";
                selectionArgs = new String[]{read};
            }

            Cursor cursor = getApplication().getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{"COUNT(*)"},
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0);
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting message count", e);
        }
        return 0;
    }

    public void markAsRead(String messageId) {
        try {
            // Update the message as read
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(Telephony.Sms.READ, "1");
            
            String where = Telephony.Sms._ID + " = ?";
            String[] whereArgs = new String[]{messageId};
            
            int rowsUpdated = getApplication().getContentResolver().update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    where,
                    whereArgs
            );

            if (rowsUpdated > 0) {
                // Refresh the current list
                loadMessageCounts();
                // Reload current filter
                // This is a simple approach - in a real app you might want to track current filter
                loadAllMessages();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error marking message as read", e);
            error.postValue("Failed to mark message as read");
        }
    }
}
