package com.bulksms.smsmanager.data.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.bulksms.smsmanager.models.Recipient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Session persistence service for Excel upload data
 * Manages upload sessions across app restarts with timeout handling
 */
@Singleton
public class UploadPersistenceService {
    private static final String TAG = "UploadPersistence";
    private static final String PREFS_NAME = "bulk_sms_upload_persistence";
    private static final String KEY_CURRENT_UPLOAD = "current_upload";
    private static final String KEY_UPLOAD_HISTORY = "upload_history";
    private static final String KEY_PREFERENCES = "upload_preferences";
    private static final long DEFAULT_SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24 hours
    
    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executor;
    private UploadPreferences uploadPreferences;
    
    @Inject
    public UploadPersistenceService(Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.uploadPreferences = loadPreferences();
    }
    
    /**
     * Data class for upload session information
     */
    public static class UploadSession {
        public String fileId;
        public String fileName;
        public long uploadTimestamp;
        public long lastAccessed;
        public List<Recipient> recipients;
        public Map<String, String> columnMapping;
        public List<String> headers;
        public int totalRecords;
        public int validRecords;
        public int invalidRecords;
        public boolean isActive;
        public String processingStatus;
        
        public UploadSession() {
            this.uploadTimestamp = System.currentTimeMillis();
            this.lastAccessed = System.currentTimeMillis();
            this.recipients = new ArrayList<>();
            this.columnMapping = new HashMap<>();
            this.headers = new ArrayList<>();
            this.isActive = true;
            this.processingStatus = "processed";
        }
    }
    
    /**
     * Data class for upload preferences
     */
    public static class UploadPreferences {
        public boolean autoSave = true;
        public long sessionTimeoutHours = 24;
        public boolean keepHistory = true;
        public int maxHistoryEntries = 10;
        
        public UploadPreferences() {}
    }
    
    /**
     * Data class for upload history entry
     */
    public static class UploadHistoryEntry {
        public String fileId;
        public String fileName;
        public long uploadTimestamp;
        public int totalRecords;
        public int validRecords;
        public boolean completed;
        public Long completedAt;
        public int sentCount;
        public int failedCount;
        public boolean archived;
        
        public UploadHistoryEntry() {}
    }
    
    /**
     * Save current upload session
     */
    public void saveCurrentUpload(UploadSession session) {
        executor.execute(() -> {
            try {
                session.lastAccessed = System.currentTimeMillis();
                session.isActive = true;
                
                String json = gson.toJson(session);
                preferences.edit()
                    .putString(KEY_CURRENT_UPLOAD, json)
                    .apply();
                
                // Add to history if enabled
                if (uploadPreferences.keepHistory) {
                    addToHistory(session);
                }
                
                Log.d(TAG, "Saved upload session: " + session.fileName + 
                      " (" + session.validRecords + " contacts)");
                      
            } catch (Exception e) {
                Log.e(TAG, "Failed to save upload session", e);
            }
        });
    }
    
    /**
     * Load current upload session
     * Returns null if no session or session expired
     */
    public void loadCurrentUpload(Callback<UploadSession> callback) {
        executor.execute(() -> {
            try {
                String json = preferences.getString(KEY_CURRENT_UPLOAD, null);
                if (json == null) {
                    callback.onResult(null);
                    return;
                }
                
                UploadSession session = gson.fromJson(json, UploadSession.class);
                if (session == null) {
                    callback.onResult(null);
                    return;
                }
                
                // Check session expiry
                long sessionAge = System.currentTimeMillis() - session.uploadTimestamp;
                long maxAge = uploadPreferences.sessionTimeoutHours * 60 * 60 * 1000;
                
                if (sessionAge > maxAge) {
                    Log.i(TAG, "Session expired: " + session.fileName + 
                          " (" + (sessionAge / 3600000) + "h old)");
                    clearCurrentUpload();
                    callback.onResult(null);
                    return;
                }
                
                // Update last accessed time
                session.lastAccessed = System.currentTimeMillis();
                saveCurrentUpload(session);
                
                Log.d(TAG, "Loaded session: " + session.fileName);
                callback.onResult(session);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to load upload session", e);
                callback.onResult(null);
            }
        });
    }
    
    /**
     * Clear current upload session
     */
    public void clearCurrentUpload() {
        executor.execute(() -> {
            try {
                preferences.edit()
                    .remove(KEY_CURRENT_UPLOAD)
                    .apply();
                Log.d(TAG, "Cleared current upload session");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear upload session", e);
            }
        });
    }
    
