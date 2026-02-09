package com.afriserve.smsmanager.data.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.afriserve.smsmanager.models.Recipient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
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
    private static final String KEY_SESSION_PREFIX = "upload_session_";
    private static final String KEY_ACTIVE_SESSION_ID = "active_session_id";
    private static final long DEFAULT_SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final SharedPreferences preferences;
    private final Gson gson;
    private final ExecutorService executor;
    private UploadPreferences uploadPreferences;

    @Inject
    public UploadPersistenceService(@dagger.hilt.android.qualifiers.ApplicationContext Context context) {
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
        public String template;
        public int simSlot = 0;
        public int sendSpeed = 300;
        public String campaignName;
        public String campaignType;
        public String source;
        public long campaignId;
        public int lastProcessedIndex;
        public int sentCount;
        public int failedCount;
        public int skippedCount;
        public boolean isPaused;
        public boolean isStopped;
        public Long scheduledAt;

        public UploadSession() {
            this.uploadTimestamp = System.currentTimeMillis();
            this.lastAccessed = System.currentTimeMillis();
            this.recipients = new ArrayList<>();
            this.columnMapping = new HashMap<>();
            this.headers = new ArrayList<>();
            this.isActive = true;
            this.processingStatus = "ready";
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

        public UploadPreferences() {
        }
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

        public UploadHistoryEntry() {
        }
    }

    private String sessionKey(String sessionId) {
        return KEY_SESSION_PREFIX + sessionId;
    }

    private void ensureSessionId(UploadSession session) {
        if (session.fileId == null || session.fileId.trim().isEmpty()) {
            session.fileId = UUID.randomUUID().toString();
        }
    }

    private boolean isSessionExpired(UploadSession session) {
        if (session == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (session.scheduledAt != null && session.scheduledAt > now) {
            return false;
        }
        long maxAge = uploadPreferences.sessionTimeoutHours * 60 * 60 * 1000;
        long sessionAge = now - session.uploadTimestamp;
        return sessionAge > maxAge;
    }

    private void saveSessionInternal(UploadSession session, String key, boolean updateHistory) {
        ensureSessionId(session);
        session.lastAccessed = System.currentTimeMillis();
        session.isActive = true;
        String json = gson.toJson(session);
        preferences.edit()
                .putString(key, json)
                .apply();
        if (updateHistory && uploadPreferences.keepHistory) {
            addToHistory(session);
        }
    }

    private UploadSession loadSessionInternal(String key, boolean touch) {
        String json = preferences.getString(key, null);
        if (json == null) {
            return null;
        }
        UploadSession session = gson.fromJson(json, UploadSession.class);
        if (session == null) {
            return null;
        }
        if (isSessionExpired(session)) {
            preferences.edit().remove(key).apply();
            if (KEY_CURRENT_UPLOAD.equals(key)) {
                preferences.edit().remove(KEY_ACTIVE_SESSION_ID).apply();
            }
            return null;
        }
        if (touch) {
            session.lastAccessed = System.currentTimeMillis();
            saveSessionInternal(session, key, false);
        }
        return session;
    }

    public void saveSession(UploadSession session) {
        executor.execute(() -> {
            try {
                saveSessionInternal(session, sessionKey(session.fileId), false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save upload session", e);
            }
        });
    }

    public void saveSessionSync(UploadSession session) {
        try {
            saveSessionInternal(session, sessionKey(session.fileId), false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save upload session", e);
        }
    }

    public UploadSession loadSessionSync(String sessionId) {
        try {
            return loadSessionInternal(sessionKey(sessionId), false);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load upload session", e);
            return null;
        }
    }

    public UploadSession loadCurrentUploadSync() {
        try {
            UploadSession session = loadSessionInternal(KEY_CURRENT_UPLOAD, true);
            if (session != null) {
                setActiveSessionId(session.fileId);
            }
            return session;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load current upload session", e);
            return null;
        }
    }

    public void setActiveSessionId(String sessionId) {
        preferences.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply();
    }

    public String getActiveSessionId() {
        return preferences.getString(KEY_ACTIVE_SESSION_ID, null);
    }

    public boolean isActiveSession(String sessionId) {
        String activeId = getActiveSessionId();
        return activeId != null && activeId.equals(sessionId);
    }

    public void clearSession(String sessionId) {
        executor.execute(() -> {
            try {
                preferences.edit()
                        .remove(sessionKey(sessionId))
                        .apply();
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear session", e);
            }
        });
    }

    /**
     * Save current upload session
     */
    public void saveCurrentUpload(UploadSession session) {
        executor.execute(() -> {
            try {
                saveSessionInternal(session, KEY_CURRENT_UPLOAD, true);
                saveSessionInternal(session, sessionKey(session.fileId), false);
                setActiveSessionId(session.fileId);
                Log.d(TAG, "Saved upload session: " + session.fileName +
                        " (" + session.validRecords + " contacts)");
            } catch (Exception e) {
                Log.e(TAG, "Failed to save upload session", e);
            }
        });
    }

    public void saveCurrentUploadSync(UploadSession session) {
        try {
            saveSessionInternal(session, KEY_CURRENT_UPLOAD, false);
            saveSessionInternal(session, sessionKey(session.fileId), false);
            setActiveSessionId(session.fileId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save upload session", e);
        }
    }

    /**
     * Load current upload session
     * Returns null if no session or session expired
     */
    public void loadCurrentUpload(Callback<UploadSession> callback) {
        executor.execute(() -> {
            try {
                UploadSession session = loadSessionInternal(KEY_CURRENT_UPLOAD, true);
                if (session == null) {
                    callback.onResult(null);
                    return;
                }
                setActiveSessionId(session.fileId);
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
                String activeId = getActiveSessionId();
                SharedPreferences.Editor editor = preferences.edit()
                        .remove(KEY_CURRENT_UPLOAD)
                        .remove(KEY_ACTIVE_SESSION_ID);
                if (activeId != null) {
                    editor.remove(sessionKey(activeId));
                }
                editor.apply();
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

            Type listType = new TypeToken<List<UploadHistoryEntry>>() {
            }.getType();
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
