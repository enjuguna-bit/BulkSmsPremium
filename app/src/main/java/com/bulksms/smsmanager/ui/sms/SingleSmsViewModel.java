package com.bulksms.smsmanager.ui.sms;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.bulksms.smsmanager.data.dao.SmsDao;
import com.bulksms.smsmanager.data.entity.SmsEntity;
import com.bulksms.smsmanager.data.error.SmsErrorHandler;
import com.bulksms.smsmanager.data.error.SmsErrorHandler.ErrorInfo;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import io.reactivex.rxjava3.core.Single;

/**
 * ViewModel for Single SMS operations
 * Handles sending individual SMS and tracking status
 */
@HiltViewModel
public class SingleSmsViewModel extends ViewModel {
    
    private static final String TAG = "SingleSmsViewModel";
    private static final String SMS_SENT_ACTION = "com.bulksms.smsmanager.SMS_SENT";
    private static final String SMS_DELIVERED_ACTION = "com.bulksms.smsmanager.SMS_DELIVERED";
    
    private final SmsDao smsDao;
    private final Context context;
    private final SmsErrorHandler errorHandler;
    
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> status = new MutableLiveData<>("");
    private final MutableLiveData<String> error = new MutableLiveData<>();
    
    private BroadcastReceiver sentReceiver;
    private BroadcastReceiver deliveredReceiver;
    
    @Inject
    public SingleSmsViewModel(SmsDao smsDao, @ApplicationContext Context context, SmsErrorHandler errorHandler) {
        this.smsDao = smsDao;
        this.context = context;
        this.errorHandler = errorHandler;
        registerReceivers();
    }
    
    private void registerReceivers() {
        // Note: Receivers are now registered in AndroidManifest.xml
        // These local receivers are kept for backward compatibility but may not be needed
        // The manifest receivers (SmsSentReceiver, SmsDeliveredReceiver) will handle updates
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
     * Send single SMS using coroutines
     */
    public void sendSms(String phoneNumber, String message, int simSlot) {
        CompletableFuture.runAsync(() -> {
            try {
                isLoading.postValue(true);
                status.postValue("Sending SMS...");
                error.postValue(null);
                
                // Create SMS entity before sending
                SmsEntity smsEntity = createSmsEntity(phoneNumber, message);
                
                // Insert into database
                Single<Long> insertSingle = smsDao.insertSms(smsEntity);
                Long smsId = insertSingle.blockingGet();
                Log.d(TAG, "Created SMS entity with ID: " + smsId);
                
                // Send SMS
                sendSmsInternal(phoneNumber, message, smsId, simSlot);
                
                Log.d(TAG, "SMS sent to: " + phoneNumber);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SMS", e);
                ErrorInfo errorInfo = errorHandler.handleSmsError(e, phoneNumber);
                error.postValue(errorInfo.userMessage);
                status.postValue("Send failed");
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    private SmsEntity createSmsEntity(String phoneNumber, String message) {
        SmsEntity smsEntity = new SmsEntity();
        smsEntity.phoneNumber = phoneNumber;
        smsEntity.message = message;
        smsEntity.status = "PENDING";
        smsEntity.boxType = 2; // Sent message
        smsEntity.isRead = true; // Sent messages are considered "read"
        smsEntity.createdAt = System.currentTimeMillis();
        return smsEntity;
    }
    
    private void sendSmsInternal(String phoneNumber, String message, Long smsId, int simSlot) throws Exception {
        SmsManager smsManager = SmsManager.getDefault();

        // Dual-SIM support (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SubscriptionManager subscriptionManager = 
                (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            
            if (subscriptionManager != null) {
                SubscriptionInfo subscriptionInfo = null;
                
                // Check for READ_PHONE_STATE permission (required for API 31+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, 
                        android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(simSlot);
                    } else {
                        Log.w(TAG, "READ_PHONE_STATE permission not granted, using default SIM");
                    }
                } else {
                    subscriptionInfo = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(simSlot);
                }
                
                int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                if (subscriptionInfo != null) {
                    subId = subscriptionInfo.getSubscriptionId();
                }
                
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
                } else {
                    // Fallback to default SIM if selected slot is empty
                    Log.w(TAG, "No active SIM in slot " + simSlot + ", falling back to default");
                    // Optional: post a warning to UI
                    // status.postValue("Selected SIM empty, using default");
                }
            }
        } else {
            Log.w(TAG, "Device API < 29, SIM selection not supported");
        }

        // --- Rest of your existing PendingIntent and send logic (unchanged) ---
        Intent sentIntent = new Intent(SMS_SENT_ACTION);
        sentIntent.putExtra("sms_id", smsId);
        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
            context,
            (int) (smsId % Integer.MAX_VALUE),
            sentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
        deliveredIntent.putExtra("sms_id", smsId);
        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
            context,
            (int) (smsId + 10000),
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        ArrayList<String> parts = smsManager.divideMessage(message);

        if (parts.size() == 1) {
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                sentPendingIntent,
                deliveredPendingIntent
            );
        } else {
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentPendingIntent);
                deliveredIntents.add(deliveredPendingIntent);
            }
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                sentIntents,
                deliveredIntents
            );
        }
        
        // Update status immediately after successful send
        // (Broadcast receivers will handle final SENT/DELIVERED/FAILED state)
        status.postValue("SMS sent");
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // Cleanup pending intents if needed
        cleanupPendingIntents();
    }
    
    private void cleanupPendingIntents() {
        // Cancel any pending intents to prevent memory leaks
        try {
            // Implementation for cleanup if needed
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up pending intents", e);
        }
    }
}