    /**
     * Add upload to history
     */
    private void addToHistory(UploadSession session) {
        try {
            List<UploadHistoryEntry> history = getUploadHistory();
            
            UploadHistoryEntry entry = new UploadHistoryEntry();
            entry.fileId = session.fileId;
            entry.fileName = session.fileName;
            entry.uploadTimestamp = session.uploadTimestamp;
            entry.totalRecords = session.totalRecords;
            entry.validRecords = session.validRecords;
            entry.completed = false;
            entry.archived = false;
            
            // Add to front, keeping max entries
            history.add(0, entry);
            history.removeIf(e -> e.fileId.equals(session.fileId)); // Remove duplicate
            
            while (history.size() > uploadPreferences.maxHistoryEntries) {
                history.remove(history.size() - 1);
            }
            
            String json = gson.toJson(history);
            preferences.edit()
                .putString(KEY_UPLOAD_HISTORY, json)
                .apply();
                
        } catch (Exception e) {
            Log.e(TAG, "Failed to add to history", e);
        }
    }
    
    /**
     * Get upload history
     */
    public List<UploadHistoryEntry> getUploadHistory() {
        try {
            String json = preferences.getString(KEY_UPLOAD_HISTORY, null);
            if (json == null) {
                return new ArrayList<>();
            }
            
            Type listType = new TypeToken<List<UploadHistoryEntry>>(){}.getType();
            return gson.fromJson(json, listType);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to get upload history", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Mark upload as completed in history
     */
    public void markUploadCompleted(String fileId, int sentCount, int failedCount) {
        executor.execute(() -> {
            try {
                List<UploadHistoryEntry> history = getUploadHistory();
                boolean updated = false;
                
                for (UploadHistoryEntry entry : history) {
                    if (entry.fileId.equals(fileId)) {
                        entry.completed = true;
                        entry.completedAt = System.currentTimeMillis();
                        entry.sentCount = sentCount;
                        entry.failedCount = failedCount;
                        updated = true;
                        break;
                    }
                }
                
                if (updated) {
                    String json = gson.toJson(history);
                    preferences.edit()
                        .putString(KEY_UPLOAD_HISTORY, json)
                        .apply();
                        
                    Log.d(TAG, "Marked upload " + fileId + " as completed (" + 
                          sentCount + " sent, " + failedCount + " failed)");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark upload completed", e);
            }
        });
    }
    
    /**
     * Clear upload history
     */
    public void clearHistory() {
        executor.execute(() -> {
            try {
                preferences.edit()
                    .remove(KEY_UPLOAD_HISTORY)
                    .apply();
                Log.d(TAG, "Cleared upload history");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear history", e);
            }
        });
    }
    
    /**
     * Get upload preferences
     */
    public UploadPreferences getPreferences() {
        return uploadPreferences;
    }
    
    /**
     * Update upload preferences
     */
    public void updatePreferences(UploadPreferences preferences) {
        executor.execute(() -> {
            try {
                this.uploadPreferences = preferences;
                String json = gson.toJson(preferences);
                this.preferences.edit()
                    .putString(KEY_PREFERENCES, json)
                    .apply();
                Log.d(TAG, "Updated upload preferences");
            } catch (Exception e) {
                Log.e(TAG, "Failed to update preferences", e);
            }
        });
    }
    
    /**
     * Check if there's an active session
     */
    public void hasActiveSession(Callback<Boolean> callback) {
        loadCurrentUpload(session -> callback.onResult(session != null && session.isActive));
    }
    
    /**
     * Cleanup expired sessions
     */
    public void cleanupExpiredSessions() {
        executor.execute(() -> {
            try {
                loadCurrentUpload(session -> {
                    // Session is automatically checked for expiry in loadCurrentUpload
                    // If session is null due to expiry, it's already cleared
                });
                
                // Also cleanup very old history entries (older than 30 days)
                List<UploadHistoryEntry> history = getUploadHistory();
                long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
                
                history.removeIf(entry -> entry.uploadTimestamp < thirtyDaysAgo);
                
                String json = gson.toJson(history);
                preferences.edit()
                    .putString(KEY_UPLOAD_HISTORY, json)
                    .apply();
                    
                Log.d(TAG, "Cleaned up expired sessions");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to cleanup expired sessions", e);
            }
        });
    }
    
    /**
     * Load preferences from storage
     */
    private UploadPreferences loadPreferences() {
        try {
            String json = preferences.getString(KEY_PREFERENCES, null);
            if (json != null) {
                return gson.fromJson(json, UploadPreferences.class);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load preferences", e);
        }
        return new UploadPreferences();
    }
    
    /**
     * Callback interface for async operations
     */
    public interface Callback<T> {
        void onResult(T result);
    }
}
