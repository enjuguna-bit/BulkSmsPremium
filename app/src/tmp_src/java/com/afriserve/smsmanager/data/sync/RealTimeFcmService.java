package com.afriserve.smsmanager.data.sync;

import android.util.Log;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.WorkManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Data;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.MainActivity;
import com.afriserve.smsmanager.data.network.SmsApiService;
import com.afriserve.smsmanager.data.entity.SyncStatusEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;
import com.afriserve.smsmanager.data.repository.ConversationRepository;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Enhanced FCM service for real-time SMS updates
 * Replaces polling with push notifications for efficient real-time sync
 */
// @AndroidEntryPoint
public class RealTimeFcmService extends FirebaseMessagingService {
    
    private static final String TAG = "RealTimeFcmService";
    private static final String CHANNEL_ID = "sms_updates";
    private static final String CHANNEL_NAME = "SMS Updates";
    private static final String NOTIFICATION_TITLE = "New Message";
    
    @Inject
    SmsRepository smsRepository;
    
    @Inject
    ConversationRepository conversationRepository;
    
    @Inject
    OfflineFirstSyncManager syncManager;
    
    private final Gson gson = new Gson();
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "RealTimeFcmService created");
    }
    
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed: " + token);
        
        // Send token to server for targeted notifications
        sendTokenToServer(token);
    }
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "FCM message received");
        
        try {
            // Handle different message types
            if (remoteMessage.getData().size() > 0) {
                handleDataMessage(remoteMessage.getData());
            }
            
            if (remoteMessage.getNotification() != null) {
                handleNotificationMessage(remoteMessage.getNotification());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing FCM message", e);
        }
    }
    
    @Override
    public void onDeletedMessages() {
        Log.d(TAG, "FCM messages deleted on server");
        
        // Server deleted pending messages - perform full sync
        scheduleFullSync();
    }
    
    @Override
    public void onMessageSent() {
        Log.d(TAG, "FCM message sent successfully");
    }
    
    @Override
    public void onSendError(@NonNull String msgId, @NonNull Exception exception) {
        Log.e(TAG, "FCM message send error: " + msgId, exception);
    }
    
    /**
     * Handle data messages (silent push for real-time updates)
     */
    private void handleDataMessage(Map<String, String> data) {
        Log.d(TAG, "Processing data message: " + data.keySet());
        
        String messageType = data.get("type");
        if (messageType == null) {
            Log.w(TAG, "No message type in FCM data");
            return;
        }
        
        switch (messageType) {
            case "new_sms":
                handleNewSmsMessage(data);
                break;
            case "sms_updated":
                handleSmsUpdatedMessage(data);
                break;
            case "conversation_updated":
                handleConversationUpdatedMessage(data);
                break;
            case "sync_required":
                handleSyncRequiredMessage(data);
                break;
            case "conflict_detected":
                handleConflictDetectedMessage(data);
                break;
            default:
                Log.w(TAG, "Unknown message type: " + messageType);
                break;
        }
    }
    
    /**
     * Handle new SMS message
     */
    private void handleNewSmsMessage(Map<String, String> data) {
        try {
            String smsData = data.get("sms");
            if (smsData == null) {
                Log.w(TAG, "No SMS data in new_sms message");
                return;
            }
            
            SmsApiService.SmsNetworkEntity smsEntity = gson.fromJson(smsData, SmsApiService.SmsNetworkEntity.class);
            
            // Schedule immediate sync for this message
            scheduleMessageSync(smsEntity.id);
            
            // Show notification if app is in background
            if (!isAppInForeground()) {
                showNewMessageNotification(smsEntity);
            }
            
            Log.d(TAG, "New SMS message processed: " + smsEntity.id);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing new SMS message", e);
        }
    }
    
    /**
     * Handle SMS updated message
     */
    private void handleSmsUpdatedMessage(Map<String, String> data) {
        try {
            String smsId = data.get("sms_id");
            String updateType = data.get("update_type");
            
            if (smsId == null || updateType == null) {
                Log.w(TAG, "Missing required fields in sms_updated message");
                return;
            }
            
            // Schedule sync for updated message
            scheduleMessageSync(smsId);
            
            Log.d(TAG, "SMS update processed: " + smsId + " (" + updateType + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS updated message", e);
        }
    }
    
    /**
     * Handle conversation updated message
     */
    private void handleConversationUpdatedMessage(Map<String, String> data) {
        try {
            String conversationId = data.get("conversation_id");
            String updateType = data.get("update_type");
            
            if (conversationId == null || updateType == null) {
                Log.w(TAG, "Missing required fields in conversation_updated message");
                return;
            }
            
            // Schedule sync for conversation
            scheduleConversationSync(conversationId);
            
            Log.d(TAG, "Conversation update processed: " + conversationId + " (" + updateType + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing conversation updated message", e);
        }
    }
    
    /**
     * Handle sync required message
     */
    private void handleSyncRequiredMessage(Map<String, String> data) {
        try {
            String syncType = data.get("sync_type");
            String entityType = data.get("entity_type");
            
            Log.d(TAG, "Sync required: " + syncType + " for " + entityType);
            
            // Schedule appropriate sync based on type
            if ("full".equals(syncType)) {
                scheduleFullSync();
            } else if ("incremental".equals(syncType)) {
                scheduleIncrementalSync(entityType);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing sync required message", e);
        }
    }
    
    /**
     * Handle conflict detected message
     */
    private void handleConflictDetectedMessage(Map<String, String> data) {
        try {
            String entityId = data.get("entity_id");
            String entityType = data.get("entity_type");
            String conflictData = data.get("conflict_data");
            
            if (entityId == null || entityType == null) {
                Log.w(TAG, "Missing required fields in conflict_detected message");
                return;
            }
            
            // Mark entity as conflicted in sync status
            markEntityAsConflicted(entityType, entityId, conflictData);
            
            // Schedule conflict resolution
            scheduleConflictResolution(entityType, entityId);
            
            Log.d(TAG, "Conflict detected for: " + entityType + ":" + entityId);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing conflict detected message", e);
        }
    }
    
    /**
     * Handle notification messages
     */
    private void handleNotificationMessage(RemoteMessage.Notification notification) {
        Log.d(TAG, "Processing notification message");
        
        String title = notification.getTitle();
        String body = notification.getBody();
        
        if (title != null && body != null) {
            showGenericNotification(title, body);
        }
    }
    
    /**
     * Schedule immediate sync for a specific message
     */
    private void scheduleMessageSync(String smsId) {
        Data syncData = new Data.Builder()
            .putString("entity_type", "sms")
            .putString("entity_id", smsId)
            .putString("sync_type", "incremental")
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setInputData(syncData)
            .setInitialDelay(0, TimeUnit.SECONDS) // Immediate
            .addTag("message_sync")
            .build();
        
        WorkManager.getInstance(this).enqueue(syncRequest);
        Log.d(TAG, "Scheduled message sync for: " + smsId);
    }
    
    /**
     * Schedule sync for a specific conversation
     */
    private void scheduleConversationSync(String conversationId) {
        Data syncData = new Data.Builder()
            .putString("entity_type", "conversation")
            .putString("entity_id", conversationId)
            .putString("sync_type", "incremental")
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setInputData(syncData)
            .setInitialDelay(0, TimeUnit.SECONDS) // Immediate
            .addTag("conversation_sync")
            .build();
        
        WorkManager.getInstance(this).enqueue(syncRequest);
        Log.d(TAG, "Scheduled conversation sync for: " + conversationId);
    }
    
    /**
     * Schedule full sync
     */
    private void scheduleFullSync() {
        Data syncData = new Data.Builder()
            .putString("sync_type", "full")
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setInputData(syncData)
            .setInitialDelay(1, TimeUnit.SECONDS) // Small delay to batch multiple requests
            .addTag("full_sync")
            .build();
        
        WorkManager.getInstance(this).enqueue(syncRequest);
        Log.d(TAG, "Scheduled full sync");
    }
    
    /**
     * Schedule incremental sync
     */
    private void scheduleIncrementalSync(String entityType) {
        Data syncData = new Data.Builder()
            .putString("entity_type", entityType)
            .putString("sync_type", "incremental")
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setInputData(syncData)
            .setInitialDelay(500, TimeUnit.MILLISECONDS) // Small delay
            .addTag("incremental_sync")
            .build();
        
        WorkManager.getInstance(this).enqueue(syncRequest);
        Log.d(TAG, "Scheduled incremental sync for: " + entityType);
    }
    
    /**
     * Schedule conflict resolution
     */
    private void scheduleConflictResolution(String entityType, String entityId) {
        Data syncData = new Data.Builder()
            .putString("entity_type", entityType)
            .putString("entity_id", entityId)
            .putString("sync_type", "conflict_resolution")
            .build();
        
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setInputData(syncData)
            .setInitialDelay(2, TimeUnit.SECONDS) // Slight delay for user interaction
            .addTag("conflict_resolution")
            .build();
        
        WorkManager.getInstance(this).enqueue(syncRequest);
        Log.d(TAG, "Scheduled conflict resolution for: " + entityType + ":" + entityId);
    }
    
    /**
     * Mark entity as conflicted
     */
    private void markEntityAsConflicted(String entityType, String entityId, String conflictData) {
        try {
            syncManager.updateSyncStatus(entityType, entityId, SyncStatusEntity.SyncStatus.CONFLICT)
                .subscribe(
                    () -> Log.d(TAG, "Marked entity as conflicted: " + entityType + ":" + entityId),
                    error -> Log.e(TAG, "Failed to mark entity as conflicted", error)
                );
        } catch (Exception e) {
            Log.e(TAG, "Error marking entity as conflicted", e);
        }
    }
    
    /**
     * Send FCM token to server
     */
    private void sendTokenToServer(String token) {
        // TODO: Implement token registration with server
        Log.d(TAG, "Sending FCM token to server: " + token);
    }
    
    /**
     * Check if app is in foreground
     */
    private boolean isAppInForeground() {
        // TODO: Implement proper foreground detection
        // For now, assume app is in background
        return false;
    }
    
    /**
     * Show new message notification
     */
    private void showNewMessageNotification(SmsApiService.SmsNetworkEntity smsEntity) {
        String message = smsEntity.message;
        if (message.length() > 50) {
            message = message.substring(0, 47) + "...";
        }
        
        String sender = smsEntity.phoneNumber;
        if (smsEntity.contactName != null && !smsEntity.contactName.trim().isEmpty()) {
            sender = smsEntity.contactName;
        }
        
        showGenericNotification("New message from " + sender, message);
    }
    
    /**
     * Show generic notification
     */
    private void showGenericNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);
        
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }
    
    /**
     * Create notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Real-time SMS updates");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
