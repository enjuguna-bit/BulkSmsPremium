package com.afriserve.smsmanager.ui.sms;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;

/**
 * ViewModel for Single SMS operations
 * Handles sending individual SMS and tracking status
 * Uses SmsRepository to send and persist messages
 */
@HiltViewModel
public class SingleSmsViewModel extends ViewModel {

    private static final String TAG = "SingleSmsViewModel";

    private final SmsRepository smsRepository;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> status = new MutableLiveData<>("");
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public SingleSmsViewModel(@ApplicationContext Context context, SmsRepository smsRepository) {
        this.smsRepository = smsRepository;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * Send single SMS
     */
    public void sendSms(String phoneNumber, String message, int simSlot) {
        CompletableFuture.runAsync(() -> {
            try {
                isLoading.postValue(true);
                status.postValue("Sending SMS...");
                error.postValue(null);

                // Use a dummy ID since we don't have DB insertion
                SmsEntity sms = new SmsEntity();
                sms.phoneNumber = phoneNumber;
                sms.message = message;
                sms.status = "PENDING";
                sms.createdAt = System.currentTimeMillis();
                sms.isRead = true;
                smsRepository.sendSms(sms, simSlot).blockingAwait();

                Log.d(TAG, "SMS sent to: " + phoneNumber);
                status.postValue("SMS sent");

            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS", e);
                error.postValue(e.getMessage());
                status.postValue("Send failed");
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Send the same SMS to multiple recipients
     */
    public void sendSmsToMultiple(List<String> phoneNumbers, String message, int simSlot) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            error.postValue("No recipients selected");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                int total = phoneNumbers.size();
                int sent = 0;
                int failed = 0;

                isLoading.postValue(true);
                status.postValue("Sending SMS to " + total + " recipients...");
                error.postValue(null);

                for (String phoneNumber : phoneNumbers) {
                    try {
                        SmsEntity sms = new SmsEntity();
                        sms.phoneNumber = phoneNumber;
                        sms.message = message;
                        sms.status = "PENDING";
                        sms.createdAt = System.currentTimeMillis();
                        sms.isRead = true;
                        smsRepository.sendSms(sms, simSlot).blockingAwait();

                        sent++;
                        status.postValue("Sent " + sent + " of " + total);
                    } catch (Exception e) {
                        failed++;
                        Log.e(TAG, "Failed to send SMS to: " + phoneNumber, e);
                        if (failed == 1) {
                            error.postValue(e.getMessage());
                        }
                    }
                }

                if (failed == 0) {
                    status.postValue("SMS sent to " + sent + " recipients");
                } else {
                    status.postValue("Sent " + sent + " of " + total + " (" + failed + " failed)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send bulk SMS", e);
                error.postValue(e.getMessage());
                status.postValue("Send failed");
            } finally {
                isLoading.postValue(false);
            }
        });
    }
}
