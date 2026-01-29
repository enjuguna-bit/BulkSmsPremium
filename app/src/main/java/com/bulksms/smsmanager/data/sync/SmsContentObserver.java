package com.bulksms.smsmanager.data.sync;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import android.content.Context;

/**
 * Real-time SMS sync using ContentObserver
 * Monitors SMS content provider changes and triggers immediate sync
 */
@Singleton
public class SmsContentObserver extends ContentObserver {
    
    private static final String TAG = "SmsContentObserver";
    
    private final Context context;
    private final ContentResolver contentResolver;
    private final Subject<SmsChangeEvent> changeEvents;
    
    // Sync listeners
    private OnSmsChangeListener syncListener;
    
    // Debounce timer to avoid excessive sync calls
    private static final long DEBOUNCE_DELAY_MS = 1000; // 1 second
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable debounceRunnable = this::performDebouncedSync;
    private boolean debounceScheduled = false;
    
    @Inject
    public SmsContentObserver(@ApplicationContext Context context) {
        super(new Handler(Looper.getMainLooper()));
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.changeEvents = PublishSubject.create();
    }
    
    /**
     * Register observer for SMS content changes
     */
    public void register() {
        try {
            // Register for SMS content changes
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true, // Notify for descendants
                this
            );
            
            // Register for MMS content changes
            contentResolver.registerContentObserver(
                Telephony.Mms.CONTENT_URI,
                true,
                this
            );
            
            Log.d(TAG, "SMS ContentObserver registered");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to register ContentObserver", e);
        }
    }
    
    /**
     * Unregister observer
     */
    public void unregister() {
        try {
            contentResolver.unregisterContentObserver(this);
            
            // Cancel any pending debounce
            debounceHandler.removeCallbacks(debounceRunnable);
            debounceScheduled = false;
            
            Log.d(TAG, "SMS ContentObserver unregistered");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister ContentObserver", e);
        }
    }
    
    /**
     * Set sync listener
     */
    public void setOnSmsChangeListener(OnSmsChangeListener listener) {
        this.syncListener = listener;
    }
    
    /**
     * Get RxJava stream of change events
     */
    public io.reactivex.rxjava3.core.Observable<SmsChangeEvent> getChangeEvents() {
        return changeEvents.distinctUntilChanged();
    }
    
    @Override
    public void onChange(boolean selfChange, Uri uri) {
        // Don't call super.onChange to avoid recursion with legacy onChange method
        Log.d(TAG, "SMS content changed: " + uri);
        
        // Determine change type
        SmsChangeEvent.ChangeType changeType = determineChangeType(uri);
        
        // Create change event
        SmsChangeEvent event = new SmsChangeEvent(
            System.currentTimeMillis(),
            uri,
            changeType,
            selfChange
        );
        
        // Publish event
        changeEvents.onNext(event);
        
        // Trigger debounced sync
        triggerDebouncedSync(event);
        
        // Notify listener
        if (syncListener != null) {
            syncListener.onSmsChanged(event);
        }
    }
    
    @Override
    public void onChange(boolean selfChange) {
        // This method is called for Android API < 16
        // Handle directly without calling the other onChange method to avoid recursion
        Log.d(TAG, "SMS content changed (legacy API): " + selfChange);
        
        // Create a default event for legacy API
        SmsChangeEvent event = new SmsChangeEvent(
            System.currentTimeMillis(),
            Telephony.Sms.CONTENT_URI,
            SmsChangeEvent.ChangeType.SMS_GENERAL,
            selfChange
        );
        
        // Publish event
        changeEvents.onNext(event);
        
        // Trigger debounced sync
        triggerDebouncedSync(event);
        
        // Notify listener
        if (syncListener != null) {
            syncListener.onSmsChanged(event);
        }
    }
    
    /**
     * Determine the type of change based on URI
     */
    private SmsChangeEvent.ChangeType determineChangeType(Uri uri) {
        if (uri == null) {
            return SmsChangeEvent.ChangeType.UNKNOWN;
        }
        
        String uriString = uri.toString();
        
        if (uriString.contains("sms")) {
            if (uriString.contains("/sent")) {
                return SmsChangeEvent.ChangeType.SMS_SENT;
            } else if (uriString.contains("/inbox")) {
                return SmsChangeEvent.ChangeType.SMS_RECEIVED;
            } else if (uriString.contains("/draft")) {
                return SmsChangeEvent.ChangeType.SMS_DRAFT;
            } else {
                return SmsChangeEvent.ChangeType.SMS_GENERAL;
            }
        } else if (uriString.contains("mms")) {
            return SmsChangeEvent.ChangeType.MMS_GENERAL;
        }
        
        return SmsChangeEvent.ChangeType.UNKNOWN;
    }
    
    /**
     * Trigger debounced sync to avoid excessive calls
     */
    private void triggerDebouncedSync(SmsChangeEvent event) {
        // Cancel previous debounce if scheduled
        if (debounceScheduled) {
            debounceHandler.removeCallbacks(debounceRunnable);
        }
        
        // Schedule new debounce
        debounceScheduled = true;
        debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
    }
    
    /**
     * Perform the debounced sync
     */
    private void performDebouncedSync() {
        debounceScheduled = false;
        
        Log.d(TAG, "Performing debounced sync");
        
        if (syncListener != null) {
            syncListener.onSyncRequested();
        }
    }
    
    /**
     * Force immediate sync (bypassing debounce)
     */
    public void forceSync() {
        Log.d(TAG, "Force sync requested");
        
        if (syncListener != null) {
            syncListener.onSyncRequested();
        }
    }
    
    /**
     * Check if observer is registered
     */
    public boolean isRegistered() {
        return contentResolver != null;
    }
    
    /**
     * Interface for sync change listeners
     */
    public interface OnSmsChangeListener {
        void onSmsChanged(SmsChangeEvent event);
        void onSyncRequested();
    }
    
    /**
     * SMS change event data class
     */
    public static class SmsChangeEvent {
        public final long timestamp;
        public final Uri uri;
        public final ChangeType changeType;
        public final boolean selfChange;
        
        public SmsChangeEvent(long timestamp, Uri uri, ChangeType changeType, boolean selfChange) {
            this.timestamp = timestamp;
            this.uri = uri;
            this.changeType = changeType;
            this.selfChange = selfChange;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            
            SmsChangeEvent that = (SmsChangeEvent) o;
            
            if (timestamp != that.timestamp) return false;
            if (selfChange != that.selfChange) return false;
            if (changeType != that.changeType) return false;
            return uri != null ? uri.equals(that.uri) : that.uri == null;
        }
        
        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + (uri != null ? uri.hashCode() : 0);
            result = 31 * result + (changeType != null ? changeType.hashCode() : 0);
            result = 31 * result + (selfChange ? 1 : 0);
            return result;
        }
        
        @Override
        public String toString() {
            return "SmsChangeEvent{" +
                    "timestamp=" + timestamp +
                    ", uri=" + uri +
                    ", changeType=" + changeType +
                    ", selfChange=" + selfChange +
                    '}';
        }
        
        /**
         * Types of SMS changes
         */
        public enum ChangeType {
            SMS_RECEIVED,
            SMS_SENT,
            SMS_DRAFT,
            SMS_GENERAL,
            MMS_GENERAL,
            UNKNOWN
        }
    }
}
